package com.app.umma.data.repository.fake

import com.app.umma.data.repository.fake.demo.correction.CorrectionDemoPreset
import com.app.umma.data.repository.fake.demo.correction.CorrectionDemoPresetConfig
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.model.realtime.TopicSummarySaveResult
import com.app.umma.domain.repository.SessionMemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSessionMemoryRepository @Inject constructor() : SessionMemoryRepository {

    private val memories = MutableStateFlow(seedMemories(CorrectionDemoPresetConfig.activePreset))

    override suspend fun appendTurn(command: AppendTurnCommand): Result<Unit> {
        val current = memories.value
        val previous = current[command.language] ?: memoryFor(command.language, emptyList())
        memories.value = current + (
            command.language to previous.copy(
                recentFullContext = previous.recentFullContext + command.turn,
                correctionAvailable = command.turn.role == TurnSpeaker.USER,
                updatedAt = command.turn.createdAt,
                isPendingTurnSync = true
            )
        )
        return Result.success(Unit)
    }

    override fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>> {
        return memories.map { it[language]?.recentFullContext.orEmpty() }
    }

    override suspend fun getSessionMemory(language: LangCode): Result<SessionMemory> {
        return Result.success(memories.value[language] ?: memoryFor(language, sampleTurns(language)))
    }

    override suspend fun compressSessionMemory(command: CompressSessionMemoryCommand): Result<Unit> {
        val current = memories.value
        val previous = current[command.language] ?: memoryFor(command.language, sampleTurns(command.language))
        memories.value = current + (
            command.language to previous.copy(
                recentFullContext = emptyList(),
                recentTopics = command.recentTopics,
                topicSummaries = command.topicSummaries,
                topicKeySentences = command.topicKeySentences,
                correctionAvailable = false,
                lastCompressedAt = command.compressedAt,
                updatedAt = command.compressedAt,
                isPendingCompressionSync = false
            )
        )
        return Result.success(Unit)
    }

    override fun getCorrectionContext(language: LangCode): Flow<List<SessionTurn>> {
        return memories.map { it[language]?.recentFullContext.orEmpty() }
    }

    override fun getFlashcardContext(language: LangCode): Flow<List<SessionTurn>> {
        return memories.map { it[language]?.recentFullContext.orEmpty() }
    }

    override suspend fun syncPendingTurns(language: LangCode): Result<Unit> {
        val current = memories.value
        val previous = current[language] ?: return Result.success(Unit)
        memories.value = current + (language to previous.copy(isPendingTurnSync = false))
        return Result.success(Unit)
    }

    override suspend fun summarizeAndSaveTopics(
        command: SummarizeTopicsCommand
    ): Result<TopicSummarySaveResult> {
        return when (CorrectionDemoPresetConfig.activePreset) {
            CorrectionDemoPreset.TopicTitleEmpty -> Result.success(
                TopicSummarySaveResult(applied = false, displayTitle = null)
            )

            else -> Result.success(
                TopicSummarySaveResult(applied = true, displayTitle = "여행 계획")
            )
        }
    }

    private fun seedMemories(preset: CorrectionDemoPreset): Map<LangCode, SessionMemory> {
        val turns = when (preset) {
            CorrectionDemoPreset.EmptyInitial -> emptyList()
            else -> sampleTurns(LangCode.EN)
        }
        return mapOf(LangCode.EN to memoryFor(LangCode.EN, turns))
    }

    private fun memoryFor(
        language: LangCode,
        turns: List<SessionTurn>
    ): SessionMemory {
        return SessionMemory(
            userId = "uid-fixture",
            language = language,
            recentFullContext = turns,
            recentTopics = listOf("travel", "plan"),
            topicSummaries = listOf("여행 계획에 대해 말하는 연습"),
            topicKeySentences = listOf("I want to visit Seoul next weekend."),
            correctionAvailable = turns.any { it.role == TurnSpeaker.USER && it.text.isNotBlank() },
            updatedAt = UPDATED_AT
        )
    }

    private fun sampleTurns(language: LangCode): List<SessionTurn> {
        val suffix = language.code
        return listOf(
            SessionTurn(
                turnId = "demo-$suffix-user-1",
                sessionId = "demo-$suffix-session",
                text = "this is test",
                role = TurnSpeaker.USER,
                createdAt = UPDATED_AT,
                durationMs = 2_000,
                tokenCount = 3,
                confidence = 0.95
            ),
            SessionTurn(
                turnId = "demo-$suffix-ai-1",
                sessionId = "demo-$suffix-session",
                text = "You can say, This is a test.",
                role = TurnSpeaker.AI,
                createdAt = UPDATED_AT + 1_000,
                durationMs = 2_500,
                tokenCount = 8,
                confidence = null
            )
        )
    }

    private companion object {
        private const val UPDATED_AT: Long = 1_700_000_000_000L
    }
}
