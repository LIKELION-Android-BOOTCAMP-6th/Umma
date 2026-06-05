package com.app.umma.devtools.chatpromptreview

import android.util.Log
import com.app.umma.BuildConfig
import com.app.umma.domain.model.learningstate.LangCode
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
        sessionId: String
    ): Result<Unit> {
        if (!isEnabled()) return Result.success(Unit)
        if (userId.isBlank() || sessionId.isBlank()) return Result.success(Unit)

        return runCatching {
            val now = System.currentTimeMillis()
            val key = bufferKey(userId, sessionId)
            bufferMutex.withLock {
                val existing = sessionBuffers[key] ?: return@withLock
                // 신고 버튼은 "이 세션을 분석 대상으로 남긴다"는 의사 표시다.
                // 이후 종료 flush에서도 저장되도록 버퍼에 reported 상태를 보존한다.
                sessionBuffers[key] = existing.copy(
                    reportedAt = existing.reportedAt ?: now,
                    updatedAt = now
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

            val sessionRef = reviewSessionDocument(userId, sessionId)
            val sortedEvents = snapshot.events
                .sortedWith(compareBy<ChatPromptReviewEvent> { it.createdAt }.thenBy { it.eventId })
            val reportRef = reviewReportDocument(userId, sessionId)
            val reviewPath = "users/$userId/$CHAT_PROMPT_REVIEWS_COLLECTION/$sessionId"
            val status = if (finalFlush) "ready" else "reported"
            val sessionMap = mapOf(
                "sessionId" to snapshot.sessionId,
                "language" to snapshot.language.code,
                "enabled" to true,
                "createdAt" to snapshot.createdAt,
                "updatedAt" to snapshot.updatedAt,
                "reportedAt" to snapshot.reportedAt,
                "sessionPromptTrace" to snapshot.sessionPromptTrace,
                "metadata" to snapshot.metadata,
                "appBuildType" to BuildConfig.BUILD_TYPE,
                "appFlavor" to BuildConfig.FLAVOR,
                "eventCount" to sortedEvents.size,
                "events" to sortedEvents.map(::eventMap)
            )
            val reportMap = mapOf(
                "reportId" to reportDocumentId(userId, sessionId),
                "uid" to userId,
                "sessionId" to sessionId,
                "reviewPath" to reviewPath,
                "language" to snapshot.language.code,
                "status" to status,
                "reportedAt" to snapshot.reportedAt,
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
                    "status=$status firestorePath=$reviewPath reportPath=$CHAT_PROMPT_REVIEW_REPORTS_COLLECTION/${reportDocumentId(userId, sessionId)}"
            )
        }
    }

    private fun reviewSessionDocument(userId: String, sessionId: String) = firestore
        .collection(USERS_COLLECTION)
        .document(userId)
        .collection(CHAT_PROMPT_REVIEWS_COLLECTION)
        .document(sessionId)

    private fun reviewReportDocument(userId: String, sessionId: String) = firestore
        .collection(CHAT_PROMPT_REVIEW_REPORTS_COLLECTION)
        .document(reportDocumentId(userId, sessionId))

    private companion object {
        private const val LOG_TAG = "AiChatPromptReview"
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
        sessionPromptTrace = sessionPromptTrace,
        metadata = metadata,
        // MutableList를 그대로 넘기면 partial flush 중 새 event 추가와 충돌할 수 있어 복사본만 밖으로 내보낸다.
        events = events.toList()
    )
}

private fun bufferKey(userId: String, sessionId: String): String {
    return "$userId/$sessionId"
}

private fun reportDocumentId(userId: String, sessionId: String): String {
    // uid/sessionId를 그대로 이어 붙이면 같은 세션 신고가 하나의 index 문서로 merge되어 중복 신고를 만들지 않는다.
    return "${userId}_${sessionId}".replace(Regex("[^A-Za-z0-9_-]"), "_")
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
        "debugTrace" to event.debugTrace,
        "hasInstructions" to event.hasInstructions,
        "outputAudioSpeed" to event.outputAudioSpeed,
        "metadata" to event.metadata
    )
}
