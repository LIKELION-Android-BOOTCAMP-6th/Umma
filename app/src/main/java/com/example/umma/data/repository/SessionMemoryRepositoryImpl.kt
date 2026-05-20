package com.example.umma.data.repository

import com.example.umma.data.source.local.RemoteSyncStatus
import com.example.umma.data.source.local.SessionMemoryLocalDataSource
import com.example.umma.data.source.local.SessionMetadataEntity
import com.example.umma.data.source.local.SessionTurnEntity
import com.example.umma.data.source.remote.SessionMemoryRemoteDataSource
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.model.realtime.AppendTurnCommand
import com.example.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.example.umma.domain.model.realtime.SessionMemory
import com.example.umma.domain.model.realtime.SessionTurn
import com.example.umma.domain.repository.AuthRepository
import com.example.umma.domain.repository.SessionMemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

/**
 * Session Memory 의 local-first 저장과 batch sync 를 담당하는 Repository 구현체입니다.
 */
class SessionMemoryRepositoryImpl @Inject constructor(
    private val localDataSource: SessionMemoryLocalDataSource,
    private val remoteDataSource: SessionMemoryRemoteDataSource,
    private val authRepository: AuthRepository
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

            val appendResult = localDataSource.appendTurn(
                turn = entity,
                maxRecentTurns = maxRecentTurns
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
        return observeRecentFullContext(language).map(::buildCorrectionContext)
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

            val currentMeta = localDataSource.getMetadata(userId, language.code)
            if (currentMeta != null) {
                localDataSource.saveMetadata(
                    currentMeta.copy(
                        isPendingTurnSync = false,
                        isPendingCompressionSync = false
                    )
                )
            }

            syncResult
        } catch (e: Exception) {
            Result.failure(e)
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

        return turns
            .filter { it.turnId in includedTurnIds }
            .sortedBy { it.createdAt }
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

}
