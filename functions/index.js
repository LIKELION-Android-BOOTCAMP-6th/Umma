const { setGlobalOptions } = require("firebase-functions");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest, onCall, HttpsError } = require("firebase-functions/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");

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
const SESSION_USAGE_RETENTION_SECONDS = 90 * 24 * 60 * 60;
const USERS_COLLECTION = "users";
const USER_LEARNING_PREFERENCE_COLLECTION = "user_learning_preference";
const USER_LEARNING_PREFERENCE_CURRENT_DOC = "current";
const FLASHCARDS_COLLECTION = "flashcards";
const NOTIFICATION_SETTINGS_COLLECTION = "notification_settings";
const NOTIFICATION_DEVICES_COLLECTION = "notification_devices";
const NOTIFICATION_HISTORY_COLLECTION = "notification_history";
const SRS_REVIEW_NOTIFICATION_TYPE = "srs_review";
const SRS_REVIEW_ROUTE = "srs_study";
const DEFAULT_NOTIFICATION_TIMEZONE = "Asia/Seoul";
const DEFAULT_NOTIFICATION_TIME_MINUTES = 18 * 60;
const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = 24 * 60;
const SRS_NOTIFICATION_SCAN_LIMIT = 300;
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
    schedule: "* * * * *",
    timeZone: "Etc/UTC",
  },
  async () => {
    const now = Date.now();
    const candidateSnapshot = await firestore
      .collectionGroup(NOTIFICATION_SETTINGS_COLLECTION)
      .where("nextNotificationAt", "<=", now)
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
        const result = await processSrsReviewNotificationCandidate(settingsDoc, now);
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

async function processSrsReviewNotificationCandidate(settingsDoc, now) {
  const settings = normalizeNotificationSettings(settingsDoc.data() || {});
  const userRef = settingsDoc.ref.parent.parent;

  if (!userRef) {
    logger.warn("srs review notification skipped: missing user reference", {
      path: settingsDoc.ref.path,
    });
    return { status: "skipped", reason: "missing-user-ref", invalidTokenCount: 0 };
  }

  if (!settings.enabled) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, now);
    return { status: "skipped", reason: "disabled", invalidTokenCount: 0 };
  }

  const selectedLearningLanguage = await readSelectedLearningLanguage(userRef);
  if (!selectedLearningLanguage) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, now);
    return { status: "skipped", reason: "missing-selected-language", invalidTokenCount: 0 };
  }

  const localDate = getLocalDateKey(now, settings.timezone);
  const historyId = `${localDate}_${SRS_REVIEW_NOTIFICATION_TYPE}`;
  const historyRef = userRef
    .collection(NOTIFICATION_HISTORY_COLLECTION)
    .doc(historyId);

  const dueFlashcards = await loadEligibleDueFlashcards(
    userRef,
    selectedLearningLanguage,
    now,
  );
  if (dueFlashcards.length === 0) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, now);
    return { status: "skipped", reason: "no-due-flashcards", invalidTokenCount: 0 };
  }

  const activeDevices = await loadActiveNotificationDevices(userRef);
  if (activeDevices.length === 0) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, now);
    return { status: "skipped", reason: "no-active-devices", invalidTokenCount: 0 };
  }

  const historyClaimed = await createNotificationHistoryClaim(
    historyRef,
    localDate,
    selectedLearningLanguage,
    dueFlashcards.length,
    now,
  );
  if (!historyClaimed) {
    await advanceSrsNotificationSchedule(settingsDoc.ref, settings, now);
    return { status: "skipped", reason: "already-sent-today", invalidTokenCount: 0 };
  }

  const message = buildSrsReviewNotificationMessage(
    selectedLearningLanguage,
    dueFlashcards.length,
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

  await advanceSrsNotificationSchedule(settingsDoc.ref, settings, now);

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

async function readSelectedLearningLanguage(userRef) {
  const snapshot = await userRef
    .collection(USER_LEARNING_PREFERENCE_COLLECTION)
    .doc(USER_LEARNING_PREFERENCE_CURRENT_DOC)
    .get();

  const value = snapshot.get("selectedLearningLanguage");
  return stringValue(value).trim();
}

async function loadEligibleDueFlashcards(userRef, selectedLearningLanguage, now) {
  const snapshot = await userRef
    .collection(FLASHCARDS_COLLECTION)
    .where("language", "==", selectedLearningLanguage)
    .where("nextReviewAt", "<=", now)
    .get();

  return snapshot.docs.filter((doc) => {
    const reviewRating = stringValue(doc.get("lastReviewRating")).trim().toUpperCase();
    return SUPPORTED_SRS_REVIEW_RATINGS.has(reviewRating);
  });
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
  const nextNotificationAt = computeNextNotificationAt(
    settings.timezone,
    settings.preferredNotificationTimeMinutes,
    now,
  );

  await settingsRef.set(
    {
      nextNotificationAt,
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

function normalizePreferredNotificationTimeMinutes(value) {
  const preferredMinutes = numberValue(value);
  if (!Number.isFinite(preferredMinutes)) {
    return DEFAULT_NOTIFICATION_TIME_MINUTES;
  }

  const normalizedMinutes = Math.trunc(preferredMinutes);
  if (normalizedMinutes < 0 || normalizedMinutes >= MINUTES_PER_DAY) {
    return DEFAULT_NOTIFICATION_TIME_MINUTES;
  }

  return normalizedMinutes;
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

function computeNextNotificationAt(timezone, preferredNotificationTimeMinutes, now) {
  const zonedNow = getZonedDateTimeParts(now, timezone);
  const hour = Math.floor(preferredNotificationTimeMinutes / MINUTES_PER_HOUR);
  const minute = preferredNotificationTimeMinutes % MINUTES_PER_HOUR;

  const todayTarget = zonedDateTimeToEpochMillis(
    zonedNow.year,
    zonedNow.month,
    zonedNow.day,
    hour,
    minute,
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
    minute,
    0,
    timezone,
  );
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

function throwInvalidArgument(message) {
  const error = new Error(message);
  error.code = "invalid-argument";
  throw error;
}
