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
 * 각 turn마다 원격 write를 하지 않고 세션 종료 시점에 세션 문서 하나로 flush합니다.
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
                // turn마다 Firestore에 쓰지 않고 세션 종료 시점에 모아서 저장해 비용과 지연을 줄입니다.
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

    override suspend fun flushSession(
        userId: String,
        sessionId: String
    ): Result<Unit> {
        if (!isEnabled()) return Result.success(Unit)
        if (userId.isBlank() || sessionId.isBlank()) return Result.success(Unit)

        return runCatching {
            val key = bufferKey(userId, sessionId)
            val buffer = bufferMutex.withLock {
                // flush 중 같은 세션이 다시 들어와도 중복 write를 피하기 위해 먼저 버퍼에서 분리합니다.
                sessionBuffers.remove(key)
            } ?: return@runCatching

            val sessionRef = reviewSessionDocument(userId, sessionId)
            val sortedEvents = buffer.events
                .sortedWith(compareBy<ChatPromptReviewEvent> { it.createdAt }.thenBy { it.eventId })
            val sessionMap = mapOf(
                "sessionId" to buffer.sessionId,
                "language" to buffer.language.code,
                "enabled" to true,
                "createdAt" to buffer.createdAt,
                "updatedAt" to buffer.updatedAt,
                "sessionPromptTrace" to buffer.sessionPromptTrace,
                "metadata" to buffer.metadata,
                "appBuildType" to BuildConfig.BUILD_TYPE,
                "appFlavor" to BuildConfig.FLAVOR,
                "eventCount" to sortedEvents.size,
                "events" to sortedEvents.map(::eventMap)
            )

            // 개발용 리뷰 자료는 세션 단위 문서 하나에 모아 저장해 Firestore write 수를 세션당 1회로 줄입니다.
            sessionRef.set(sessionMap, SetOptions.merge()).await()
            // 개발자가 Logcat 한 줄만 복사해도 export 대상을 정확히 지정할 수 있도록 성공 후에만 식별 로그를 남깁니다.
            Log.i(
                LOG_TAG,
                "sessionFlushed uid=$userId sessionId=$sessionId eventCount=${sortedEvents.size} " +
                    "firestorePath=users/$userId/$CHAT_PROMPT_REVIEWS_COLLECTION/$sessionId"
            )
        }
    }

    private fun reviewSessionDocument(userId: String, sessionId: String) = firestore
        .collection(USERS_COLLECTION)
        .document(userId)
        .collection(CHAT_PROMPT_REVIEWS_COLLECTION)
        .document(sessionId)

    private companion object {
        private const val LOG_TAG = "AiChatPromptReview"
        private const val USERS_COLLECTION = "users"
        private const val CHAT_PROMPT_REVIEWS_COLLECTION = "chat_prompt_reviews"
    }
}

private data class ChatPromptReviewSessionBuffer(
    val userId: String,
    val sessionId: String,
    val language: LangCode,
    val createdAt: Long,
    val updatedAt: Long,
    val sessionPromptTrace: String?,
    val metadata: String?,
    val events: MutableList<ChatPromptReviewEvent>
)

private fun bufferKey(userId: String, sessionId: String): String {
    return "$userId/$sessionId"
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
