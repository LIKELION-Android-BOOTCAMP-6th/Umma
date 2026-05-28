package com.app.umma.data.repository

import android.util.Log
import com.app.umma.data.repository.realtime.TopicSummaryAiClient
import com.app.umma.data.source.local.RemoteSyncStatus
import com.app.umma.data.source.local.SessionMemoryLocalDataSource
import com.app.umma.data.source.local.SessionMetadataEntity
import com.app.umma.data.source.local.SessionTurnEntity
import com.app.umma.data.source.remote.SessionMemoryRemoteDataSource
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

/**
 * Session Memory 의 local-first 저장과 batch sync 를 담당하는 Repository 구현체입니다.
 */
class SessionMemoryRepositoryImpl @Inject constructor(
    private val localDataSource: SessionMemoryLocalDataSource,
    private val remoteDataSource: SessionMemoryRemoteDataSource,
    private val authRepository: AuthRepository,
    // 세션 주제 요약 AI 호출 어댑터. 교정 완료 직후 topicSummaries 갱신에만 사용한다. (#162-C)
    private val topicSummaryAiClient: TopicSummaryAiClient
) : SessionMemoryRepository {

    /**
     * recentFullContext 에 유지할 최대 turn 수입니다.
     */
    private val maxRecentTurns: Int = 100

    /**
     * 의미 있는 발화로 판단할 최소 길이입니다.
     */
    private val minMeaningfulTextLength: Int = 2

    override suspend fun appendTurn(command: AppendTurnCommand): Result<Unit> {
        return try {
            val userId = requireUserId()
            val entity = command.turn.toEntity(
                userId = userId,
                language = command.language.code
            )

            Log.d(
                SESSION_MEMORY_TAG,
                "append requested userId=$userId, lang=${command.language.code}, turnId=${command.turn.turnId}, role=${command.turn.role}, textLength=${command.turn.text.length}"
            )

            val appendResult = localDataSource.appendTurn(
                turn = entity,
                maxRecentTurns = maxRecentTurns
            )

            Log.d(
                SESSION_MEMORY_TAG,
                "append local result inserted=${appendResult.inserted}, lang=${command.language.code}, turnId=${command.turn.turnId}, role=${command.turn.role}"
            )

            if (appendResult.inserted) {
                CoroutineScope(Dispatchers.IO).launch {
                    syncPendingTurns(command.language)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>> {
        return authRepository.currentUserUid.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                emptyFlow()
            } else {
                localDataSource.observeTurns(userId, language.code).map { turns ->
                    turns.map { it.toDomainModel() }
                }
            }
        }
    }

    override suspend fun getSessionMemory(language: LangCode): Result<SessionMemory> {
        return try {
            val userId = requireUserId()
            Result.success(buildSessionMemory(userId, language))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun compressSessionMemory(command: CompressSessionMemoryCommand): Result<Unit> {
        return try {
            val userId = requireUserId()
            val currentMeta = localDataSource.getMetadata(userId, command.language.code)

            val updatedMeta = (currentMeta ?: SessionMetadataEntity(
                id = "${userId}_${command.language.code}",
                userId = userId,
                language = command.language.code,
                recentTopicsJson = "[]",
                topicSummariesJson = "[]",
                topicKeySentencesJson = "[]",
                correctionAvailable = false,
                lastCompressedAt = null,
                updatedAt = command.compressedAt,
                isPendingTurnSync = false,
                isPendingCompressionSync = false
            )).copy(
                recentTopicsJson = JSONArray(command.recentTopics).toString(),
                topicSummariesJson = JSONArray(command.topicSummaries).toString(),
                topicKeySentencesJson = JSONArray(command.topicKeySentences).toString(),
                correctionAvailable = false,
                lastCompressedAt = command.compressedAt,
                updatedAt = command.compressedAt,
                isPendingCompressionSync = true
            )

            localDataSource.compress(updatedMeta)

            val localSnapshot = buildSessionMemory(userId, command.language)
            val syncResult = remoteDataSource.syncSessionMemorySnapshot(localSnapshot)

            if (syncResult.isSuccess) {
                localDataSource.saveMetadata(updatedMeta.copy(isPendingCompressionSync = false))
            }

            syncResult.getOrNull()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCorrectionContext(language: LangCode): Flow<List<SessionTurn>> {
        return observeRecentFullContext(language)
            .map(::buildCorrectionContext)
            .onEach { turns ->
                val userTurnCount = turns.count { it.role == TurnSpeaker.USER }
                val assistantTurnCount = turns.count { it.role == TurnSpeaker.AI }
                Log.d(
                    SESSION_MEMORY_TAG,
                    "correction context emitted lang=${language.code}, turns=${turns.size}, userTurns=$userTurnCount, assistantTurns=$assistantTurnCount, turnIds=${turns.joinToString(separator = ",") { it.turnId }}"
                )
            }
    }

    override fun getFlashcardContext(language: LangCode): Flow<List<SessionTurn>> {
        return observeRecentFullContext(language).map { turns ->
            turns.filter(::isMeaningfulUserTurn)
        }
    }

    override suspend fun syncPendingTurns(language: LangCode): Result<Unit> {
        return try {
            val userId = requireUserId()
            val snapshot = buildSessionMemory(userId, language)
            Log.d(
                SESSION_MEMORY_TAG,
                "sync pending turns requested userId=$userId, lang=${language.code}, recentFullContextSize=${snapshot.recentFullContext.size}, pendingTurnSync=${snapshot.isPendingTurnSync}, pendingCompressionSync=${snapshot.isPendingCompressionSync}"
            )
            val syncResult = remoteDataSource.syncSessionMemorySnapshot(snapshot)

            if (syncResult.isSuccess) {
                val pendingEntities = localDataSource.getPendingTurns(userId, language.code)
                if (pendingEntities.isNotEmpty()) {
                    localDataSource.updateSyncStatus(
                        turnIds = pendingEntities.map { it.turnId },
                        status = RemoteSyncStatus.SYNCED
                    )
                }
            }

            if (syncResult.isSuccess) {
                val currentMeta = localDataSource.getMetadata(userId, language.code)
                if (currentMeta != null) {
                    localDataSource.saveMetadata(
                        currentMeta.copy(
                            isPendingTurnSync = false,
                            isPendingCompressionSync = false
                        )
                    )
                }
            }

            syncResult.onSuccess {
                Log.d(
                    SESSION_MEMORY_TAG,
                    "sync pending turns succeeded userId=$userId, lang=${language.code}"
                )
            }.onFailure { error ->
                Log.w(
                    SESSION_MEMORY_TAG,
                    "sync pending turns failed userId=$userId, lang=${language.code}, reason=${error.message ?: error.javaClass.simpleName}",
                    error
                )
            }

            syncResult
        } catch (e: Exception) {
            Log.w(
                SESSION_MEMORY_TAG,
                "sync pending turns failed before remote sync lang=${language.code}, reason=${e.message ?: e.javaClass.simpleName}",
                e
            )
            Result.failure(e)
        }
    }

    override suspend fun summarizeAndSaveTopics(command: SummarizeTopicsCommand): Result<Unit> {
        return try {
            val userId = requireUserId()
            val turns = localDataSource.getTurns(userId, command.language.code)

            // turn 이 없으면 AI 호출 없이 바로 성공으로 처리한다.
            if (turns.isEmpty()) return Result.success(Unit)

            // sessionId 별로 그룹핑하고, 각 세션의 마지막 turn createdAt 기준 내림차순 정렬 후 최근 5개 선정.
            // session_turns 테이블에 sessionId 별 MAX(createdAt) 쿼리가 없으므로 Kotlin 에서 처리한다.
            val recentSessions = turns
                .groupBy { it.sessionId }
                .entries
                .sortedByDescending { (_, sessionTurns) -> sessionTurns.maxOf { it.createdAt } }
                .take(RECENT_SESSION_COUNT)

            // 각 세션을 대화 텍스트 블록으로 변환한다. 내용이 없는 세션은 제외한다.
            val sessionTexts = recentSessions.mapNotNull { (_, sessionTurns) ->
                val block = sessionTurns
                    .filter { it.text.isNotBlank() && it.text.trim().length > MIN_MEANINGFUL_TURN_LENGTH }
                    .takeLast(MAX_TURNS_PER_SESSION) // 토큰 제한: 세션 당 최대 턴 수 제한
                    .joinToString("\n") { "${it.role}: ${it.text}" }
                block.ifBlank { null }
            }

            if (sessionTexts.isEmpty()) return Result.success(Unit)

            val prompt = buildTopicSummaryPrompt(sessionTexts)
            val jsonResponse = topicSummaryAiClient.generateJson(prompt)
            val summaries = parseTopicSummaries(jsonResponse)

            // 요약이 파싱되지 않으면 기존 topicSummaries 를 덮어쓰지 않는다.
            if (summaries.isEmpty()) return Result.success(Unit)

            val summariesJson = JSONArray(summaries).toString()
            localDataSource.updateTopicSummaries(
                userId = userId,
                language = command.language.code,
                summariesJson = summariesJson,
                updatedAt = command.requestedAt
            )

            Log.d(SESSION_MEMORY_TAG, "topic summaries saved lang=${command.language.code}, count=${summaries.size}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(SESSION_MEMORY_TAG, "summarizeAndSaveTopics failed lang=${command.language.code}, reason=${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 세션 텍스트 목록으로 주제 요약 프롬프트를 만듭니다.
     *
     * @param sessionTexts 세션별 대화 텍스트 블록 목록
     * @return AI 에 전달할 프롬프트 문자열
     */
    private fun buildTopicSummaryPrompt(sessionTexts: List<String>): String {
        val sessionsBlock = sessionTexts.mapIndexed { i, text ->
            "Session ${i + 1}:\n$text"
        }.joinToString("\n\n")

        return """
            Summarize the main topic of each conversation session in 1-2 sentences.
            Return ONLY a JSON object with a "summaries" array — no markdown, no explanation.

            Format: {"summaries": ["summary of session 1", "summary of session 2", ...]}

            Conversations:
            $sessionsBlock
        """.trimIndent()
    }

    /**
     * AI 응답 JSON 을 요약 문자열 목록으로 파싱합니다.
     *
     * @param json `{"summaries": [...]}` 형태의 JSON 문자열
     * @return 요약 목록. 파싱 실패 시 빈 리스트를 반환해 기존 데이터를 보호한다.
     */
    private fun parseTopicSummaries(json: String): List<String> {
        return try {
            val array = JSONObject(json).getJSONArray("summaries")
            List(array.length()) { array.getString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 현재 로그인된 사용자 UID 를 조회합니다.
     *
     * @return 사용자 UID
     */
    private suspend fun requireUserId(): String {
        return authRepository.currentUserUid.firstOrNull()
            ?: throw IllegalStateException("User not authenticated")
    }

    /**
     * correction 흐름에 사용할 최소 문맥 친화적 turn 목록을 구성합니다.
     *
     * @param turns 최근 확정 turn 목록
     * @return 교정 대상 user turn 과 최소 assistant 문맥
     */
    private fun buildCorrectionContext(turns: List<SessionTurn>): List<SessionTurn> {
        val includedTurnIds = linkedSetOf<String>()

        turns.forEachIndexed { index, turn ->
            if (!isMeaningfulUserTurn(turn)) return@forEachIndexed

            val previousTurn = turns.getOrNull(index - 1)
            if (previousTurn?.role == TurnSpeaker.AI && previousTurn.text.isNotBlank()) {
                includedTurnIds += previousTurn.turnId
            }

            includedTurnIds += turn.turnId
        }

        val contextTurns = turns
            .filter { it.turnId in includedTurnIds }
            .sortedBy { it.createdAt }

        Log.d(
            SESSION_MEMORY_TAG,
            "build correction context sourceTurns=${turns.size}, contextTurns=${contextTurns.size}, sourceTurnIds=${turns.joinToString(separator = ",") { it.turnId }}, contextTurnIds=${contextTurns.joinToString(separator = ",") { it.turnId }}"
        )

        return contextTurns
    }

    /**
     * flashcard/correction 후보로 사용할 수 있는 user turn 여부를 판별합니다.
     *
     * @param turn 검사할 turn
     * @return 의미 있는 사용자 발화 여부
     */
    private fun isMeaningfulUserTurn(turn: SessionTurn): Boolean {
        return turn.role == TurnSpeaker.USER &&
            turn.text.isNotBlank() &&
            turn.text.trim().length > minMeaningfulTextLength
    }

    /**
     * JSON 배열 문자열을 문자열 리스트로 변환합니다.
     *
     * @param jsonString JSON 배열 문자열
     * @return 문자열 리스트
     */
    private fun parseJsonArray(jsonString: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonString)
            for (index in 0 until array.length()) {
                list.add(array.getString(index))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }

    /**
     * 도메인 모델을 Room 엔터티로 변환합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return Room 저장용 turn 엔터티
     */
    private fun SessionTurn.toEntity(
        userId: String,
        language: String
    ): SessionTurnEntity {
        return SessionTurnEntity(
            turnId = turnId,
            sessionId = sessionId,
            userId = userId,
            language = language,
            text = text,
            role = role.name,
            createdAt = createdAt,
            durationMs = durationMs,
            tokenCount = tokenCount,
            confidence = confidence,
            syncStatus = RemoteSyncStatus.PENDING
        )
    }

    /**
     * Room 엔터티를 도메인 모델로 변환합니다.
     *
     * @return SessionTurn 도메인 모델
     */
    private fun SessionTurnEntity.toDomainModel(): SessionTurn {
        return SessionTurn(
            turnId = turnId,
            sessionId = sessionId,
            text = text,
            role = TurnSpeaker.valueOf(role),
            createdAt = createdAt,
            durationMs = durationMs,
            tokenCount = tokenCount,
            confidence = confidence
        )
    }

    /**
     * 특정 언어의 현재 Session Memory 스냅샷을 조립합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어
     * @return 현재 Session Memory
     */
    private suspend fun buildSessionMemory(
        userId: String,
        language: LangCode
    ): SessionMemory {
        val turns = localDataSource.getTurns(userId, language.code).map { it.toDomainModel() }
        val meta = localDataSource.getMetadata(userId, language.code)

        return if (meta != null) {
            SessionMemory(
                userId = userId,
                language = language,
                recentFullContext = turns,
                recentTopics = parseJsonArray(meta.recentTopicsJson),
                topicSummaries = parseJsonArray(meta.topicSummariesJson),
                topicKeySentences = parseJsonArray(meta.topicKeySentencesJson),
                correctionAvailable = meta.correctionAvailable,
                lastCompressedAt = meta.lastCompressedAt,
                updatedAt = meta.updatedAt,
                isPendingTurnSync = meta.isPendingTurnSync,
                isPendingCompressionSync = meta.isPendingCompressionSync
            )
        } else {
            SessionMemory(
                userId = userId,
                language = language,
                recentFullContext = turns,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private companion object {
        const val SESSION_MEMORY_TAG = "SessionMemoryFlow"

        /** 요약할 최근 세션 최대 개수. (#162-C) */
        const val RECENT_SESSION_COUNT = 5

        /** 세션 당 프롬프트에 포함할 최대 turn 수 (토큰 제한). (#162-C) */
        const val MAX_TURNS_PER_SESSION = 20

        /** 의미 있는 발화로 판단할 최소 텍스트 길이 (문자 수). (#162-C) */
        const val MIN_MEANINGFUL_TURN_LENGTH = 2
    }
}
