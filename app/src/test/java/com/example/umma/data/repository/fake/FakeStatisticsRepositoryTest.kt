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
        // mockDebug에서 쓰는 fake도 실제 repository 계약처럼 userId + language 경계를 지켜야 한다.
        val repository = FakeStatisticsRepository()

        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // Statistics 화면은 다른 사용자/다른 언어의 history 를 섞어 보여주면 안 된다.
        val content = state as StatisticsHistoryState.Content
        assertEquals(10, content.histories.size)
        assertTrue(content.histories.all { it.userId == "user-1" })
        assertTrue(content.histories.all { it.language == LangCode.EN })
    }

    @Test
    fun `fake repository falls back to language demo data in debug`() = runBlocking {
        // 실제 Firebase uid가 샘플 uid와 달라도 mockDebug 화면 검증은 막히지 않아야 한다.
        val repository = FakeStatisticsRepository()

        val state = repository.observeHistory("unknown-user", LangCode.EN).first()

        // Debug 실행에서는 실제 Firebase uid가 달라도 Statistics 차트를 먼저 볼 수 있도록
        // language 기준 demo data를 한 번 더 제공한다.
        assertTrue(state is StatisticsHistoryState.Content)
        val content = state as StatisticsHistoryState.Content
        assertTrue(content.histories.all { it.language == LangCode.EN })
    }

    @Test
    fun `fake repository ships chart friendly seed data for multiple languages`() = runBlocking {
        // Build Variant로 mockDebug를 실행했을 때 언어를 바꿔도 차트 상태를 바로 확인하기 위한 seed 검증이다.
        val repository = FakeStatisticsRepository()

        val english = repository.observeHistory("user-1", LangCode.EN).first()
        val korean = repository.observeHistory("user-1", LangCode.KO).first()
        val japanese = repository.observeHistory("user-1", LangCode.JA).first()
        val spanish = repository.observeHistory("user-1", LangCode.ES).first()

        // 실행 화면에서 language 를 바꿔도 chart dialog 를 바로 확인할 수 있도록,
        // 최소한 여러 언어에 대한 seed history 를 함께 제공한다.
        assertTrue(english is StatisticsHistoryState.Content)
        assertTrue(korean is StatisticsHistoryState.Content)
        assertTrue(japanese is StatisticsHistoryState.Content)
        assertTrue(spanish is StatisticsHistoryState.Content)
    }

    @Test
    fun `fake repository can emit retry state`() = runBlocking {
        // UI의 ChartError/Retry 분기를 실제 네트워크 장애 없이 재현하기 위한 테스트 훅이다.
        val repository = FakeStatisticsRepository().apply {
            setFetchFailure(IllegalStateException("boom"))
        }

        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // local cache 조회가 실패했을 때 UI 는 Retry 상태를 재현할 수 있어야 한다.
        assertTrue(state is StatisticsHistoryState.Retry)
    }

    @Test
    fun `fake repository can be seeded with short history and pending sync`() = runBlocking {
        // history 부족과 pending sync는 STAT-003/004에서 별도 UI 상태로 다뤄야 하므로 fake에서 재현 가능해야 한다.
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
        // STI-002 기록 계약의 중복 방지 흐름을 fake에서도 맞춰두면 후속 화면 테스트가 안정적이다.
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
