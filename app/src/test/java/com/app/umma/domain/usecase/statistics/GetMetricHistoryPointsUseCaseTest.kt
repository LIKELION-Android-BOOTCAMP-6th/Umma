package com.app.umma.domain.usecase.statistics

import com.app.umma.data.repository.fake.FakeStatisticsRepository
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.app.umma.domain.model.statistics.StatisticsMetricType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetMetricHistoryPointsUseCaseTest {

    @Test
    fun `returns sorted metric points from ready history query`() = runBlocking {
        // 입력 순서를 일부러 뒤집어서 UseCase가 chart 입력을 recordedAt 기준으로 정렬하는지 확인한다.
        val repository = FakeStatisticsRepository().apply {
            seedHistories(
                listOf(
                    history(id = "h-2", recordedAt = 2_000L, grammarAccuracy = 0.82),
                    history(id = "h-1", recordedAt = 1_000L, grammarAccuracy = 0.61)
                )
            )
        }
        val useCase = GetMetricHistoryPointsUseCase(repository)

        val result = useCase(
            queryState = StatisticsHistoryQueryState.Ready(
                userId = "user-1",
                language = LangCode.EN
            ),
            metricType = StatisticsMetricType.GrammarAccuracy
        ).getOrThrow()

        assertEquals(2, result.size)
        // 원본 0.0~1.0 grammarAccuracy가 chart 표시용 0~100 값과 라벨로 바뀌어야 한다.
        assertEquals(1_000L, result.first().recordedAt)
        assertEquals(61.0, result.first().value, 0.0)
        assertEquals("61%", result.first().displayValue)
    }

    @Test
    fun `returns empty list when history is missing`() = runBlocking {
        // history 없음은 UseCase 실패가 아니라 presentation의 Empty chart 상태로 이어질 빈 목록이다.
        // fake repository는 debug fallback seed가 있으므로, 이 테스트에서는 저장소를 명시적으로 비운다.
        val repository = FakeStatisticsRepository().apply {
            seedHistories(emptyList())
        }
        val useCase = GetMetricHistoryPointsUseCase(repository)

        val result = useCase(
            queryState = StatisticsHistoryQueryState.Ready(
                userId = "user-unknown",
                language = LangCode.EN
            ),
            metricType = StatisticsMetricType.FluencyScore
        ).getOrThrow()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `fails when query state is unavailable`() = runBlocking {
        // STAT-001이 userId/language를 준비하지 못한 경우 chart 조회는 즉시 실패해야 한다.
        val useCase = GetMetricHistoryPointsUseCase(FakeStatisticsRepository())

        val result = useCase(
            queryState = StatisticsHistoryQueryState.Unavailable("missing context"),
            metricType = StatisticsMetricType.NaturalnessScore
        )

        assertTrue(result.isFailure)
    }

    private fun history(
        id: String,
        recordedAt: Long,
        grammarAccuracy: Double
    ): StatisticsHistory {
        // 테스트 관심사는 grammarAccuracy 변환이므로 나머지 metric은 유효한 기본값으로 고정한다.
        return StatisticsHistory(
            id = id,
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = recordedAt,
            vocabularyLevel = VocabLevel.B2,
            grammarAccuracy = grammarAccuracy,
            expressionRange = 7,
            fluencyScore = 0.74,
            naturalnessScore = 0.71,
            sourceEventId = "source-$id",
            syncStatus = SyncStatus.SYNCED
        )
    }
}
