const { HttpsError } = require("firebase-functions/https");

const USERS_COLLECTION = "users";
const CHAT_PROMPT_REVIEW_REPORTS_COLLECTION = "chat_prompt_review_reports";
const CHAT_PROMPT_REVIEWS_COLLECTION = "chat_prompt_reviews";
const ACCOUNT_DELETE_LOCKS_COLLECTION = "account_delete_locks";
const DELETE_BATCH_LIMIT = 400;
// 삭제 시작 후 이 시간이 지난 락은 스케줄러가 자동 정리한다.
// handleDeleteAccount 성공/실패 시에는 명시적으로 해제하고, 이 TTL은 안전망이다.
const ACCOUNT_DELETE_LOCK_TTL_MS = 24 * 60 * 60 * 1000;

async function deleteDocumentsInBatches({ db, documentRefs }) {
  if (!documentRefs.length) {
    return 0;
  }

  let deletedCount = 0;
  for (let index = 0; index < documentRefs.length; index += DELETE_BATCH_LIMIT) {
    const batch = db.batch();
    const batchRefs = documentRefs.slice(index, index + DELETE_BATCH_LIMIT);
    batchRefs.forEach((ref) => batch.delete(ref));
    await batch.commit();
    deletedCount += batchRefs.length;
  }

  return deletedCount;
}

async function deletePromptReviewReports({ db, loggerImpl, uid }) {
  const reportCollection = db.collection(CHAT_PROMPT_REVIEW_REPORTS_COLLECTION);
  const reviewPathPrefix = `users/${uid}/${CHAT_PROMPT_REVIEWS_COLLECTION}/`;

  const [byUserId, byUid, byReviewPath] = await Promise.all([
    reportCollection.where("userId", "==", uid).get(),
    reportCollection.where("uid", "==", uid).get(),
    reportCollection
      .where("reviewPath", ">=", reviewPathPrefix)
      .where("reviewPath", "<", `${reviewPathPrefix}\uf8ff`)
      .get(),
  ]);

  // 기존 문서는 userId, uid, reviewPath 중 일부만 갖고 있을 수 있어 여러 조건으로 찾는다.
  // 같은 문서를 중복 삭제하지 않도록 Firestore path 기준으로 하나로 합친다.
  const refsByPath = new Map();
  [byUserId, byUid, byReviewPath].forEach((snapshot) => {
    snapshot.forEach((doc) => {
      refsByPath.set(doc.ref.path, doc.ref);
    });
  });

  const documentRefs = [...refsByPath.values()];
  if (!documentRefs.length) {
    loggerImpl.info("Prompt review cleanup skipped: no root reports", { uid });
    return 0;
  }

  const deletedCount = await deleteDocumentsInBatches({
    db,
    documentRefs,
  });

  loggerImpl.info("Prompt review reports deleted", {
    uid,
    deletedCount,
  });

  return deletedCount;
}

// accountDeleteLockRef는 handleDeleteAccount에서 생성해 전달한다.
// 락 해제 책임(성공/실패 양 경로)을 handleDeleteAccount에 두기 위해 ref를 공유한다.
async function deleteFirestoreUserData({ db, loggerImpl, uid, accountDeleteLockRef }) {
  const userRef = db.collection(USERS_COLLECTION).doc(uid);

  try {
    // expiresAt을 함께 기록해, 명시적 해제가 실패하더라도 스케줄러가 TTL 기준으로 정리할 수 있게 한다.
    await accountDeleteLockRef.set({
      userId: uid,
      status: "deleting",
      startedAt: Date.now(),
      expiresAt: Date.now() + ACCOUNT_DELETE_LOCK_TTL_MS,
    }, { merge: true });

    // 계정 삭제는 전역 리뷰 인덱스부터 먼저 비워야, 부분 실패 시 다시 시도할 때
    // root index와 user subtree가 서로 어긋난 상태를 줄일 수 있다.
    await deletePromptReviewReports({ db, loggerImpl, uid });

    await db.recursiveDelete(userRef);
  } catch (error) {
    loggerImpl.error("Firestore account delete failed", {
      uid,
      documentPath: userRef.path,
      message: error?.message ?? "unknown error",
      code: error?.code ?? "unknown",
      stack: error?.stack ?? "",
    });
    throw new HttpsError("internal", "사용자 데이터를 삭제하지 못했습니다.");
  }
}

async function deleteFirebaseAuthUser({ auth, loggerImpl, uid }) {
  try {
    await auth.deleteUser(uid);
  } catch (error) {
    if (error?.code !== "auth/user-not-found") {
      loggerImpl.error("Auth user delete failed", {
        uid,
        message: error?.message ?? "unknown error",
        code: error?.code ?? "unknown",
        stack: error?.stack ?? "",
      });
      throw new HttpsError("internal", "인증 계정을 삭제하지 못했습니다.");
    }
  }
}

async function handleDeleteAccount({
  auth,
  db,
  loggerImpl,
  request,
}) {
  const uid = request.auth?.uid;

  if (!uid) {
    throw new HttpsError("unauthenticated", "로그인이 필요한 기능입니다.");
  }

  // lockRef를 여기서 생성해 성공/실패 양 경로에서 동일 ref로 해제한다.
  const accountDeleteLockRef = db.collection(ACCOUNT_DELETE_LOCKS_COLLECTION).doc(uid);

  loggerImpl.info("Delete account started", { uid });

  try {
    await deleteFirestoreUserData({ db, loggerImpl, uid, accountDeleteLockRef });
    loggerImpl.info("Firestore user data deletion succeeded", { uid });

    await deleteFirebaseAuthUser({ auth, loggerImpl, uid });
    loggerImpl.info("Firebase Auth user deletion succeeded", { uid });
  } catch (error) {
    // 실패 시 락을 해제해 재시도 경로를 열어둔다.
    // Auth 삭제 실패 케이스: users/{uid}는 삭제됐지만 Auth가 살아있는 상태에서
    // 락이 남으면 사용자가 모든 write를 영구 차단당하는 브릭 상태가 된다.
    await accountDeleteLockRef.delete().catch((lockErr) => {
      loggerImpl.error("Lock release failed after deletion error — manual cleanup required", {
        uid,
        lockPath: accountDeleteLockRef.path,
        message: lockErr?.message ?? "unknown",
      });
    });
    throw error;
  }

  // 전체 성공 시 락 정리. Auth까지 삭제됐으므로 락 해제 실패는 비치명적이지만
  // 컬렉션 누적을 막기 위해 정리를 시도하고, 실패하면 스케줄러 정리에 맡긴다.
  await accountDeleteLockRef.delete().catch((lockErr) => {
    loggerImpl.warn("Lock cleanup failed after successful deletion — will be cleared by scheduled job", {
      uid,
      message: lockErr?.message ?? "unknown",
    });
  });

  loggerImpl.info("Account deleted", { uid });
  return { success: true };
}

module.exports = {
  deleteFirebaseAuthUser,
  deleteFirestoreUserData,
  handleDeleteAccount,
};
