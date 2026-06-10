const { HttpsError } = require("firebase-functions/https");

const FORCE_LOGOUT_TYPE = "force_logout";
const USERS_COLLECTION = "users";

function stringValue(value) {
  return typeof value === "string" ? value : "";
}

/**
 * 로그인 성공 직후 호출되는 callable의 핵심 로직.
 * 새 sessionId를 발급해 users/{uid}.activeSession을 갱신하고,
 * 요청한 deviceId(=새로 로그인한 기기 자신)를 제외한 등록 기기에 force_logout FCM을 전송한다.
 */
async function handleClaimLoginSession({
  firestore,
  messaging,
  request,
  now,
  randomUUIDImpl,
  loadNotificationDevices,
  extractInvalidNotificationDevices,
  disableInvalidNotificationDevices,
}) {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication required");
  }

  const deviceId = stringValue(request.data?.deviceId).trim();
  if (!deviceId) {
    throw new HttpsError("invalid-argument", "deviceId is required");
  }

  const sessionId = randomUUIDImpl();
  const userRef = firestore.collection(USERS_COLLECTION).doc(request.auth.uid);

  await userRef.set(
    {
      activeSession: {
        deviceId,
        sessionId,
        updatedAt: now,
      },
    },
    { merge: true },
  );

  const activeDevices = await loadNotificationDevices(userRef);
  const targetDevices = activeDevices.filter((device) => device.deviceId !== deviceId);

  if (targetDevices.length > 0) {
    const multicastResponse = await messaging.sendEachForMulticast({
      tokens: targetDevices.map((device) => device.fcmToken),
      data: {
        type: FORCE_LOGOUT_TYPE,
        sessionId,
      },
    });

    const invalidDevices = extractInvalidNotificationDevices(
      targetDevices,
      multicastResponse.responses,
    );
    if (invalidDevices.length > 0) {
      await disableInvalidNotificationDevices(userRef, invalidDevices, now);
    }
  }

  return { sessionId };
}

module.exports = { handleClaimLoginSession, FORCE_LOGOUT_TYPE };
