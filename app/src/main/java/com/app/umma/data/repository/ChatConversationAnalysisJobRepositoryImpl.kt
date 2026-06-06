package com.app.umma.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.app.umma.domain.model.chat.ChatConversationAnalysisJob
import com.app.umma.domain.model.chat.ChatConversationAnalysisJobTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.repository.ChatConversationAnalysisJobRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Chat conversation analysis pending job을 Preferences DataStore에 저장한다.
 *
 * Room migration을 추가하지 않고도 강제 종료 복구에 필요한 최소 job queue를 만들기 위한 구현이다.
 * retry 시점에 SessionMemory가 Correction에 의해 압축될 수 있으므로, 세션 식별자뿐 아니라
 * Gemini 분석에 필요한 최소 final turn snapshot도 함께 보관한다.
 */
@Singleton
class ChatConversationAnalysisJobRepositoryImpl @Inject constructor(
    @param:Named("chatConversationAnalysisDataStore")
    private val dataStore: DataStore<Preferences>
) : ChatConversationAnalysisJobRepository {

    override suspend fun enqueue(job: ChatConversationAnalysisJob): Result<Unit> {
        return runCatching {
            require(job.userId.isNotBlank()) { "userId must not be blank" }
            require(job.sessionId.isNotBlank()) { "sessionId must not be blank" }
            require(job.finalTurnCount >= 0) { "finalTurnCount must not be negative" }
            require(job.turns.isNotEmpty()) { "turns must not be empty" }

            dataStore.edit { prefs ->
                val currentJobs = prefs[PENDING_JOBS].orEmpty().mapNotNull { encoded -> encoded.decodeJobOrNull() }
                // 같은 사용자/언어/세션 job은 하나만 유지한다. turn 수가 갱신되면 최신 예약 정보로 덮는다.
                val nextJobs = (currentJobs.filterNot { existing -> existing.key == job.key } + job)
                    .sortedByDescending { queued -> queued.createdAt }
                    .take(MAX_STORED_JOBS)
                prefs[PENDING_JOBS] = nextJobs.map { queued -> queued.encode() }.toSet()
            }
        }
    }

    override suspend fun getPendingJobs(userId: String, limit: Int): Result<List<ChatConversationAnalysisJob>> {
        return runCatching {
            if (userId.isBlank()) return@runCatching emptyList()
            val safeLimit = limit.coerceAtLeast(0)
            if (safeLimit == 0) return@runCatching emptyList()

            dataStore.data.first()[PENDING_JOBS]
                .orEmpty()
                .mapNotNull { encoded -> encoded.decodeJobOrNull() }
                // 현재 로그인 사용자 job만 반환해 계정 전환 후 이전 사용자의 대화를 분석하지 않게 한다.
                .filter { job -> job.userId == userId }
                .sortedWith(compareBy<ChatConversationAnalysisJob> { it.lastAttemptedAt ?: 0L }.thenBy { it.createdAt })
                .take(safeLimit)
        }
    }

    override suspend fun markAttempted(
        job: ChatConversationAnalysisJob,
        attemptedAt: Long
    ): Result<Unit> {
        return runCatching {
            dataStore.edit { prefs ->
                val nextJobs = prefs[PENDING_JOBS]
                    .orEmpty()
                    .mapNotNull { encoded -> encoded.decodeJobOrNull() }
                    .map { queued ->
                        if (queued.key == job.key) {
                            // retry 실패 여부를 나중에 추적할 수 있게 관찰 metadata만 갱신한다.
                            // attemptCount는 아직 retry 차단 조건으로 쓰지 않아 복구 기회를 임의로 줄이지 않는다.
                            queued.copy(
                                lastAttemptedAt = attemptedAt,
                                attemptCount = queued.attemptCount + 1
                            )
                        } else {
                            queued
                        }
                    }
                prefs[PENDING_JOBS] = nextJobs.map { queued -> queued.encode() }.toSet()
            }
        }
    }

    override suspend fun markCompleted(
        userId: String,
        selectedLang: LangCode,
        sessionId: String
    ): Result<Unit> {
        return runCatching {
            // 완료 기준은 사용자/언어/세션 조합이다.
            // 같은 sessionId라도 사용자나 언어가 다르면 다른 pending job으로 취급한다.
            val completedKey = ChatConversationAnalysisJob.key(
                userId = userId,
                selectedLang = selectedLang,
                sessionId = sessionId
            )
            dataStore.edit { prefs ->
                val nextJobs = prefs[PENDING_JOBS]
                    .orEmpty()
                    .mapNotNull { encoded -> encoded.decodeJobOrNull() }
                    .filterNot { job -> job.key == completedKey }
                if (nextJobs.isEmpty()) {
                    prefs.remove(PENDING_JOBS)
                } else {
                    prefs[PENDING_JOBS] = nextJobs.map { job -> job.encode() }.toSet()
                }
            }
        }
    }

    override suspend fun clearAll(): Result<Unit> {
        return runCatching {
            // 로그아웃/회원탈퇴 시 pending snapshot을 모두 제거해 계정 간 대화 분석이 섞이지 않게 한다.
            dataStore.edit { prefs -> prefs.clear() }
        }
    }

    private fun ChatConversationAnalysisJob.encode(): String {
        // Preferences DataStore에는 복합 객체를 직접 넣을 수 없으므로 job 단위를 JSON 문자열로 보관한다.
        // 구분자 기반 직렬화를 쓰지 않아 transcript 안의 특수문자와 줄바꿈이 queue를 깨지 않게 한다.
        return json.encodeToString(
            ChatConversationAnalysisJobDto(
                userId = userId,
                selectedLang = selectedLang.code,
                sessionId = sessionId,
                finalTurnCount = finalTurnCount,
                turns = turns.map { turn -> turn.toDto() },
                createdAt = createdAt,
                lastAttemptedAt = lastAttemptedAt,
                attemptCount = attemptCount
            )
        )
    }

    private fun String.decodeJobOrNull(): ChatConversationAnalysisJob? {
        // 오래된/손상된 entry는 전체 queue를 실패시키지 않고 해당 entry만 버린다.
        // pending retry는 보조 복구 작업이므로 corrupted local data가 Chat 진입을 막으면 안 된다.
        val dto = runCatching { json.decodeFromString<ChatConversationAnalysisJobDto>(this) }.getOrNull()
            ?: return null
        val lang = LangCode.fromCode(dto.selectedLang) ?: return null
        // retry payload가 없는 job은 session id만으로 복구할 수 없다.
        // SessionMemory가 압축됐을 수 있다는 전제 때문에 최소 turn snapshot이 필수다.
        val turns = dto.turns.mapNotNull { turn -> turn.toDomainOrNull() }
            .takeIf { it.isNotEmpty() }
            ?: return null
        return ChatConversationAnalysisJob(
            userId = dto.userId.trim().takeIf { it.isNotBlank() } ?: return null,
            selectedLang = lang,
            sessionId = dto.sessionId.trim().takeIf { it.isNotBlank() } ?: return null,
            finalTurnCount = dto.finalTurnCount,
            turns = turns,
            createdAt = dto.createdAt,
            lastAttemptedAt = dto.lastAttemptedAt,
            attemptCount = dto.attemptCount
        )
    }

    private fun ChatConversationAnalysisJobTurn.toDto(): ChatConversationAnalysisJobTurnDto {
        // domain enum은 name으로 저장해 DTO가 별도 enum migration 없이도 현재 TurnSpeaker와 동기화되게 한다.
        return ChatConversationAnalysisJobTurnDto(
            speaker = speaker.name,
            text = text,
            createdAt = createdAt,
            tokenCount = tokenCount,
            durationMs = durationMs,
            confidence = confidence
        )
    }

    private fun ChatConversationAnalysisJobTurnDto.toDomainOrNull(): ChatConversationAnalysisJobTurn? {
        // speaker/text가 복원되지 않으면 Gemini 분석 payload로 사용할 수 없으므로 해당 turn만 제외한다.
        val speaker = runCatching { TurnSpeaker.valueOf(speaker) }.getOrNull() ?: return null
        val safeText = text.trim().takeIf { it.isNotBlank() } ?: return null
        return ChatConversationAnalysisJobTurn(
            speaker = speaker,
            text = safeText,
            createdAt = createdAt,
            tokenCount = tokenCount,
            durationMs = durationMs,
            confidence = confidence
        )
    }

    @Serializable
    private data class ChatConversationAnalysisJobDto(
        // userId는 계정 전환 후 다른 사용자의 pending job을 실행하지 않기 위한 필터 키다.
        val userId: String,
        // selectedLang은 LangState update 대상 언어이자 Gemini 분석 prompt의 학습 언어다.
        val selectedLang: String,
        // sessionId는 idempotency와 debug trace를 연결하는 기준이다.
        val sessionId: String,
        // finalTurnCount는 실제 분석 payload 크기보다 예약 당시 화면이 본 turn 수를 남기는 관찰용 값이다.
        val finalTurnCount: Int,
        // turns가 없으면 retry가 SessionMemory에 다시 의존하게 되므로 필수 payload로 둔다.
        val turns: List<ChatConversationAnalysisJobTurnDto>,
        val createdAt: Long,
        val lastAttemptedAt: Long? = null,
        val attemptCount: Int = 0
    )

    @Serializable
    private data class ChatConversationAnalysisJobTurnDto(
        // USER/AI 역할이 있어야 Gemini가 대화 왕복 구조를 평가할 수 있다.
        val speaker: String,
        // 확정 transcript만 저장한다. partial/delta는 분석 근거로 사용하지 않는다.
        val text: String,
        // retry payload에서도 원래 대화 순서를 유지하기 위한 정렬 기준이다.
        val createdAt: Long,
        // 아래 metadata는 LangState evidence 계산 보조값이며 없으면 null로 둔다.
        val tokenCount: Int? = null,
        val durationMs: Long? = null,
        val confidence: Double? = null
    )

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val PENDING_JOBS = stringSetPreferencesKey("chat_conversation_analysis_pending_jobs")
        const val MAX_STORED_JOBS = 30
    }
}
