package com.example.umma.domain.usecase.statistics

import com.example.umma.data.repository.fake.FakeStatisticsRepository
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LearningStateUpdateResult
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordStatisticsHistoryUseCaseTest {

    private val repository = FakeStatisticsRepository().apply {
        seedHistories(emptyList())
    }
    private val useCase = RecordStatisticsHistoryUseCase(
        buildStatisticsHistoryUseCase = BuildStatisticsHistoryUseCase(),
        statisticsRepository = repository
    )

    @Test
    fun `records history once and keeps duplicate idempotent`() = runBlocking {
        val updateResult = updateResult()

        // 같은 완료 결과를 두 번 넣어도 history row 는 1개만 남아야 한다.
        // 이 테스트는 record 정책이 중복 생성 대신 덮어쓰기/idempotent 동작을 하는지 본다.
        val first = useCase("user-1", updateResult).getOrThrow()
        val second = useCase("user-1", updateResult).getOrThrow()

        // Save/usecase 경계가 history id 를 기준으로 중복 생성 없이 재기록되는지만 본다.
        assertEquals("user-1_en_analysis-123", first.historyId)
        assertTrue(first.applied)
        assertEquals(false, second.applied)

        val state = repository.observeHistory("user-1", LangCode.EN).first()
        val content = state as StatisticsHistoryState.Content
        assertEquals(1, content.histories.size)
    }

    private fun updateResult(): LearningStateUpdateResult {
        val savedState = LangState.initial(LangCode.EN).copy(
            external = LangState.initial(LangCode.EN).external.copy(
                vocabularyLevel = VocabLevel.B2,
                grammarAccuracy = 0.73,
                expressionRange = 6,
                fluencyScore = 0.81,
                naturalnessScore = 0.68
            )
        )
        return LearningStateUpdateResult(
            lang = LangCode.EN,
            savedState = savedState,
            sourceEventId = "analysis-123",
            applied = true,
            updatedAt = 1_700_000_000_000L
        )
    }
}
