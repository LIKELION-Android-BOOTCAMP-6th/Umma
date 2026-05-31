const { setGlobalOptions } = require("firebase-functions");
const { onRequest, onCall, HttpsError } = require("firebase-functions/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

if (!admin.apps.length) {
    admin.initializeApp();
}
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
        // CHAT-POC-001에서는 Android 앱이 아직 Auth/App Check 토큰을 전달하지 않으므로
        // Cloud Run 공개 액세스를 임시로 허용한다. PoC 후에는 인증 검증을 붙이고 닫아야 한다.
        cors: false,
    },
    async (request, response) => {
        if (request.method !== "POST") {
            // 앱은 POST 로만 호출한다. GET 호출을 막아 브라우저 직접 접근을 token 발급 경로로 쓰지 않게 한다.
            response.status(405).json({ error: "Method Not Allowed" });
            return;
        }

        try {
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
                    payload,
                });
                response.status(openAiResponse.status).json(payload);
                return;
            }

            response.status(200).json(payload);
        } catch (error) {
            // 네트워크나 Secret 접근 같은 서버 내부 실패는 Android 에 상세 key 값을 노출하지 않는다.
            logger.error("Failed to issue OpenAI realtime token", error);
            response.status(500).json({
                error: "Failed to issue realtime token",
            });
        }
    },
);

// 회원탈퇴 Cloud Functions API
exports.deleteAccount = onCall(
    {
        region: "us-central1",
    },
    async (req) => {
        const uid = req.auth?.uid;

        if (!uid) {
            throw new HttpsError("unauthenticated", "로그인이 필요한 기능입니다.");
        }

        const db = admin.firestore();
        const userRef = db.collection("users").document(uid);

        try {
            // users/{uid} 와 모든 하위 컬렉션 재귀 삭제
            await db.recursiveDelete(userRef);
        } catch (error) {
            logger.error("Firestore recursive delete failed", { uid, error });
            throw new HttpsError("internal", "사용자 데이터 삭제에 실패했습니다.");
        }

        try {
            // Firebase Auth 계정 삭제
            await admin.auth().deleteUser(uid);
        } catch (e) {
            logger.error("Google 계정 삭제 실패", { uid, e });
            throw new HttpsError("internal", "Google 인증 계정 삭제에 실패했습니다.");
        }

        logger.info("계정 삭제 완료", { uid });

        return {
            success: true,
            uid,
        };
    },
);