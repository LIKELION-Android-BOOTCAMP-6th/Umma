const { setGlobalOptions } = require("firebase-functions");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest, onCall, HttpsError } = require("firebase-functions/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");
const { handleDeleteAccount } = require("./deleteAccount");

admin.initializeApp();

setGlobalOptions({ maxInstances: 10 });

// OPENAI_API_KEY 는 Firebase Secret Manager 에 저장한다.
// 값은 배포된 함수 런타임에서만 읽고, Android 앱이나 repository 에는 절대 두지 않는다.
const openAiApiKey = defineSecret("OPENAI_API_KEY");
// umma-6804c 프로젝트의 Firestore database ID는 "(default)"가 아니라 "default"다.
// Admin SDK 기본 선택값에 맡기면 함수 런타임에서 존재하지 않는 DB를 바라봐 NOT_FOUND가 발생할 수 있다.
// usage 저장/집계 함수는 실제 앱 데이터가 있는 DB를 명시해 같은 `users/{uid}` 하위에 문서를 생성한다.
const firestore = getFirestore("default");
const fieldValue = admin.firestore.FieldValue;
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const CLEANUP_BATCH_LIMIT = 300;
const AI_CONTENT_REPORTS_COLLECTION = "ai_content_reports";
const ACCOUNT_DELETE_LOCKS_COLLECTION = "account_delete_locks";
const AI_CONTENT_REPORT_RETENTION_MS = 90 * 24 * 60 * 60 * 1000;
const AI_CONTENT_REPORT_NOTE_MAX_LENGTH = 300;
const SESSION_USAGE_RETENTION_SECONDS = 90 * 24 * 60 * 60;
const USERS_COLLECTION = "users";
const USER_LEARNING_PREFERENCE_COLLECTION = "user_learning_preference";
const USER_LEARNING_PREFERENCE_CURRENT_DOC = "current";
const FLASHCARDS_COLLECTION = "flashcards";
const FLASHCARD_SUMMARIES_COLLECTION = "flashcard_summaries";
const NOTIFICATION_SETTINGS_COLLECTION = "notification_settings";
const NOTIFICATION_DEVICES_COLLECTION = "notification_devices";
const NOTIFICATION_HISTORY_COLLECTION = "notification_history";
const SRS_REVIEW_NOTIFICATION_TYPE = "srs_review";
const SRS_REVIEW_ROUTE = "srs_study";
const MARKETING_NOTIFICATION_TYPE = "marketing";
const MARKETING_NOTIFICATION_ROUTE = "home";
const MARKETING_NOTIFICATION_CHANNEL_ID = "marketing_notifications";
const MARKETING_NOTIFICATION_TITLE = "Umma";
const MARKETING_SLOT_MORNING = "morning";
const MARKETING_SLOT_NOON = "noon";
const MARKETING_SLOT_AFTER_NOON = "after_noon";
const MARKETING_SLOT_MORNING_HOUR = 7;
const MARKETING_SLOT_NOON_HOUR = 12;
const MARKETING_SLOT_AFTER_NOON_HOUR = 17;
const DEFAULT_NOTIFICATION_TIMEZONE = "Asia/Seoul";
const DEFAULT_NOTIFICATION_TIME_MINUTES = 18 * 60;
const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = 24 * 60;
const SRS_NOTIFICATION_SCAN_LIMIT = 300;
const MARKETING_NOTIFICATION_SCAN_LIMIT = 500;
const SUPPORTED_SRS_REVIEW_RATINGS = new Set(["HARD", "GOOD", "EASY"]);
const INVALID_FCM_ERROR_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

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
 * Stores a session usage aggregate and updates the monthly aggregate in one server boundary.
 *
 * Android still receives the OpenAI Realtime usage event because the WebSocket is app-side.
 * The app sends only the session aggregate to this function. The function owns Firestore writes,
 * so clients do not need direct write permission for usage summary collections.
 */
exports.submitChatUsageSession = onRequest(
  {
    region: "us-central1",
    cors: false,
  },
  async (request, response) => {
    if (request.method !== "POST") {
      // Usage sync is a state-changing operation, so only POST is accepted.
      response.status(405).json({ error: "Method Not Allowed" });
      return;
    }

    try {
      const decodedToken = await verifyFirebaseIdToken(request);
      const session = normalizeUsageSessionPayload(request.body, decodedToken.uid);

      await firestore.runTransaction(async (transaction) => {
        const sessionRef = firestore
          .collection("users")
          .doc(decodedToken.uid)
          .collection("chat_usage_sessions")
          .doc(session.sessionId);
        const previousSnapshot = await transaction.get(sessionRef);
        const previousSession = previousSnapshot.exists ? previousSnapshot.data() : null;

        // The session document is the detailed/debuggable source for this session.
        // It is overwritten by sessionId so retrying the same session cannot create duplicate docs.
        transaction.set(sessionRef, {
          ...session,
          syncedAt: Date.now(),
          expiresAt: admin.firestore.Timestamp.fromMillis(
            session.endedAt + SESSION_USAGE_RETENTION_SECONDS * 1000,
          ),
        });

        // Monthly aggregate uses delta updates instead of scanning all session docs.
        // This keeps Firestore reads low and still prevents duplicate counting on retry.
        applyMonthlyUsageDelta(
          transaction,
          decodedToken.uid,
          previousSession,
          session,
        );
      });

      response.status(200).json({
        ok: true,
        sessionId: session.sessionId,
        monthlyPeriodId: getKstMonthPeriod(session.endedAt).id,
      });
    } catch (error) {
      if (error.code === "unauthenticated") {
        response.status(401).json({ error: "Unauthorized" });
        return;
      }
      if (error.code === "invalid-argument") {
        response.status(400).json({ error: error.message });
        return;
      }

      logger.error("Failed to submit chat usage session", error);
      response.status(500).json({ error: "Failed to submit chat usage session" });
    }
  },
);

/**
 * Stores an AI content report with server-controlled retention metadata.
 *
 * The client sends the report draft and identifying data, but the server owns
 * `reportedAt`/`expiresAt` so the 90-day retention window cannot be extended by
 * a skewed or tampered device clock.
 *
 * The user-existence check, lock check, and document write are executed in a single
 * transaction to prevent a race where deleteAccount sets the lock between the check
 * and the write (TOCTOU). If a document with the same reportId already exists the
 * existing document is kept and an ok response is returned (idempotent retry support).
 */
exports.submitAiContentReport = onCall(
  {
    region: "us-central1",
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "로그인이 필요한 기능입니다.");
    }

    const report = normalizeAiContentReportPayload(request.data, uid);

    // reportId는 클라이언트 입력을 신뢰하지 않고 서버에서 직접 계산한다.
    // 같은 (sessionId, reportedTurnId) → 항상 같은 reportId → 재시도 시 중복 문서 생성 방지.
    const reportId = buildReportId(report.sessionId, report.reportedTurnId);

    const userRef = firestore.collection(USERS_COLLECTION).doc(uid);
    const lockRef = firestore.collection(ACCOUNT_DELETE_LOCKS_COLLECTION).doc(uid);
    const reportDocRef = firestore.collection(AI_CONTENT_REPORTS_COLLECTION).doc(reportId);

    // reportedAt은 트랜잭션 외부에서 한 번 계산한다.
    // 트랜잭션이 재시도되더라도 보존 만료 시각의 미세한 차이는 허용 범위 내다.
    const reportedAt = Date.now();
    const expiresAt = reportedAt + AI_CONTENT_REPORT_RETENTION_MS;

    await firestore.runTransaction(async (tx) => {
      // 세 read를 병렬 실행해 왕복 지연을 줄인다.
      const [userSnapshot, lockSnapshot, existingSnapshot] = await Promise.all([
        tx.get(userRef),
        tx.get(lockRef),
        tx.get(reportDocRef),
      ]);

      if (!userSnapshot.exists) {
        throw new HttpsError("failed-precondition", "삭제된 계정에서는 신고를 저장할 수 없습니다.");
      }

      // lockSnapshot.exists만으로는 TTL이 지난 락을 걸러내지 못한다.
      // Firestore 규칙과 동일한 기준(expiresAt)으로 유효한 락인지 확인한다.
      const lockData = lockSnapshot.data();
      const isActiveLock = lockSnapshot.exists && (lockData?.expiresAt ?? 0) > Date.now();
      if (isActiveLock) {
        throw new HttpsError("failed-precondition", "계정 삭제 진행 중에는 신고를 저장할 수 없습니다.");
      }

      // 같은 reportId로 이미 저장된 문서가 있으면 재저장 없이 그대로 둔다.
      // 네트워크 재시도나 더블 탭으로 인한 중복 문서 생성을 방지한다.
      if (existingSnapshot.exists) {
        return;
      }

      tx.set(reportDocRef, {
        ...report,
        reportId,
        reportedAt,
        expiresAt,
        status: "New",
      });
    });

    return {
      ok: true,
      reportId,
      reportedAt,
      expiresAt,
    };
  },
);

/**
 * Deletes expired per-session usage documents.
 *
 * Firestore TTL can also handle this if enabled on `expiresAt`. The scheduled function keeps the
 * cleanup policy explicit in code for teams that have not enabled TTL yet.
 */
exports.cleanupExpiredChatUsageSessions = onSchedule(
  {
    region: "us-central1",
    schedule: "every 24 hours",
    timeZone: "Asia/Seoul",
  },
  async () => {
    const now = admin.firestore.Timestamp.now();
    const snapshot = await firestore
      .collectionGroup("chat_usage_sessions")
      .where("expiresAt", "<=", now)
      .limit(CLEANUP_BATCH_LIMIT)
      .get();

    if (snapshot.empty) {
      logger.info("expired chat usage session cleanup skipped: no documents");
      return;
    }

    const batch = firestore.batch();
    snapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });
    await batch.commit();

    logger.info("expired chat usage sessions deleted", {
      deletedCount: snapshot.size,
    });
  },
);

/**
 * Deletes expired AI content reports after the 90-day retention window.
 *
 * The Android app writes `expiresAt` as a millis value, so the cleanup query uses
 * the same numeric clock to keep the retention contract explicit in code.
 */
exports.cleanupExpiredAiContentReports = onSchedule(
  {
    region: "us-central1",
    schedule: "every 24 hours",
    timeZone: "Asia/Seoul",
  },
  async () => {
    let deletedCount = 0;
    let passCount = 0;

    while (passCount < 20) {
      const snapshot = await firestore
        .collection(AI_CONTENT_REPORTS_COLLECTION)
        .where("expiresAt", "<=", Date.now())
        .limit(CLEANUP_BATCH_LIMIT)
        .get();

      if (snapshot.empty) {
        break;
      }

      const batch = firestore.batch();
      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
      });
      await batch.commit();

      deletedCount += snapshot.size;
      passCount += 1;

      if (snapshot.size < CLEANUP_BATCH_LIMIT) {
        break;
      }
    }

    if (passCount === 20) {
      logger.warn("expired ai content report cleanup reached pass limit", {
        deletedCount,
        passCount,
      });
    }

    if (deletedCount === 0) {
      logger.info("expired ai content report cleanup skipped: no documents");
      return;
    }

    logger.info("expired ai content reports deleted", {
      deletedCount,
      passCount,
    });
  },
);

/**
 * Cleans up stale account-deletion lock documents.
 *
 * handleDeleteAccount deletes the lock on both success and failure paths, so this job
 * is a safety net for cases where the explicit delete call itself failed. Locks carry
 * an `expiresAt` timestamp (24 h from creation), so any document past that time is safe
 * to remove — the associated deletion either completed or permanently failed.
 */
exports.cleanupExpiredAccountDeleteLocks = onSchedule(
  {
    region: "us-central1",
    schedule: "every 24 hours",
    timeZone: "Asia/Seoul",
  },
  async () => {
    let deletedCount = 0;
    let passCount = 0;

    // 락은 uid 기반 단일 문서라 실제 잔존량이 많지 않지만,
    // ai_content_reports cleanup과 동일한 반복 구조로 맞춰 backlog가 쌓여도 정리되도록 한다.
    while (passCount < 5) {
      const snapshot = await firestore
        .collection(ACCOUNT_DELETE_LOCKS_COLLECTION)
        .where("expiresAt", "<=", Date.now())
        .limit(CLEANUP_BATCH_LIMIT)
        .get();

      if (snapshot.empty) {
        break;
      }

      const batch = firestore.batch();
      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
      });
      await batch.commit();

      deletedCount += snapshot.size;
      passCount += 1;

      if (snapshot.size < CLEANUP_BATCH_LIMIT) {
        break;
      }
    }

    if (passCount === 5) {
      logger.warn("expired account delete lock cleanup reached pass limit", {
        deletedCount,
        passCount,
      });
    }

    if (deletedCount === 0) {
      logger.info("expired account delete lock cleanup skipped: no documents");
      return;
    }

    logger.info("expired account delete locks deleted", {
      deletedCount,
      passCount,
    });
  },
);

/**
 * Sends at most one SRS review reminder per user per local date.
 *
 * The scheduler checks users whose configured notification time has arrived,
 * filters due flashcards by the currently selected learning language, excludes
 * `AGAIN` and never-reviewed cards, and fan-outs a data-only FCM payload to
 * every active Android device for that user.
 */
exports.dispatchSrsReviewNotifications = onSchedule(
  {
    region: "us-central1",
    schedule: "*/5 * * * *",
    timeZone: "Etc/UTC",
  },
  async () => {
    const now = Date.now();
    const currentHourBucket = truncateToHourBucket(now);
    const candidateSnapshot = await firestore
      .collectionGroup(NOTIFICATION_SETTINGS_COLLECTION)
      .where("type", "==", SRS_REVIEW_NOTIFICATION_TYPE)
      .where("enabled", "==", true)
      .where("nextNotificationBucketAt", "<=", currentHourBucket)
      .limit(SRS_NOTIFICATION_SCAN_LIMIT)
      .get();

    if (candidateSnapshot.empty) {
      logger.info("srs review notification dispatch skipped: no due settings");
      return;
    }

    const summary = {
      candidateCount: candidateSnapshot.size,
      processedCount: 0,
      sentCount: 0,
      skippedCount: 0,
      invalidTokenCount: 0,
      failureCount: 0,
    };

    for (const settingsDoc of candidateSnapshot.docs) {
      if (
        settingsDoc.id !== SRS_REVIEW_NOTIFICATION_TYPE ||
        settingsDoc.ref.parent.id !== NOTIFICATION_SETTINGS_COLLECTION
      ) {
        continue;
      }

      summary.processedCount += 1;

      try {
        const result = await processSrsReviewNotificationCandidate(
          settingsDoc,
          now,
          currentHourBucket,
        );
        summary.invalidTokenCount += result.invalidTokenCount;

        if (result.status === "sent") {
          summary.sentCount += 1;
        } else {
          summary.skippedCount += 1;
        }
      } catch (error) {
        summary.failureCount += 1;
        logger.error("srs review notification dispatch failed for user", {
          path: settingsDoc.ref.path,
          message: error.message,
        });
      }
    }

    logger.info("srs review notification dispatch completed", summary);
  },
);

/**
 * Sends marketing push notifications twice a day using each user's local time.
 *
 * Eligible users are those who explicitly enabled marketing notifications and
 * still have at least one active device with notification permission.
 */
exports.dispatchMarketingNotifications = onSchedule(
  {
    region: "us-central1",
    schedule: "*/5 * * * *",
    timeZone: "Etc/UTC",
  },
  async () => {
    const now = Date.now();
    const currentHourBucket = truncateToHourBucket(now);
    const candidateSnapshot = await firestore
      .collectionGroup(NOTIFICATION_SETTINGS_COLLECTION)
      .where("type", "==", MARKETING_NOTIFICATION_TYPE)
      .where("enabled", "==", true)
      .where("nextNotificationBucketAt", "<=", currentHourBucket)
      .limit(MARKETING_NOTIFICATION_SCAN_LIMIT)
      .get();

    if (candidateSnapshot.empty) {
      logger.info("marketing notification dispatch skipped: no enabled settings");
      return;
    }

    const summary = {
      candidateCount: candidateSnapshot.size,
      processedCount: 0,
      sentCount: 0,
      skippedCount: 0,
      invalidTokenCount: 0,
      failureCount: 0,
    };

    for (const settingsDoc of candidateSnapshot.docs) {
      if (
        settingsDoc.id !== MARKETING_NOTIFICATION_TYPE ||
        settingsDoc.ref.parent.id !== NOTIFICATION_SETTINGS_COLLECTION
      ) {
        continue;
      }

      summary.processedCount += 1;

      try {
        const result = await processMarketingNotificationCandidate(
          settingsDoc,
          now,
          currentHourBucket,
        );
        summary.invalidTokenCount += result.invalidTokenCount;

        if (result.status === "sent") {
          summary.sentCount += 1;
        } else {
          summary.skippedCount += 1;
        }
      } catch (error) {
        summary.failureCount += 1;
        logger.error("marketing notification dispatch failed for user", {
          path: settingsDoc.ref.path,
          message: error.message,
        });
      }
    }

    logger.info("marketing notification dispatch completed", summary);
  },
);

/**
 * Sends a test notification immediately to the caller's active devices when
 * the matching notification setting is enabled.
 *
 * This is intended for internal QA only. The caller must be authenticated and
 * can only target their own notification devices.
 */
exports.sendTestNotification = onCall(
  {
    region: "us-central1",
  },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Authentication required");
    }

    const type = stringValue(request.data?.type).trim();
    if (type !== SRS_REVIEW_NOTIFICATION_TYPE && type !== MARKETING_NOTIFICATION_TYPE) {
      throw new HttpsError(
        "invalid-argument",
        "type must be srs_review or marketing",
      );
    }

    const userRef = firestore.collection(USERS_COLLECTION).doc(request.auth.uid);
    const notificationEnabled = await isNotificationTypeEnabled(userRef, type);
    if (!notificationEnabled) {
      throw new HttpsError(
        "failed-precondition",
        `${type} notification setting is disabled`,
      );
    }

    const activeDevices = await loadActiveNotificationDevices(userRef);
    if (activeDevices.length === 0) {
      throw new HttpsError(
        "failed-precondition",
        "No active notification devices are available",
      );
    }

    const now = Date.now();
    const historyId = buildTestNotificationHistoryId(type, now);
    const message = buildTestNotificationMessage(type);
    let selectedLearningLanguage = null;

    if (type === SRS_REVIEW_NOTIFICATION_TYPE) {
      selectedLearningLanguage = await readSelectedLearningLanguage(userRef);
      if (!selectedLearningLanguage) {
        throw new HttpsError(
          "failed-precondition",
          "Selected learning language is required for srs_review",
        );
      }
    }

    const multicastResponse = await admin.messaging().sendEachForMulticast({
      tokens: activeDevices.map((device) => device.fcmToken),
      data: {
        type,
        route: type === SRS_REVIEW_NOTIFICATION_TYPE ? SRS_REVIEW_ROUTE : MARKETING_NOTIFICATION_ROUTE,
        historyId,
        title: message.title,
        body: message.body,
        ...(selectedLearningLanguage ? { lang: selectedLearningLanguage } : {}),
      },
    });

    const invalidDevices = extractInvalidNotificationDevices(
      activeDevices,
      multicastResponse.responses,
    );
    if (invalidDevices.length > 0) {
      await disableInvalidNotificationDevices(userRef, invalidDevices, now);
    }

    await userRef
      .collection(NOTIFICATION_HISTORY_COLLECTION)
      .doc(historyId)
      .set(
        {
          type,
          isTest: true,
          triggeredByUid: request.auth.uid,
          targetUid: request.auth.uid,
          selectedLangAtSend: selectedLearningLanguage,
          deliveryStatus: multicastResponse.successCount > 0 ? "sent" : "failed",
          deliveredDeviceCount: multicastResponse.successCount,
          invalidTokenCount: invalidDevices.length,
          sentAt: now,
          updatedAt: now,
        },
        { merge: true },
      );

    return {
      ok: multicastResponse.successCount > 0,
      type,
      historyId,
      targetDeviceCount: activeDevices.length,
      successCount: multicastResponse.successCount,
      failureCount: multicastResponse.failureCount,
      invalidTokenCount: invalidDevices.length,
    };
  },
);

function buildTestNotificationMessage(type) {
  if (type === SRS_REVIEW_NOTIFICATION_TYPE) {
    return {
      title: "Study reminder",
      body: "A review is ready. Open the app to check.",
    };
  }

  return {
    title: MARKETING_NOTIFICATION_TITLE,
    body: "Test marketing notification. Open the app to check.",
  };
}

function buildTestNotificationHistoryId(type, now) {
  return `test_${type}_${now}_${Math.random().toString(36).slice(2, 8)}`;
}

async function isNotificationTypeEnabled(userRef, type) {
  const snapshot = await userRef
    .collection(NOTIFICATION_SETTINGS_COLLECTION)
    .doc(type)
    .get();

  if (!snapshot.exists) {
    return false;
  }

  return Boolean(snapshot.data()?.enabled);
}

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

// 클라이언트와 동일한 sanitization 규칙으로 서버에서 직접 계산한다.
// uid를 포함하지 않으면서 같은 (sessionId, turnId)에 대해 항상 같은 id를 보장한다.
function buildReportId(sessionId, reportedTurnId) {
  const safeSession = sessionId.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 24);
  const safeTurn = reportedTurnId.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 24);
  return `report_${safeSession}_${safeTurn}`;
}

function normalizeAiContentReportPayload(data, uid) {
  const userId = stringValue(data?.userId).trim();
  if (userId !== uid) {
    throwInvalidArgument("userId must match authenticated user");
  }

  const sessionId = stringValue(data?.sessionId).trim();
  const reportedTurnId = stringValue(data?.reportedTurnId).trim();
  const reportedAiText = stringValue(data?.reportedAiText).trim();
  const primaryLang = stringValue(data?.primaryLang).trim();
  const selectedLang = stringValue(data?.selectedLang).trim();
  const reasonCategory = stringValue(data?.reasonCategory).trim();
  const appVersion = stringValue(data?.appVersion).trim();
  const modelVersion = stringValue(data?.modelVersion).trim();
  const promptVersion = stringValue(data?.promptVersion).trim();
  const promptRevision = stringValue(data?.promptRevision).trim();

  if (!sessionId) {
    throwInvalidArgument("sessionId is required");
  }
  if (!reportedTurnId) {
    throwInvalidArgument("reportedTurnId is required");
  }
  if (!reportedAiText) {
    throwInvalidArgument("reportedAiText is required");
  }
  if (!primaryLang) {
    throwInvalidArgument("primaryLang is required");
  }
  if (!selectedLang) {
    throwInvalidArgument("selectedLang is required");
  }
  if (!reasonCategory) {
    throwInvalidArgument("reasonCategory is required");
  }
  if (!appVersion || !modelVersion || !promptVersion || !promptRevision) {
    throwInvalidArgument("report metadata is required");
  }

  const contextTurns = Array.isArray(data?.contextTurns)
    ? data.contextTurns.map((turn) => normalizeAiContentReportContextTurn(turn, sessionId))
    : [];

  return {
    sessionId,
    reportedTurnId,
    reportedAiText,
    previousUserText: optionalStringValue(data?.previousUserText),
    contextTurns,
    primaryLang,
    selectedLang,
    reasonCategory,
    detailNote: optionalStringValue(data?.detailNote)?.slice(0, AI_CONTENT_REPORT_NOTE_MAX_LENGTH) ?? null,
    appVersion,
    modelVersion,
    promptVersion,
    promptRevision,
  };
}

function normalizeAiContentReportContextTurn(turn, sessionId) {
  const turnId = stringValue(turn?.turnId).trim();
  const role = stringValue(turn?.role).trim();
  const text = stringValue(turn?.text).trim();
  const createdAt = Number(turn?.createdAt);

  if (!turnId || !role || !text || !Number.isFinite(createdAt)) {
    throwInvalidArgument("invalid context turn");
  }

  return {
    turnId,
    sessionId: stringValue(turn?.sessionId).trim() || sessionId,
    role,
    text,
    createdAt,
  };
}

async function processSrsReviewNotificationCandidate(settingsDoc, now, currentHourBucket) {
  const settings = normalizeNotificationSettings(settingsDoc.data() || {});
  const userRef = settingsDoc.ref.parent.parent;

  if (!userRef) {
    logger.warn("srs review notification skipped: missing user reference", {
      path: settingsDoc.ref.path,
    });
    return { status: "skipped", reason: "missing-user-ref", invalidTokenCount: 0 };
  }

  const selectedLearningLanguage = await readSelectedLearningLanguage(userRef);
  if (!selectedLearningLanguage) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);
    return { status: "skipped", reason: "missing-selected-language", invalidTokenCount: 0 };
  }

  const localDate = getLocalDateKey(now, settings.timezone);
  const historyId = `${localDate}_${SRS_REVIEW_NOTIFICATION_TYPE}`;
  const historyRef = userRef
    .collection(NOTIFICATION_HISTORY_COLLECTION)
    .doc(historyId);

  const dueFlashcardCount = await recalculateNotifiableDueFlashcardsCount(
    userRef,
    selectedLearningLanguage,
    now,
  );
  await repairNotifiableDueFlashcardsSummary(
    userRef,
    selectedLearningLanguage,
    dueFlashcardCount,
    now,
  );
  if (dueFlashcardCount <= 0) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);
    return { status: "skipped", reason: "no-due-flashcards", invalidTokenCount: 0 };
  }

  const activeDevices = await loadActiveNotificationDevices(userRef);
  if (activeDevices.length === 0) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);
    return { status: "skipped", reason: "no-active-devices", invalidTokenCount: 0 };
  }

  const historyClaimed = await createNotificationHistoryClaim(
    historyRef,
    localDate,
    selectedLearningLanguage,
    dueFlashcardCount,
    now,
  );
  if (!historyClaimed) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);
    return { status: "skipped", reason: "already-sent-today", invalidTokenCount: 0 };
  }

  const message = buildSrsReviewNotificationMessage(
    selectedLearningLanguage,
    dueFlashcardCount,
  );
  const multicastResponse = await admin.messaging().sendEachForMulticast({
    tokens: activeDevices.map((device) => device.fcmToken),
    data: {
      type: SRS_REVIEW_NOTIFICATION_TYPE,
      route: SRS_REVIEW_ROUTE,
      lang: selectedLearningLanguage,
      historyId,
      title: message.title,
      body: message.body,
    },
  });

  const invalidDevices = extractInvalidNotificationDevices(
    activeDevices,
    multicastResponse.responses,
  );
  if (invalidDevices.length > 0) {
    await disableInvalidNotificationDevices(userRef, invalidDevices, now);
  }

  if (multicastResponse.successCount > 0) {
    await historyRef.set(
      {
        deliveryStatus: "sent",
        deliveredDeviceCount: multicastResponse.successCount,
        invalidTokenCount: invalidDevices.length,
        updatedAt: now,
      },
      { merge: true },
    );
  } else {
    await historyRef.set(
      {
        deliveryStatus: "failed",
        deliveredDeviceCount: 0,
        invalidTokenCount: invalidDevices.length,
        updatedAt: now,
      },
      { merge: true },
    );
  }

  await advanceSrsNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);

  return {
    status: multicastResponse.successCount > 0 ? "sent" : "skipped",
    reason:
      multicastResponse.successCount > 0 ? "sent" : "all-deliveries-failed",
    invalidTokenCount: invalidDevices.length,
  };
}

async function processMarketingNotificationCandidate(settingsDoc, now, currentHourBucket) {
  const settings = normalizeMarketingNotificationSettings(settingsDoc.data() || {});
  const userRef = settingsDoc.ref.parent.parent;
  if (!userRef) {
    logger.warn("marketing notification skipped: missing user reference", {
      path: settingsDoc.ref.path,
    });
    return { status: "skipped", reason: "missing-user-ref", invalidTokenCount: 0 };
  }

  const activeDevices = await loadActiveNotificationDevices(userRef);
  if (activeDevices.length === 0) {
    await advanceMarketingNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);
    return { status: "skipped", reason: "no-active-devices", invalidTokenCount: 0 };
  }

  const slot = resolveMarketingCampaignSlot(currentHourBucket, settings.timezone);
  if (!slot) {
    await advanceMarketingNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);
    return { status: "skipped", reason: "outside-campaign-window", invalidTokenCount: 0 };
  }

  const historyId = `${slot.localDate}_${MARKETING_NOTIFICATION_TYPE}_${slot.name}`;
  const historyRef = userRef
    .collection(NOTIFICATION_HISTORY_COLLECTION)
    .doc(historyId);

  const historyClaimed = await createMarketingHistoryClaim(
    historyRef,
    slot,
    settings.timezone,
    now,
  );
  if (!historyClaimed) {
    await advanceMarketingNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);
    return { status: "skipped", reason: "already-sent-today", invalidTokenCount: 0 };
  }

  const message = buildMarketingNotificationMessage(slot.name);
  const multicastResponse = await admin.messaging().sendEachForMulticast({
    tokens: activeDevices.map((device) => device.fcmToken),
    data: {
      type: MARKETING_NOTIFICATION_TYPE,
      route: MARKETING_NOTIFICATION_ROUTE,
      historyId,
      campaignSlot: slot.name,
      title: message.title,
      body: message.body,
    },
    android: {
      priority: "high",
    },
  });

  const invalidDevices = extractInvalidNotificationDevices(
    activeDevices,
    multicastResponse.responses,
  );
  if (invalidDevices.length > 0) {
    await disableInvalidNotificationDevices(userRef, invalidDevices, now);
  }

  await historyRef.set(
    {
      deliveryStatus: multicastResponse.successCount > 0 ? "sent" : "failed",
      deliveredDeviceCount: multicastResponse.successCount,
      invalidTokenCount: invalidDevices.length,
      updatedAt: now,
    },
    { merge: true },
  );

  await advanceMarketingNotificationSchedule(settingsDoc.ref, settings, currentHourBucket);

  return {
    status: multicastResponse.successCount > 0 ? "sent" : "skipped",
    reason:
      multicastResponse.successCount > 0 ? "sent" : "all-deliveries-failed",
    invalidTokenCount: invalidDevices.length,
  };
}

async function createNotificationHistoryClaim(
  historyRef,
  localDate,
  selectedLearningLanguage,
  dueCount,
  now,
) {
  try {
    await historyRef.create({
      type: SRS_REVIEW_NOTIFICATION_TYPE,
      localDate,
      selectedLangAtSend: selectedLearningLanguage,
      dueCount,
      sentAt: now,
      deliveryStatus: "processing",
      deliveredDeviceCount: 0,
      invalidTokenCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    return true;
  } catch (error) {
    if (error.code === 6 || error.code === "already-exists") {
      return false;
    }
    throw error;
  }
}

async function createMarketingHistoryClaim(historyRef, slot, timezone, now) {
  try {
    await historyRef.create({
      type: MARKETING_NOTIFICATION_TYPE,
      campaignSlot: slot.name,
      localDate: slot.localDate,
      timezone,
      sentAt: now,
      deliveryStatus: "processing",
      deliveredDeviceCount: 0,
      invalidTokenCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    return true;
  } catch (error) {
    if (error.code === 6 || error.code === "already-exists") {
      return false;
    }
    throw error;
  }
}

async function readSelectedLearningLanguage(userRef) {
  const snapshot = await userRef
    .collection(USER_LEARNING_PREFERENCE_COLLECTION)
    .doc(USER_LEARNING_PREFERENCE_CURRENT_DOC)
    .get();

  const value = snapshot.get("selectedLearningLanguage");
  return stringValue(value).trim();
}

async function recalculateNotifiableDueFlashcardsCount(
  userRef,
  selectedLearningLanguage,
  now,
) {
  const snapshot = await userRef
    .collection(FLASHCARDS_COLLECTION)
    .where("language", "==", selectedLearningLanguage)
    .where("nextReviewAt", "<=", now)
    .get();

  return snapshot.docs.reduce((count, doc) => {
    const lastReviewRating = stringValue(doc.get("lastReviewRating")).trim();
    return SUPPORTED_SRS_REVIEW_RATINGS.has(lastReviewRating) ? count + 1 : count;
  }, 0);
}

async function repairNotifiableDueFlashcardsSummary(
  userRef,
  selectedLearningLanguage,
  dueFlashcardCount,
  now,
) {
  const snapshot = await userRef
    .collection(FLASHCARD_SUMMARIES_COLLECTION)
    .doc(selectedLearningLanguage);

  await snapshot.set(
    {
      language: selectedLearningLanguage,
      notifiableDueFlashcards: dueFlashcardCount,
      updatedAt: now,
    },
    { merge: true },
  );
}

async function loadActiveNotificationDevices(userRef) {
  const snapshot = await userRef
    .collection(NOTIFICATION_DEVICES_COLLECTION)
    .get();

  return snapshot.docs
    .map((doc) => ({
      ref: doc.ref,
      deviceId: doc.id,
      permissionGranted: Boolean(doc.get("permissionGranted")),
      fcmToken: stringValue(doc.get("fcmToken")).trim(),
      timezone: sanitizeTimeZone(doc.get("timezone")),
      updatedAt: numberValue(doc.get("updatedAt")),
    }))
    .filter((device) => device.permissionGranted && device.fcmToken.length > 0);
}

function buildSrsReviewNotificationMessage(selectedLearningLanguage, dueCount) {
  const languageLabel = selectedLearningLanguage.toUpperCase();
  return {
    title: "학습 알림",
    body: `복습할 ${languageLabel} 카드 ${dueCount}개가 있습니다. 앱에서 확인해보세요.`,
  };
}

function buildMarketingNotificationMessage(slotName) {
  if (slotName === MARKETING_SLOT_MORNING) {
    return {
      title: MARKETING_NOTIFICATION_TITLE,
      body: "AI와 대화를 하며 하루를 시작해보세요!\n*알림끄기: 마이페이지 > 마케팅 알림",
    };
  }

  if (slotName == MARKETING_SLOT_NOON) {
    return {
       title: MARKETING_NOTIFICATION_TITLE,
       body: "Umma에서는 다양한 언어를 학습할 수 있어요!\n지금 접속해서 언어 능력을 향상 시켜보세요!\n*알림끄기: 마이페이지 > 마케팅 알림",
    };
  }

  return {
      title: MARKETING_NOTIFICATION_TITLE,
      body: "플래시 카드를 통해 문장을 학습해보세요!\n플래시 카드는 대화 후 교정을 통해 추가할 수 있어요!\n*알림끄기: 마이페이지 > 마케팅 알림"
  }



}

function resolveMarketingCampaignSlot(referenceMillis, timezone) {
  const zonedNow = getZonedDateTimeParts(referenceMillis, timezone);

  if (zonedNow.hour === MARKETING_SLOT_MORNING_HOUR) {
    return {
      name: MARKETING_SLOT_MORNING,
      localDate: `${zonedNow.year}${padNumber(zonedNow.month)}${padNumber(zonedNow.day)}`,
    };
  }

  if (zonedNow.hour === MARKETING_SLOT_NOON_HOUR) {
    return {
      name: MARKETING_SLOT_NOON,
      localDate: `${zonedNow.year}${padNumber(zonedNow.month)}${padNumber(zonedNow.day)}`,
    };
  }

  if (zonedNow.hour === MARKETING_SLOT_AFTER_NOON_HOUR) {
      return {
        name: MARKETING_SLOT_AFTER_NOON,
        localDate: `${zonedNow.year}${padNumber(zonedNow.month)}${padNumber(zonedNow.day)}`,
      };
    }



  return null;
}

function extractInvalidNotificationDevices(activeDevices, responses) {
  const invalidDevices = [];

  responses.forEach((response, index) => {
    if (response.success) {
      return;
    }

    if (INVALID_FCM_ERROR_CODES.has(response.error?.code)) {
      invalidDevices.push(activeDevices[index]);
    }
  });

  return invalidDevices;
}

async function disableInvalidNotificationDevices(userRef, invalidDevices, now) {
  if (invalidDevices.length === 0) {
    return;
  }

  const batch = firestore.batch();
  invalidDevices.forEach((device) => {
    batch.set(
      userRef.collection(NOTIFICATION_DEVICES_COLLECTION).doc(device.deviceId),
      {
        permissionGranted: false,
        fcmToken: "",
        updatedAt: now,
      },
      { merge: true },
    );
  });
  await batch.commit();
}

async function advanceSrsNotificationSchedule(settingsRef, settings, now) {
  const nextNotificationBucketAt = computeNextNotificationBucketAt(
    settings.timezone,
    settings.preferredNotificationTimeMinutes,
    now,
  );

  await settingsRef.set(
    {
      nextNotificationAt: nextNotificationBucketAt,
      nextNotificationBucketAt,
      updatedAt: now,
    },
    { merge: true },
  );
}

async function advanceMarketingNotificationSchedule(settingsRef, settings, now) {
  const nextNotificationBucketAt = computeNextMarketingNotificationBucketAt(
    settings.timezone,
    now,
  );

  await settingsRef.set(
    {
      nextNotificationBucketAt,
      updatedAt: now,
    },
    { merge: true },
  );
}

function normalizeNotificationSettings(data) {
  return {
    enabled: Boolean(data.enabled),
    timezone: sanitizeTimeZone(data.timezone),
    preferredNotificationTimeMinutes: normalizePreferredNotificationTimeMinutes(
      data.preferredNotificationTimeMinutes,
    ),
  };
}

function normalizeMarketingNotificationSettings(data) {
  return {
    enabled: Boolean(data.enabled),
    timezone: sanitizeTimeZone(data.timezone),
  };
}

function normalizePreferredNotificationTimeMinutes(value) {
  const preferredMinutes = numberValue(value);
  if (!Number.isFinite(preferredMinutes)) {
    return DEFAULT_NOTIFICATION_TIME_MINUTES;
  }

  const normalizedMinutes = Math.trunc(preferredMinutes);
  if (normalizedMinutes < 0 || normalizedMinutes >= MINUTES_PER_DAY) {
    return DEFAULT_NOTIFICATION_TIME_MINUTES;
  }

  return Math.floor(normalizedMinutes / MINUTES_PER_HOUR) * MINUTES_PER_HOUR;
}

function sanitizeTimeZone(timezone) {
  const trimmedTimezone = stringValue(timezone).trim();
  if (!trimmedTimezone) {
    return DEFAULT_NOTIFICATION_TIMEZONE;
  }

  try {
    Intl.DateTimeFormat("en-US", { timeZone: trimmedTimezone }).format(new Date());
    return trimmedTimezone;
  } catch (error) {
    logger.warn("invalid notification timezone. default timezone will be used", {
      timezone: trimmedTimezone,
    });
    return DEFAULT_NOTIFICATION_TIMEZONE;
  }
}

function getLocalDateKey(referenceMillis, timezone) {
  const zonedDate = getZonedDateTimeParts(referenceMillis, timezone);
  return `${zonedDate.year}${padNumber(zonedDate.month)}${padNumber(zonedDate.day)}`;
}

function computeNextNotificationBucketAt(timezone, preferredNotificationTimeMinutes, now) {
  const zonedNow = getZonedDateTimeParts(now, timezone);
  const hour = Math.floor(preferredNotificationTimeMinutes / MINUTES_PER_HOUR);

  const todayTarget = zonedDateTimeToEpochMillis(
    zonedNow.year,
    zonedNow.month,
    zonedNow.day,
    hour,
    0,
    0,
    timezone,
  );
  if (todayTarget > now) {
    return todayTarget;
  }

  const tomorrow = addCivilDays(zonedNow.year, zonedNow.month, zonedNow.day, 1);
  return zonedDateTimeToEpochMillis(
    tomorrow.year,
    tomorrow.month,
    tomorrow.day,
    hour,
    0,
    0,
    timezone,
  );
}

function computeNextMarketingNotificationBucketAt(timezone, now) {
  const zonedNow = getZonedDateTimeParts(now, timezone);
  const morningTarget = zonedDateTimeToEpochMillis(
    zonedNow.year,
    zonedNow.month,
    zonedNow.day,
    MARKETING_SLOT_MORNING_HOUR,
    0,
    0,
    timezone,
  );
  if (morningTarget > now) {
    return morningTarget;
  }

  const noonTarget = zonedDateTimeToEpochMillis(
    zonedNow.year,
    zonedNow.month,
    zonedNow.day,
    MARKETING_SLOT_NOON_HOUR,
    0,
    0,
    timezone,
  );
  if (noonTarget > now) {
    return noonTarget;
  }

  const afterNoonTarget = zonedDateTimeToEpochMillis(
     zonedNow.year,
     zonedNow.month,
     zonedNow.day,
     MARKETING_SLOT_AFTER_NOON_HOUR,
     0,
     0,
     timezone,
  );
  if (afterNoonTarget > now) {
      return afterNoonTarget;
  }

  const tomorrow = addCivilDays(zonedNow.year, zonedNow.month, zonedNow.day, 1);
  return zonedDateTimeToEpochMillis(
    tomorrow.year,
    tomorrow.month,
    tomorrow.day,
    MARKETING_SLOT_MORNING_HOUR,
    0,
    0,
    timezone,
  );
}

function truncateToHourBucket(referenceMillis) {
  const date = new Date(referenceMillis);
  date.setUTCMinutes(0, 0, 0);
  return date.getTime();
}

function getZonedDateTimeParts(referenceMillis, timezone) {
  const parts = getTimeZoneFormatter(timezone).formatToParts(new Date(referenceMillis));
  return parts.reduce((accumulator, part) => {
    if (part.type === "year" || part.type === "month" || part.type === "day") {
      accumulator[part.type] = Number(part.value);
    }
    if (part.type === "hour" || part.type === "minute" || part.type === "second") {
      accumulator[part.type] = Number(part.value);
    }
    return accumulator;
  }, {});
}

const timeZoneFormatterCache = new Map();

function getTimeZoneFormatter(timezone) {
  if (!timeZoneFormatterCache.has(timezone)) {
    timeZoneFormatterCache.set(
      timezone,
      new Intl.DateTimeFormat("en-US", {
        timeZone: timezone,
        hourCycle: "h23",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      }),
    );
  }

  return timeZoneFormatterCache.get(timezone);
}

function zonedDateTimeToEpochMillis(year, month, day, hour, minute, second, timezone) {
  let guess = Date.UTC(year, month - 1, day, hour, minute, second);

  for (let attempt = 0; attempt < 5; attempt += 1) {
    const actual = getZonedDateTimeParts(guess, timezone);
    const desiredAsUtc = Date.UTC(year, month - 1, day, hour, minute, second);
    const actualAsUtc = Date.UTC(
      actual.year,
      actual.month - 1,
      actual.day,
      actual.hour,
      actual.minute,
      actual.second,
    );
    const diff = desiredAsUtc - actualAsUtc;

    if (diff === 0) {
      return guess;
    }

    guess += diff;
  }

  return guess;
}

function addCivilDays(year, month, day, days) {
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return {
    year: date.getUTCFullYear(),
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
  };
}

function padNumber(value) {
  return String(value).padStart(2, "0");
}

function emptyUsageAggregate({ uid, periodId, periodStart, periodEnd }) {
  return {
    id: periodId,
    userId: uid,
    aggregateType: "monthly",
    periodId,
    periodStart,
    periodEnd,
    sessionCount: 0,
    recordCount: 0,
    responseCount: 0,
    transcriptionCount: 0,
    responseUsage: emptyUsageObject(),
    transcriptionUsage: emptyUsageObject(),
    totalUsage: emptyUsageObject(),
    updatedAt: Date.now(),
  };
}

function emptyUsageObject() {
  return {
    totalTokens: 0,
    inputTokens: 0,
    outputTokens: 0,
    inputTextTokens: 0,
    inputAudioTokens: 0,
    inputCachedTokens: 0,
    outputTextTokens: 0,
    outputAudioTokens: 0,
  };
}

function sumUsageObjects(left, right) {
  const total = emptyUsageObject();

  Object.keys(total).forEach((key) => {
    total[key] = numberValue(left[key]) + numberValue(right[key]);
  });

  return total;
}

function numberValue(value) {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function toMillis(value) {
  if (typeof value === "number") return value;
  if (value && typeof value.toMillis === "function") return value.toMillis();
  return null;
}

function getKstMonthPeriod(referenceMillis) {
  const kstDate = new Date(referenceMillis + KST_OFFSET_MS);
  const year = kstDate.getUTCFullYear();
  const month = kstDate.getUTCMonth();
  const startMillis = Date.UTC(year, month, 1) - KST_OFFSET_MS;
  const endMillis = Date.UTC(year, month + 1, 1) - KST_OFFSET_MS;

  return {
    id: `${year}${pad2(month + 1)}`,
    startMillis,
    endMillis,
  };
}

function pad2(value) {
  return String(value).padStart(2, "0");
}

function normalizeUsageSessionPayload(body, uid) {
  const payload = body || {};

  if (payload.userId !== uid) {
    throwInvalidArgument("usage userId does not match signed-in user");
  }
  if (typeof payload.sessionId !== "string" || payload.sessionId.length === 0) {
    throwInvalidArgument("sessionId is required");
  }

  const startedAt = numberValue(payload.startedAt);
  const endedAt = numberValue(payload.endedAt);
  if (startedAt <= 0 || endedAt <= 0) {
    throwInvalidArgument("startedAt and endedAt are required");
  }

  return {
    id: payload.sessionId,
    userId: uid,
    sessionId: payload.sessionId,
    language: stringValue(payload.language),
    model: stringValue(payload.model),
    startedAt,
    endedAt,
    recordCount: numberValue(payload.recordCount),
    responseCount: numberValue(payload.responseCount),
    transcriptionCount: numberValue(payload.transcriptionCount),
    responseUsage: normalizeUsageObject(payload.responseUsage),
    transcriptionUsage: normalizeUsageObject(payload.transcriptionUsage),
    pricingVersion: stringValue(payload.pricingVersion),
  };
}

function normalizeUsageObject(source) {
  const value = source || {};

  return {
    totalTokens: numberValue(value.totalTokens),
    inputTokens: numberValue(value.inputTokens),
    outputTokens: numberValue(value.outputTokens),
    inputTextTokens: numberValue(value.inputTextTokens),
    inputAudioTokens: numberValue(value.inputAudioTokens),
    inputCachedTokens: numberValue(value.inputCachedTokens),
    outputTextTokens: numberValue(value.outputTextTokens),
    outputAudioTokens: numberValue(value.outputAudioTokens),
  };
}

function applyMonthlyUsageDelta(transaction, uid, previousSession, nextSession) {
  const nextPeriod = getKstMonthPeriod(nextSession.endedAt);

  if (previousSession) {
    const previousEndedAt = toMillis(previousSession.endedAt);
    const previousPeriod = previousEndedAt ? getKstMonthPeriod(previousEndedAt) : null;

    if (previousPeriod && previousPeriod.id !== nextPeriod.id) {
      // 세션 시간이 월 경계를 넘어 이동한 경우, 이전 월에서는 기존 값을 빼고 새 월에 다시 더한다.
      incrementMonthlyUsage(transaction, uid, previousPeriod, previousSession, -1, -1);
      incrementMonthlyUsage(transaction, uid, nextPeriod, nextSession, 1, 1);
      return;
    }

    // 같은 sessionId가 재전송된 경우 전체 값을 다시 더하지 않고 차이만 반영한다.
    incrementMonthlyUsage(
      transaction,
      uid,
      nextPeriod,
      diffUsageSession(nextSession, previousSession),
      0,
      1,
    );
    return;
  }

  // 신규 세션은 월별 sessionCount를 1 증가시키고 usage 전체를 더한다.
  incrementMonthlyUsage(transaction, uid, nextPeriod, nextSession, 1, 1);
}

function incrementMonthlyUsage(transaction, uid, period, session, sessionCountDelta, sign) {
  const monthlyRef = firestore
    .collection("users")
    .doc(uid)
    .collection("chat_usage_monthly")
    .doc(period.id);
  const responseUsage = normalizeUsageObject(session.responseUsage);
  const transcriptionUsage = normalizeUsageObject(session.transcriptionUsage);
  const totalUsage = sumUsageObjects(responseUsage, transcriptionUsage);

  transaction.set(
    monthlyRef,
    {
      ...emptyUsageAggregate({
        uid,
        periodId: period.id,
        periodStart: period.startMillis,
        periodEnd: period.endMillis,
      }),
      sessionCount: fieldValue.increment(sessionCountDelta),
      recordCount: fieldValue.increment(sign * numberValue(session.recordCount)),
      responseCount: fieldValue.increment(sign * numberValue(session.responseCount)),
      transcriptionCount: fieldValue.increment(sign * numberValue(session.transcriptionCount)),
      responseUsage: usageIncrementMap(responseUsage, sign),
      transcriptionUsage: usageIncrementMap(transcriptionUsage, sign),
      totalUsage: usageIncrementMap(totalUsage, sign),
      updatedAt: Date.now(),
    },
    { merge: true },
  );
}

function diffUsageSession(nextSession, previousSession) {
  return {
    recordCount: numberValue(nextSession.recordCount) - numberValue(previousSession.recordCount),
    responseCount:
      numberValue(nextSession.responseCount) - numberValue(previousSession.responseCount),
    transcriptionCount:
      numberValue(nextSession.transcriptionCount) -
      numberValue(previousSession.transcriptionCount),
    responseUsage: diffUsageObject(nextSession.responseUsage, previousSession.responseUsage),
    transcriptionUsage: diffUsageObject(
      nextSession.transcriptionUsage,
      previousSession.transcriptionUsage,
    ),
  };
}

function diffUsageObject(nextUsage, previousUsage) {
  const next = normalizeUsageObject(nextUsage);
  const previous = normalizeUsageObject(previousUsage);
  const diff = emptyUsageObject();

  Object.keys(diff).forEach((key) => {
    diff[key] = numberValue(next[key]) - numberValue(previous[key]);
  });

  return diff;
}

function usageIncrementMap(usage, sign) {
  const normalizedUsage = normalizeUsageObject(usage);
  const incrementMap = {};

  Object.keys(normalizedUsage).forEach((key) => {
    incrementMap[key] = fieldValue.increment(sign * numberValue(normalizedUsage[key]));
  });

  return incrementMap;
}

function stringValue(value) {
  return typeof value === "string" ? value : "";
}

function optionalStringValue(value) {
  const normalized = stringValue(value).trim();
  return normalized.length ? normalized : null;
}

function throwInvalidArgument(message) {
  const error = new Error(message);
  error.code = "invalid-argument";
  throw error;
}

// 회원탈퇴 Cloud Functions API
exports.deleteAccount = onCall(
  {
    region: "us-central1",
  },
  async (request) => {
    return handleDeleteAccount({
      auth: admin.auth(),
      db: getFirestore(admin.app(), "default"),
      loggerImpl: logger,
      request,
    });
  },
);
