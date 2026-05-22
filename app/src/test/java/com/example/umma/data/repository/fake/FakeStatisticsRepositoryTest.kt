package com.example.umma.data.repository.fake

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeStatisticsRepositoryTest {

    @Test
    fun `fake repository filters by user and language`() = runBlocking {
        val repository = FakeStatisticsRepository()

        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // Statistics 화면은 다른 사용자/다른 언어의 history 를 섞어 보여주면 안 된다.
        val content = state as StatisticsHistoryState.Content
        assertEquals(2, content.histories.size)
        assertTrue(content.histories.all { it.userId == "user-1" })
        assertTrue(content.histories.all { it.language == LangCode.EN })
    }

    @Test
    fun `fake repository can emit retry state`() = runBlocking {
        val repository = FakeStatisticsRepository().apply {
            setFetchFailure(IllegalStateException("boom"))
        }

        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // local cache 조회가 실패했을 때 UI 는 Retry 상태를 재현할 수 있어야 한다.
        assertTrue(state is StatisticsHistoryState.Retry)
    }

    @Test
    fun `fake repository can be seeded with short history and pending sync`() = runBlocking {
        val repository = FakeStatisticsRepository().apply {
            seedHistories(
                listOf(
                    StatisticsHistory(
                        id = "only-one",
                        userId = "user-1",
                        language = LangCode.KO,
                        recordedAt = 100L,
                        vocabularyLevel = VocabLevel.A1,
                        grammarAccuracy = 0.1,
                        expressionRange = 1,
                        fluencyScore = 0.2,
                        naturalnessScore = 0.3,
                        sourceEventId = "event-x",
                        syncStatus = SyncStatus.PENDING
                    )
                )
            )
        }

        val state = repository.observeHistory("user-1", LangCode.KO).first()
        val content = state as StatisticsHistoryState.Content

        // history 가 적어도 record snapshot 자체는 유지되어야 하고,
        // syncStatus 도 함께 보존되어야 한다.
        assertEquals(1, content.histories.size)
        assertEquals(SyncStatus.PENDING, content.histories.first().syncStatus)
    }

    @Test
    fun `fake repository records history idempotently`() = runBlocking {
        val repository = FakeStatisticsRepository().apply {
            seedHistories(emptyList())
        }
        val history = StatisticsHistory(
            id = "stats-new",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 4_000L,
            vocabularyLevel = VocabLevel.B2,
            grammarAccuracy = 0.8,
            expressionRange = 6,
            fluencyScore = 0.7,
            naturalnessScore = 0.75,
            sourceEventId = "event-new",
            syncStatus = SyncStatus.PENDING
        )

        val first = repository.recordHistory(history).getOrThrow()
        val second = repository.recordHistory(history).getOrThrow()

        // fake repository 는 duplicate insert 를 새 row 로 늘리지 않고 덮어쓰는 경계를 흉내낸다.
        assertEquals("stats-new", first.historyId)
        assertTrue(first.applied)
        assertEquals(false, second.applied)
        val content = repository.observeHistory("user-1", LangCode.EN).first() as StatisticsHistoryState.Content
        assertTrue(content.histories.any { it.id == "stats-new" })
    }
}
