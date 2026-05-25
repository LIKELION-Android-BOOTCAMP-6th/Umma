package com.example.umma.domain.usecase.statistics

import com.example.umma.data.repository.fake.FakeStatisticsRepository
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryQueryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshStatisticsHistoryUseCaseTest {

    @Test
    fun `refresh history writes remote snapshot into local cache`() = runBlocking {
        // refresh는 Firestore 최신본을 local observe 경로로 다시 흘려보내야 한다.
        val repository = FakeStatisticsRepository().apply {
            seedHistories(emptyList())
            seedRefreshHistories(
                listOf(
                    StatisticsHistory(
                        id = "remote-refresh-1",
                        userId = "user-1",
                        language = LangCode.EN,
                        recordedAt = 13_000L,
                        vocabularyLevel = VocabLevel.C2,
                        grammarAccuracy = 0.97,
                        expressionRange = 11,
                        fluencyScore = 0.94,
                        naturalnessScore = 0.92,
                        sourceEventId = "refresh-source-1",
                        syncStatus = SyncStatus.PENDING
                    )
                )
            )
        }
        val useCase = RefreshStatisticsHistoryUseCase(repository)

        val result = useCase(
            StatisticsHistoryQueryState.Ready(
                userId = "user-1",
                language = LangCode.EN
            )
        )

        assertTrue(result.isSuccess)
        val state = repository.observeHistory("user-1", LangCode.EN).first()
        val content = state as com.example.umma.domain.model.statistics.StatisticsHistoryState.Content
        assertEquals("remote-refresh-1", content.histories.first().id)
        assertEquals(SyncStatus.SYNCED, content.histories.first().syncStatus)
    }

    @Test
    fun `refresh history fails when query state is unavailable`() = runBlocking {
        val useCase = RefreshStatisticsHistoryUseCase(FakeStatisticsRepository())

        val result = useCase(
            StatisticsHistoryQueryState.Unavailable("missing context")
        )

        assertTrue(result.isFailure)
    }
}
