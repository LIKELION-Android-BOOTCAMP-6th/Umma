const { setGlobalOptions } = require("firebase-functions");
const { onRequest, onCall, HttpsError } = require("firebase-functions/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

setGlobalOptions({ maxInstances: 10 });

// OPENAI_API_KEY 는 Firebase Secret Manager 에 저장한다.
// 값은 배포된 함수 런타임에서만 읽고, Android 앱이나 repository 에는 절대 두지 않는다.
const openAiApiKey = defineSecret("OPENAI_API_KEY");

/**
 * Issues an OpenAI Realtime ephemeral token for CHAT-POC-001.
 *
 * The Android app must not contain the OpenAI API key. This function keeps the
 * real key in Firebase Secret Manager and returns only the short-lived Realtime
 * session response needed by the app-side WebSocket connection.
 */
exports.realtimeToken = onRequest(
  {
    // Android local.properties 의 OPENAI_REALTIME_TOKEN_URL 과 같은 region URL 을 사용한다.
    region: "us-central1",
    // 이 함수가 실행될 때만 OPENAI_API_KEY secret 접근 권한을 부여한다.
    secrets: [openAiApiKey],
    // 모바일 앱은 Cloud Run IAM 으로 직접 인증하지 않고 Firebase ID token 을 전달한다.
    // 따라서 URL 접근 자체보다, 아래 handler 에서 인증된 Firebase 사용자에게만 token 을 발급하는 것이 핵심이다.
    cors: false,
  },
  async (request, response) => {
    if (request.method !== "POST") {
      // 앱은 POST 로만 호출한다. GET 호출을 막아 브라우저 직접 접근을 token 발급 경로로 쓰지 않게 한다.
      response.status(405).json({ error: "Method Not Allowed" });
      return;
    }

    try {
      const decodedToken = await verifyFirebaseIdToken(request);

      // Realtime WebSocket 은 Android 앱이 직접 연결하지만,
      // 그 연결에 필요한 short-lived client secret 은 서버에서 발급한다.
      const openAiResponse = await fetch(
        "https://api.openai.com/v1/realtime/client_secrets",
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${openAiApiKey.value()}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            // client secret 을 만들 때도 PoC 모델을 고정해 앱과 서버의 모델 선택이 어긋나지 않게 한다.
            session: {
              type: "realtime",
              model: "gpt-realtime-mini",
            },
          }),
        },
      );

      const payload = await openAiResponse.json();

      if (!openAiResponse.ok) {
        // OpenAI 에러 payload 를 그대로 남겨 Android 연결 실패가 key/권한/모델 문제인지 구분한다.
        logger.error("OpenAI realtime session request failed", {
          status: openAiResponse.status,
          uid: decodedToken.uid,
          payload,
        });
        response.status(openAiResponse.status).json(payload);
        return;
      }

      response.status(200).json(payload);
    } catch (error) {
      if (error.code === "unauthenticated") {
        response.status(401).json({ error: "Unauthorized" });
        return;
      }

      // 네트워크나 Secret 접근 같은 서버 내부 실패는 Android 에 상세 key 값을 노출하지 않는다.
      logger.error("Failed to issue OpenAI realtime token", error);
      response.status(500).json({
        error: "Failed to issue realtime token",
      });
    }
  },
);

/**
 * Verifies the Firebase Auth ID token sent by the Android app.
 *
 * The function endpoint may still be reachable as an HTTPS URL, but it must not
 * issue an OpenAI client secret unless the caller proves a valid Firebase login.
 */
async function verifyFirebaseIdToken(request) {
  const authorization = request.get("Authorization") || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);

  if (!match) {
    const error = new Error("Missing Firebase ID token");
    error.code = "unauthenticated";
    throw error;
  }

  try {
    return await admin.auth().verifyIdToken(match[1]);
  } catch (error) {
    logger.warn("Firebase ID token verification failed", {
      message: error.message,
    });
    const authError = new Error("Invalid Firebase ID token");
    authError.code = "unauthenticated";
    throw authError;
  }
}
const { getFirestore } = require("firebase-admin/firestore");
// 회원탈퇴 Cloud Functions API
exports.deleteAccount = onCall(
  {
    region: "us-central1",
  },
  async (request) => {
    const uid = request.auth?.uid;

    if (!uid) {
      throw new HttpsError("unauthenticated", "로그인이 필요한 기능입니다.");
    }

    const db = getFirestore(admin.app(), "default");
    const userRef = db.collection("users").doc(uid);

    try {
      await userRef.get();
      await db.recursiveDelete(userRef);
    } catch (error) {
      logger.error("Firestore account delete failed", {
        uid,
        documentPath: userRef.path,
        message: error?.message ?? "unknown error",
        code: error?.code ?? "unknown",
        stack: error?.stack ?? "",
      });
      throw new HttpsError("internal", "사용자 데이터 삭제에 실패했습니다.");
    }

    try {
      await admin.auth().deleteUser(uid);
    } catch (error) {
      if (error?.code !== "auth/user-not-found") {
        logger.error("Auth user delete failed", {
          uid,
          message: error?.message ?? "unknown error",
          code: error?.code ?? "unknown",
          stack: error?.stack ?? "",
        });
        throw new HttpsError("internal", "인증 계정 삭제에 실패했습니다.");
      }
    }

    logger.info("Account deleted", { uid });

    return { success: true };
  },
);