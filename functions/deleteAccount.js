const { HttpsError } = require("firebase-functions/https");

const USERS_COLLECTION = "users";
const CHAT_PROMPT_REVIEW_REPORTS_COLLECTION = "chat_prompt_review_reports";
const CHAT_PROMPT_REVIEWS_COLLECTION = "chat_prompt_reviews";
const DELETE_BATCH_LIMIT = 400;

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

async function deleteFirestoreUserData({ db, loggerImpl, uid }) {
  const userRef = db.collection(USERS_COLLECTION).doc(uid);

  try {
    // 계정 삭제는 전역 리뷰 인덱스부터 먼저 비워야, 부분 실패 시 다시 시도할 때
    // root index와 user subtree가 서로 어긋난 상태를 줄일 수 있다.
    await deletePromptReviewReports({ db, loggerImpl, uid });

    await userRef.get();
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

  loggerImpl.info("Delete account started", { uid });

  await deleteFirestoreUserData({
    db,
    loggerImpl,
    uid,
  });
  loggerImpl.info("Firestore user data deletion succeeded", { uid });

  await deleteFirebaseAuthUser({
    auth,
    loggerImpl,
    uid,
  });
  loggerImpl.info("Firebase Auth user deletion succeeded", { uid });

  loggerImpl.info("Account deleted", { uid });
  return { success: true };
}

module.exports = {
  deleteFirebaseAuthUser,
  deleteFirestoreUserData,
  handleDeleteAccount,
};
