package com.app.umma.data.repository.fake

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryState
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

        // 기본 seed에는 여러 사용자/여러 언어가 섞여 있으므로 EN + user-1만 조회한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // Statistics 화면은 다른 사용자/다른 언어의 history 를 섞어 보여주면 안 된다.
        val content = state as StatisticsHistoryState.Content
        // EN user-1 seed는 차트 확인에 충분한 10건으로 고정되어 있다.
        assertEquals(10, content.histories.size)
        // userId 필터가 깨지면 다른 계정의 통계가 섞이는 치명적인 문제가 된다.
        assertTrue(content.histories.all { it.userId == "user-1" })
        // language 필터가 깨지면 선택 언어와 다른 차트가 표시된다.
        assertTrue(content.histories.all { it.language == LangCode.EN })
    }

    @Test
    fun `fake repository falls back to language demo data in debug`() = runBlocking {
        // 실제 Firebase uid가 샘플 uid와 달라도 mockDebug 화면 검증은 막히지 않아야 한다.
        val repository = FakeStatisticsRepository()

        // mockDebug 수동 실행에서는 실제 로그인 uid가 seed의 user-1과 다를 수 있다.
        val state = repository.observeHistory("unknown-user", LangCode.EN).first()

        // Debug 실행에서는 실제 Firebase uid가 달라도 Statistics 차트를 먼저 볼 수 있도록
        // language 기준 demo data를 한 번 더 제공한다.
        assertTrue(state is StatisticsHistoryState.Content)
        val content = state as StatisticsHistoryState.Content
        // fallback은 userId 경계 대신 language 데모 데이터를 보여주는 mockDebug 전용 보조 흐름이다.
        assertTrue(content.histories.all { it.language == LangCode.EN })
    }

    @Test
    fun `fake repository can emit retry state`() = runBlocking {
        // UI의 ChartError/Retry 분기를 실제 네트워크 장애 없이 재현하기 위한 테스트 훅이다.
        val repository = FakeStatisticsRepository().apply {
            // queryFailure를 넣으면 observeHistory가 Content 대신 Retry를 즉시 반환한다.
            setFetchFailure(IllegalStateException("boom"))
        }

        // 조회 실패 preset과 같은 repository 실패 hook을 직접 확인한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // local cache 조회가 실패했을 때 UI 는 Retry 상태를 재현할 수 있어야 한다.
        assertTrue(state is StatisticsHistoryState.Retry)
    }

    @Test
    fun `fake repository can refresh remote snapshot into local cache`() = runBlocking {
        // STAT-004의 background refresh는 local cache를 직접 교체하지 않고 보정하는 흐름이므로,
        // fake에서도 remote 최신본이 local observe 결과로 이어지는지 확인해야 한다.
        val repository = FakeStatisticsRepository().apply {
            // local cache를 비워 refresh 전에는 history가 없는 상태를 만든다.
            seedHistories(emptyList())
            // refresh 호출 시 remote snapshot처럼 주입될 row를 준비한다.
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

        // refresh는 remote snapshot을 local cache에 보정하는 역할이다.
        val refreshResult = repository.refreshHistory("user-1", LangCode.EN)
        assertTrue(refreshResult.isSuccess)

        // refresh 이후 새 observe는 local cache에 보정된 최신 snapshot을 보아야 한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()
        assertTrue(state is StatisticsHistoryState.Content)
        val content = state as StatisticsHistoryState.Content
        // 빈 local cache에 remote snapshot 1건이 들어왔는지 확인한다.
        assertEquals(1, content.histories.size)
        // id가 보존되어야 이후 중복/갱신 경계도 같은 row를 따라갈 수 있다.
        assertEquals("refreshed-en-1", content.histories.first().id)
        // remote refresh로 들어온 row는 local pending이 아니라 synced snapshot으로 정리된다.
        assertEquals(SyncStatus.SYNCED, content.histories.first().syncStatus)
    }

    @Test
    fun `fake repository emits updated snapshot to existing observer after refresh`() = runBlocking {
        // STAT-004는 refresh 결과를 UI에 직접 넣지 않고 기존 observe stream으로 다시 받는 구조다.
        // fake도 같은 방식으로 emit해야 ViewModel 테스트와 mockDebug 검증이 실제 Room 흐름과 맞아진다.
        val repository = FakeStatisticsRepository().apply {
            // 첫 emit이 Empty여야 refresh 전/후 두 snapshot 차이를 확인할 수 있다.
            seedHistories(emptyList())
            // refresh 후 두 번째 emit으로 들어올 snapshot이다.
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

        // observe stream을 먼저 잡아둔 뒤 refresh를 호출해야 기존 구독자 갱신을 검증할 수 있다.
        val stream = repository.observeHistory("user-1", LangCode.EN)
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            // 기존 구독자는 refresh 전 상태와 refresh 후 상태를 둘 다 받아야 한다.
            stream.take(2).toList()
        }

        // refresh가 histories StateFlow를 갱신하면 위 stream에 두 번째 값이 emit된다.
        val refreshResult = repository.refreshHistory("user-1", LangCode.EN)
        assertTrue(refreshResult.isSuccess)

        // take(2)로 모은 두 snapshot을 순서대로 확인한다.
        val collectedStates = emissions.await()
        val firstState = collectedStates.first()
        val refreshedState = collectedStates[1]
        // 첫 snapshot은 local cache가 비어 있던 상태다.
        assertTrue(firstState is StatisticsHistoryState.Empty)
        // 두 번째 snapshot은 refreshSeed가 local cache에 반영된 상태다.
        assertTrue(refreshedState is StatisticsHistoryState.Content)
        val content = refreshedState as StatisticsHistoryState.Content
        assertEquals("stream-refresh-en", content.histories.first().id)
        assertEquals(SyncStatus.SYNCED, content.histories.first().syncStatus)
    }

    @Test
    fun `fake repository preserves local cache when refresh fails`() = runBlocking {
        // remote refresh 실패는 local history를 지우는 실패가 아니어야 한다.
        val repository = FakeStatisticsRepository().apply {
            // 실패 후에도 유지되어야 할 local pending row를 먼저 넣는다.
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
            // refresh 호출만 실패시키고 observeHistory는 기존 local cache를 계속 보게 한다.
            setRefreshFailure(IllegalStateException("refresh failed"))
        }

        // background refresh는 실패한다.
        val refreshResult = repository.refreshHistory("user-1", LangCode.KO)
        assertTrue(refreshResult.isFailure)

        // 실패가 나도 기존 local pending row는 그대로 남아 있어야 한다.
        val state = repository.observeHistory("user-1", LangCode.KO).first()
        assertTrue(state is StatisticsHistoryState.Content)
        val content = state as StatisticsHistoryState.Content
        // 실패로 인해 row가 사라지거나 duplicate 되면 안 된다.
        assertEquals(1, content.histories.size)
        // pending 상태가 유지되어 다음 retry 대상이 된다.
        assertEquals(SyncStatus.PENDING, content.histories.first().syncStatus)
    }

    @Test
    fun `fake repository records history idempotently`() = runBlocking {
        // STI-002 기록 계약의 중복 방지 흐름을 fake에서도 맞춰두면 후속 화면 테스트가 안정적이다.
        val repository = FakeStatisticsRepository().apply {
            // 새 record의 applied 여부를 명확히 보기 위해 기존 seed를 비운다.
            seedHistories(emptyList())
        }
        // 같은 id/sourceEventId로 두 번 기록해 idempotent 경계를 확인한다.
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

        // 첫 기록은 새 row이므로 applied=true여야 한다.
        val first = repository.recordHistory(history).getOrThrow()
        // 같은 history를 다시 기록하면 새 row 추가가 아니라 기존 row 갱신이 되어야 한다.
        val second = repository.recordHistory(history).getOrThrow()

        // fake repository 는 duplicate insert 를 새 row 로 늘리지 않고 덮어쓰는 경계를 흉내낸다.
        assertEquals("stats-new", first.historyId)
        assertTrue(first.applied)
        assertEquals(false, second.applied)
        // observe 결과에도 같은 id의 row가 남아 있어야 기록 결과가 읽기 경로로 이어진다.
        val content = repository.observeHistory("user-1", LangCode.EN).first() as StatisticsHistoryState.Content
        assertTrue(content.histories.any { it.id == "stats-new" })
    }

    @Test
    fun `fake repository syncs only pending histories for requested user`() = runBlocking {
        // STAT-004의 pending retry는 사용자 단위로 동작하되, 다른 사용자의 pending은 건드리면 안 된다.
        val repository = FakeStatisticsRepository().apply {
            // user-1 pending, user-1 synced, user-2 pending을 섞어 사용자 경계를 만든다.
            seedHistories(
                listOf(
                    testHistory(id = "user-1-pending", userId = "user-1", syncStatus = SyncStatus.PENDING),
                    testHistory(id = "user-1-synced", userId = "user-1", syncStatus = SyncStatus.SYNCED),
                    testHistory(id = "user-2-pending", userId = "user-2", syncStatus = SyncStatus.PENDING)
                )
            )
        }

        // user-1 기준으로만 pending sync를 요청한다.
        val syncedCount = repository.syncPendingHistories("user-1").getOrThrow()

        // user-1의 EN history는 pending이 synced로 바뀌어야 한다.
        val userOneEnglish = repository.observeHistory("user-1", LangCode.EN).first() as StatisticsHistoryState.Content
        // user-2의 JA history는 다른 사용자이므로 pending으로 남아야 한다.
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
            // syncPendingHistories만 실패시키고 기존 local seed는 그대로 둔다.
            setPendingSyncFailure(IllegalStateException("network down"))
        }

        // pending retry 실패를 발생시킨다.
        val result = repository.syncPendingHistories("user-1")
        // 실패 후에도 observe는 local history를 그대로 내려야 한다.
        val content = repository.observeHistory("user-1", LangCode.EN).first() as StatisticsHistoryState.Content

        // 실패 결과는 caller가 non-blocking으로 처리하고, local row는 아직 PENDING으로 남긴다.
        assertTrue(result.isFailure)
        // pending row가 사라지면 다음 재시도 대상이 없어지므로 반드시 남아야 한다.
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
            // user-1과 user-2를 다른 언어로 나누어 observeHistory 필터도 함께 검증한다.
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
