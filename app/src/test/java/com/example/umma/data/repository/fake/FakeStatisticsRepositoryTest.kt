package com.example.umma.data.repository.fake

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
    fun `fake repository can refresh remote snapshot into local cache`() = runBlocking {
        // STAT-004의 background refresh는 local cache를 직접 교체하지 않고 보정하는 흐름이므로,
        // fake에서도 remote 최신본이 local observe 결과로 이어지는지 확인해야 한다.
        val repository = FakeStatisticsRepository().apply {
            seedHistories(emptyList())
            seedRefreshHistories(
                listOf(
                    StatisticsHistory(
                        id = "refreshed-en-1",
                        userId = "user-1",
                        language = LangCode.EN,
                        recordedAt = 11_000L,
                        vocabularyLevel = VocabLevel.C2,
                        grammarAccuracy = 0.95,
                        expressionRange = 10,
                        fluencyScore = 0.93,
                        naturalnessScore = 0.91,
                        sourceEventId = "refresh-event-en-1",
                        syncStatus = SyncStatus.PENDING
                    )
                )
            )
        }

        val refreshResult = repository.refreshHistory("user-1", LangCode.EN)
        assertTrue(refreshResult.isSuccess)

        // refresh 이후 새 observe는 local cache에 보정된 최신 snapshot을 보아야 한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()
        assertTrue(state is StatisticsHistoryState.Content)
        val content = state as StatisticsHistoryState.Content
        assertEquals(1, content.histories.size)
        assertEquals("refreshed-en-1", content.histories.first().id)
        assertEquals(SyncStatus.SYNCED, content.histories.first().syncStatus)
    }

    @Test
    fun `fake repository emits updated snapshot to existing observer after refresh`() = runBlocking {
        // STAT-004는 refresh 결과를 UI에 직접 넣지 않고 기존 observe stream으로 다시 받는 구조다.
        // fake도 같은 방식으로 emit해야 ViewModel 테스트와 mockDebug 검증이 실제 Room 흐름과 맞아진다.
        val repository = FakeStatisticsRepository().apply {
            seedHistories(emptyList())
            seedRefreshHistories(
                listOf(
                    StatisticsHistory(
                        id = "stream-refresh-en",
                        userId = "user-1",
                        language = LangCode.EN,
                        recordedAt = 13_000L,
                        vocabularyLevel = VocabLevel.C1,
                        grammarAccuracy = 0.9,
                        expressionRange = 9,
                        fluencyScore = 0.86,
                        naturalnessScore = 0.84,
                        sourceEventId = "stream-refresh-event-en",
                        syncStatus = SyncStatus.PENDING
                    )
                )
            )
        }

        val stream = repository.observeHistory("user-1", LangCode.EN)
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            // 기존 구독자는 refresh 전 상태와 refresh 후 상태를 둘 다 받아야 한다.
            stream.take(2).toList()
        }

        val refreshResult = repository.refreshHistory("user-1", LangCode.EN)
        assertTrue(refreshResult.isSuccess)

        // take(2)로 모은 두 snapshot을 순서대로 확인한다.
        val collectedStates = emissions.await()
        val firstState = collectedStates.first()
        val refreshedState = collectedStates[1]
        assertTrue(firstState is StatisticsHistoryState.Empty)
        assertTrue(refreshedState is StatisticsHistoryState.Content)
        val content = refreshedState as StatisticsHistoryState.Content
        assertEquals("stream-refresh-en", content.histories.first().id)
        assertEquals(SyncStatus.SYNCED, content.histories.first().syncStatus)
    }

    @Test
    fun `fake repository preserves local cache when refresh fails`() = runBlocking {
        // remote refresh 실패는 local history를 지우는 실패가 아니어야 한다.
        val repository = FakeStatisticsRepository().apply {
            seedHistories(
                listOf(
                    StatisticsHistory(
                        id = "local-pending",
                        userId = "user-1",
                        language = LangCode.KO,
                        recordedAt = 12_000L,
                        vocabularyLevel = VocabLevel.B2,
                        grammarAccuracy = 0.84,
                        expressionRange = 7,
                        fluencyScore = 0.8,
                        naturalnessScore = 0.79,
                        sourceEventId = "local-event-ko",
                        syncStatus = SyncStatus.PENDING
                    )
                )
            )
            setRefreshFailure(IllegalStateException("refresh failed"))
        }

        val refreshResult = repository.refreshHistory("user-1", LangCode.KO)
        assertTrue(refreshResult.isFailure)

        // 실패가 나도 기존 local pending row는 그대로 남아 있어야 한다.
        val state = repository.observeHistory("user-1", LangCode.KO).first()
        assertTrue(state is StatisticsHistoryState.Content)
        val content = state as StatisticsHistoryState.Content
        assertEquals(1, content.histories.size)
        assertEquals(SyncStatus.PENDING, content.histories.first().syncStatus)
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

    @Test
    fun `fake repository syncs only pending histories for requested user`() = runBlocking {
        // STAT-004의 pending retry는 사용자 단위로 동작하되, 다른 사용자의 pending은 건드리면 안 된다.
        val repository = FakeStatisticsRepository().apply {
            seedHistories(
                listOf(
                    testHistory(id = "user-1-pending", userId = "user-1", syncStatus = SyncStatus.PENDING),
                    testHistory(id = "user-1-synced", userId = "user-1", syncStatus = SyncStatus.SYNCED),
                    testHistory(id = "user-2-pending", userId = "user-2", syncStatus = SyncStatus.PENDING)
                )
            )
        }

        val syncedCount = repository.syncPendingHistories("user-1").getOrThrow()

        val userOneEnglish = repository.observeHistory("user-1", LangCode.EN).first() as StatisticsHistoryState.Content
        val userTwoJapanese = repository.observeHistory("user-2", LangCode.JA).first() as StatisticsHistoryState.Content

        // user-1의 pending row만 SYNCED로 정리되고,
        // user-2의 pending row는 사용자 경계 때문에 그대로 유지된다.
        assertEquals(1, syncedCount)
        assertTrue(userOneEnglish.histories.all { it.syncStatus == SyncStatus.SYNCED })
        assertTrue(userTwoJapanese.histories.all { it.syncStatus == SyncStatus.PENDING })
    }

    @Test
    fun `fake repository leaves pending histories when retry fails`() = runBlocking {
        // Firestore 재전송 실패를 재현하면 local PENDING 상태가 유지되어 다음 retry 대상이 되어야 한다.
        val repository = FakeStatisticsRepository().apply {
            setPendingSyncFailure(IllegalStateException("network down"))
        }

        val result = repository.syncPendingHistories("user-1")
        val content = repository.observeHistory("user-1", LangCode.EN).first() as StatisticsHistoryState.Content

        // 실패 결과는 caller가 non-blocking으로 처리하고, local row는 아직 PENDING으로 남긴다.
        assertTrue(result.isFailure)
        assertTrue(content.histories.any { it.syncStatus == SyncStatus.PENDING })
    }

    private fun testHistory(
        id: String,
        userId: String,
        syncStatus: SyncStatus
    ): StatisticsHistory {
        // pending retry 테스트는 syncStatus와 userId 경계만 보면 되므로 나머지 지표는 고정값으로 둔다.
        return StatisticsHistory(
            id = id,
            userId = userId,
            language = if (userId == "user-1") LangCode.EN else LangCode.JA,
            recordedAt = 1_000L,
            vocabularyLevel = VocabLevel.A1,
            grammarAccuracy = 0.5,
            expressionRange = 3,
            fluencyScore = 0.5,
            naturalnessScore = 0.5,
            sourceEventId = "event-$id",
            syncStatus = syncStatus
        )
    }
}
