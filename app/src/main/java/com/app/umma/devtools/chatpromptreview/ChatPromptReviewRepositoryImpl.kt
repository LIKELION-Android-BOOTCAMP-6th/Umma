package com.app.umma.devtools.chatpromptreview

import android.util.Log
import com.app.umma.BuildConfig
import com.app.umma.domain.model.learningstate.LangCode
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Firestore 기반 개발용 프롬프트 리뷰 저장소입니다.
 *
 * `CHAT_PROMPT_REVIEW_ENABLED=false`이면 모든 쓰기를 즉시 no-op 처리합니다.
 * 저장 위치를 SessionMemory와 분리해 나중에 도구 전체를 제거해도 운영 저장 계약이 흔들리지 않게 합니다.
 * 각 turn마다 원격 write를 하지 않고, 신고된 세션만 신고 시점과 종료 시점에 flush합니다.
 */
@Singleton
class ChatPromptReviewRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ChatPromptReviewRepository {
    private val bufferMutex = Mutex()
    private val sessionBuffers = mutableMapOf<String, ChatPromptReviewSessionBuffer>()

    @Suppress("KotlinConstantConditions")
    override fun isEnabled(): Boolean {
        // release에서는 local flag가 실수로 켜져도 운영 데이터가 쌓이지 않도록 항상 닫습니다.
        // 현재 튜닝 단계에서는 debug 기본값을 true로 고정해 IDE가 상수 조건으로 볼 수 있으므로 의도적으로 suppress합니다.
        return BuildConfig.DEBUG && BuildConfig.CHAT_PROMPT_REVIEW_ENABLED
    }

    override suspend fun recordSessionStarted(
        userId: String,
        sessionId: String,
        language: LangCode,
        sessionPromptTrace: String?,
        metadata: String
    ): Result<Unit> {
        if (!isEnabled()) return Result.success(Unit)
        if (userId.isBlank() || sessionId.isBlank()) return Result.success(Unit)

        return runCatching {
            val now = System.currentTimeMillis()
            bufferMutex.withLock {
                // 세션 시작 시점에는 원격 write를 하지 않고, exporter가 읽을 session metadata만 메모리에 준비합니다.
                val key = bufferKey(userId, sessionId)
                val existing = sessionBuffers[key]
                sessionBuffers[key] = ChatPromptReviewSessionBuffer(
                    userId = userId,
                    sessionId = sessionId,
                    language = language,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    reportedAt = existing?.reportedAt,
                    reportId = existing?.reportId,
                    reportNote = existing?.reportNote,
                    promptVersion = extractPromptVersion(sessionPromptTrace) ?: existing?.promptVersion,
                    promptRevision = extractPromptRevision(sessionPromptTrace) ?: existing?.promptRevision,
                    promptBand = extractPromptBand(sessionPromptTrace) ?: existing?.promptBand,
                    sessionPromptTrace = sessionPromptTrace,
                    metadata = metadata,
                    events = existing?.events.orEmpty().toMutableList()
                )
            }
        }
    }

    override suspend fun recordEvent(
        userId: String,
        event: ChatPromptReviewEvent
    ): Result<Unit> {
        if (!isEnabled()) return Result.success(Unit)
        if (userId.isBlank() || event.sessionId.isBlank() || event.eventId.isBlank()) {
            return Result.success(Unit)
        }

        return runCatching {
            bufferMutex.withLock {
                // turn마다 Firestore에 쓰지 않고 메모리에만 모은다.
                // 신고된 세션만 reportSession/flushSession 경로에서 원격에 남긴다.
                val key = bufferKey(userId, event.sessionId)
                val existing = sessionBuffers[key]
                val buffer = existing ?: ChatPromptReviewSessionBuffer(
                    userId = userId,
                    sessionId = event.sessionId,
                    language = event.language,
                    createdAt = event.createdAt,
                    updatedAt = event.createdAt,
                    sessionPromptTrace = null,
                    reportId = null,
                    promptVersion = null,
                    promptRevision = null,
                    promptBand = null,
                    reportNote = null,
                    metadata = null,
                    events = mutableListOf()
                )
                buffer.events += event
                sessionBuffers[key] = buffer.copy(
                    language = event.language,
                    updatedAt = event.createdAt
                )
            }
        }
    }

    override suspend fun reportSession(
        userId: String,
        sessionId: String,
        reportNote: String?
    ): Result<Unit> {
        if (!isEnabled()) return Result.success(Unit)
        if (userId.isBlank() || sessionId.isBlank()) return Result.success(Unit)

        return runCatching {
            val now = System.currentTimeMillis()
            val key = bufferKey(userId, sessionId)
            val sanitizedReportNote = sanitizeReportNote(reportNote)
            bufferMutex.withLock {
                val existing = sessionBuffers[key] ?: return@withLock
                val reportedAt = existing.reportedAt ?: now
                val reportId = existing.reportId ?: reportDocumentId(
                    reportedAt = reportedAt,
                    language = existing.language,
                    sessionId = sessionId
                )
                // 신고 버튼은 "이 세션을 분석 대상으로 남긴다"는 의사 표시다.
                // 이후 종료 flush에서도 저장되도록 버퍼에 reported 상태를 보존한다.
                // reportNote는 문제를 느낀 상황을 사람이 빠르게 재구성하기 위한 메모라 세션 단위로 보관한다.
                sessionBuffers[key] = existing.copy(
                    reportedAt = reportedAt,
                    updatedAt = now,
                    reportId = reportId,
                    reportNote = sanitizedReportNote ?: existing.reportNote
                )
            }

            flushSession(
                userId = userId,
                sessionId = sessionId,
                finalFlush = false
            ).getOrThrow()
        }
    }

    override suspend fun flushSession(
        userId: String,
        sessionId: String,
        finalFlush: Boolean
    ): Result<Unit> {
        if (!isEnabled()) return Result.success(Unit)
        if (userId.isBlank() || sessionId.isBlank()) return Result.success(Unit)

        return runCatching {
            val key = bufferKey(userId, sessionId)
            val snapshot = bufferMutex.withLock {
                val existing = sessionBuffers[key]
                // 신고하지 않은 세션은 원격에 남기지 않는다. 개발 테스트 중 생성되는 일반 세션 노이즈와 비용을 줄이기 위함이다.
                if (existing?.reportedAt == null) {
                    if (finalFlush) {
                        sessionBuffers.remove(key)
                    }
                    return@withLock null
                }
                // 신고 직후 partial flush는 계속 이벤트를 받을 수 있어 버퍼를 유지한다.
                // 세션 종료 final flush에서만 메모리 누수를 막기 위해 제거한다.
                if (finalFlush) {
                    sessionBuffers.remove(key)
                }
                // Firestore write는 mutex 밖에서 수행하되, 이벤트 목록은 여기서 immutable snapshot으로 고정한다.
                // 신고 후 대화가 계속되어 새 event가 들어와도 partial flush가 mutable list와 충돌하지 않게 하기 위함이다.
                existing.toSnapshot()
            } ?: return@runCatching

            val sortedEvents = snapshot.events
                .sortedWith(compareBy<ChatPromptReviewEvent> { it.createdAt }.thenBy { it.eventId })
            val reportId = snapshot.reportId ?: reportDocumentId(
                reportedAt = snapshot.reportedAt ?: snapshot.updatedAt,
                language = snapshot.language,
                sessionId = sessionId
            )
            // review 본문도 report index와 같은 시간 기반 ID를 사용한다.
            // Firestore 콘솔에서 users/{uid}/chat_prompt_reviews를 열었을 때 신고 목록과 같은 순서/이름으로 찾기 위함이다.
            val sessionRef = reviewSessionDocument(userId, reportId)
            val reportRef = reviewReportDocument(reportId)
            val reviewPath = "users/$userId/$CHAT_PROMPT_REVIEWS_COLLECTION/$reportId"
            val status = if (finalFlush) "ready" else "reported"
            val sessionMap = mapOf(
                "reviewId" to reportId,
                "sessionId" to snapshot.sessionId,
                "language" to snapshot.language.code,
                "enabled" to true,
                "createdAt" to snapshot.createdAt,
                "updatedAt" to snapshot.updatedAt,
                "reportedAt" to snapshot.reportedAt,
                "promptVersion" to snapshot.promptVersion,
                "promptRevision" to snapshot.promptRevision,
                "promptBand" to snapshot.promptBand,
                "reportNote" to snapshot.reportNote,
                "sessionPromptTrace" to snapshot.sessionPromptTrace,
                "metadata" to snapshot.metadata,
                "appBuildType" to BuildConfig.BUILD_TYPE,
                "appFlavor" to BuildConfig.FLAVOR,
                "eventCount" to sortedEvents.size,
                "events" to sortedEvents.map(::eventMap)
            )
            val reportMap = mapOf(
                "reportId" to reportId,
                "uid" to userId,
                "sessionId" to sessionId,
                "reviewId" to reportId,
                "reviewPath" to reviewPath,
                "language" to snapshot.language.code,
                "status" to status,
                "reportedAt" to snapshot.reportedAt,
                "promptVersion" to snapshot.promptVersion,
                "promptRevision" to snapshot.promptRevision,
                "promptBand" to snapshot.promptBand,
                "reportNote" to snapshot.reportNote,
                "updatedAt" to snapshot.updatedAt,
                "appBuildType" to BuildConfig.BUILD_TYPE,
                "appFlavor" to BuildConfig.FLAVOR,
                "eventCount" to sortedEvents.size
            )

            // 신고된 세션만 실제 리뷰 문서와 전역 인덱스에 저장한다.
            // report index는 PM/개발자가 Firestore에서 "봐야 할 세션"만 빠르게 찾기 위한 얇은 문서다.
            sessionRef.set(sessionMap, SetOptions.merge()).await()
            reportRef.set(reportMap, SetOptions.merge()).await()
            // 신고 버튼을 누르면 로그 공유 없이도 Firestore index로 찾을 수 있지만,
            // 로컬 확인과 수동 export를 위해 기존 식별 로그도 유지한다.
            Log.i(
                LOG_TAG,
                "sessionFlushed uid=$userId sessionId=$sessionId eventCount=${sortedEvents.size} " +
                    "status=$status promptBand=${snapshot.promptBand ?: "unknown"} " +
                    "promptRevision=${snapshot.promptRevision ?: "unknown"} " +
                    "firestorePath=$reviewPath reportPath=$CHAT_PROMPT_REVIEW_REPORTS_COLLECTION/$reportId"
            )
            // Prompt 분석 시 AiChatPromptTrace 하나만 필터링해도 신고 세션 식별자가 보이도록 같은 핵심 정보를 짧게 남긴다.
            Log.i(
                PROMPT_TRACE_LOG_TAG,
                "review_saved lang=${snapshot.language.code} session=$sessionId " +
                    "status=$status band=${snapshot.promptBand ?: "unknown"} revision=${snapshot.promptRevision ?: "unknown"} " +
                    "events=${sortedEvents.size} reportId=$reportId"
            )
        }
    }

    private fun reviewSessionDocument(userId: String, reviewId: String) = firestore
        .collection(USERS_COLLECTION)
        .document(userId)
        .collection(CHAT_PROMPT_REVIEWS_COLLECTION)
        .document(reviewId)

    private fun reviewReportDocument(reportId: String) = firestore
        .collection(CHAT_PROMPT_REVIEW_REPORTS_COLLECTION)
        .document(reportId)

    private companion object {
        private const val LOG_TAG = "AiChatPromptReview"
        private const val PROMPT_TRACE_LOG_TAG = "AiChatPromptTrace"
        private const val USERS_COLLECTION = "users"
        private const val CHAT_PROMPT_REVIEWS_COLLECTION = "chat_prompt_reviews"
        private const val CHAT_PROMPT_REVIEW_REPORTS_COLLECTION = "chat_prompt_review_reports"
    }
}

private data class ChatPromptReviewSessionBuffer(
    val userId: String,
    val sessionId: String,
    val language: LangCode,
    val createdAt: Long,
    val updatedAt: Long,
    val reportedAt: Long? = null,
    val reportId: String?,
    val promptVersion: String?,
    val promptRevision: String?,
    val promptBand: String?,
    val reportNote: String?,
    val sessionPromptTrace: String?,
    val metadata: String?,
    val events: MutableList<ChatPromptReviewEvent>
)

private data class ChatPromptReviewSessionSnapshot(
    val sessionId: String,
    val language: LangCode,
    val createdAt: Long,
    val updatedAt: Long,
    val reportedAt: Long?,
    val reportId: String?,
    val promptVersion: String?,
    val promptRevision: String?,
    val promptBand: String?,
    val reportNote: String?,
    val sessionPromptTrace: String?,
    val metadata: String?,
    val events: List<ChatPromptReviewEvent>
)

private fun ChatPromptReviewSessionBuffer.toSnapshot(): ChatPromptReviewSessionSnapshot {
    return ChatPromptReviewSessionSnapshot(
        sessionId = sessionId,
        language = language,
        createdAt = createdAt,
        updatedAt = updatedAt,
        reportedAt = reportedAt,
        reportId = reportId,
        promptVersion = promptVersion,
        promptRevision = promptRevision,
        promptBand = promptBand,
        reportNote = reportNote,
        sessionPromptTrace = sessionPromptTrace,
        metadata = metadata,
        // MutableList를 그대로 넘기면 partial flush 중 새 event 추가와 충돌할 수 있어 복사본만 밖으로 내보낸다.
        events = events.toList()
    )
}

private fun bufferKey(userId: String, sessionId: String): String {
    return "$userId/$sessionId"
}

private fun reportDocumentId(
    reportedAt: Long,
    language: LangCode,
    sessionId: String
): String {
    // Firestore 콘솔에서 시간순으로 바로 찾을 수 있게 KST 24시간 표기와 언어를 문서 ID 앞에 둔다.
    // 같은 초에 같은 언어 신고가 여러 개 생길 수 있어 session prefix를 뒤에 붙여 충돌을 피한다.
    val timestamp = formatReportTimestamp(reportedAt)
    val sessionPrefix = sessionId
        .take(REPORT_ID_SESSION_PREFIX_LENGTH)
        .ifBlank { "unknown" }
    return "${timestamp}_${language.code}_$sessionPrefix"
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
}

private fun extractPromptVersion(sessionPromptTrace: String?): String? {
    // trace 전체를 별도 파싱 모델로 만들지 않고, index에 필요한 promptVersion 값만 안전하게 추출한다.
    if (sessionPromptTrace.isNullOrBlank()) return null
    return Regex("""\bpromptVersion=([^\s]+)""")
        .find(sessionPromptTrace)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

private fun extractPromptRevision(sessionPromptTrace: String?): String? {
    // promptVersion은 큰 구조 버전이고, revision은 같은 구조 안의 반복 튜닝 식별자다.
    if (sessionPromptTrace.isNullOrBlank()) return null
    return Regex("""\bpromptRevision=([^\s]+)""")
        .find(sessionPromptTrace)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

private fun extractPromptBand(sessionPromptTrace: String?): String? {
    // 신고 목록에서 세션별 적용 band를 바로 볼 수 있도록 trace의 style 요약에서 band만 추출한다.
    if (sessionPromptTrace.isNullOrBlank()) return null
    return Regex("""style=\{band=([^,}]+)""")
        .find(sessionPromptTrace)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

private fun sanitizeReportNote(reportNote: String?): String? {
    // 신고 메모는 Firestore index에서 바로 읽는 사람이 보는 값이므로 공백을 정리하고 과도한 길이는 제한한다.
    return reportNote
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(MAX_REPORT_NOTE_LENGTH)
        ?.takeIf { it.isNotBlank() }
}

private const val MAX_REPORT_NOTE_LENGTH = 500
private const val REPORT_ID_SESSION_PREFIX_LENGTH = 8
private const val REPORT_ID_TIME_ZONE = "Asia/Seoul"
private const val REPORT_ID_TIME_PATTERN = "yyyyMMdd_HHmmss"

private fun formatReportTimestamp(timestampMillis: Long): String {
    // minSdk 24에서는 java.time desugaring 의존 없이 동작하도록 java.text 포맷터를 매 호출 생성한다.
    return SimpleDateFormat(REPORT_ID_TIME_PATTERN, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone(REPORT_ID_TIME_ZONE) }
        .format(Date(timestampMillis))
}

private fun eventMap(event: ChatPromptReviewEvent): Map<String, Any?> {
    return mapOf(
        "eventId" to event.eventId,
        "sessionId" to event.sessionId,
        "type" to event.type.name,
        "language" to event.language.code,
        "createdAt" to event.createdAt,
        "role" to event.role?.name,
        "turnId" to event.turnId,
        "text" to event.text,
        "metadata" to event.metadata
    )
}
