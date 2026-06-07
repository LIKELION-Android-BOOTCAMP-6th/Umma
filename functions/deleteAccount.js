const { HttpsError } = require("firebase-functions/https");

async function deleteFirestoreUserData({ db, loggerImpl, uid }) {
  const userRef = db.collection("users").doc(uid);

  try {
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
