package com.example.umma.presentation.statistics.model

import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.VocabLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsSyncStateTest {

    @Test
    fun `content with pending history resolves to pending sync state`() {
        // pending 상태는 local-first 저장은 되었지만 Firestore mirror가 아직 남아있다는 뜻이다.
        val historyState = StatisticsHistoryState.Content(
            listOf(
                StatisticsHistory(
                    id = "pending-1",
                    userId = "user-1",
                    language = LangCode.EN,
                    recordedAt = 1_000L,
                    vocabularyLevel = VocabLevel.B2,
                    grammarAccuracy = 0.8,
                    expressionRange = 6,
                    fluencyScore = 0.75,
                    naturalnessScore = 0.72,
                    sourceEventId = "event-1",
                    syncStatus = SyncStatus.PENDING
                )
            )
        )

        assertTrue(resolveStatisticsSyncState(historyState) is StatisticsSyncState.Pending)
    }

    @Test
    fun `content with synced history resolves to idle sync state`() {
        // 모든 row가 synced 이면 보조 상태는 숨겨도 된다.
        val historyState = StatisticsHistoryState.Content(
            listOf(
                StatisticsHistory(
                    id = "synced-1",
                    userId = "user-1",
                    language = LangCode.EN,
                    recordedAt = 1_000L,
                    vocabularyLevel = VocabLevel.B2,
                    grammarAccuracy = 0.8,
                    expressionRange = 6,
                    fluencyScore = 0.75,
                    naturalnessScore = 0.72,
                    sourceEventId = "event-1",
                    syncStatus = SyncStatus.SYNCED
                )
            )
        )

        assertTrue(resolveStatisticsSyncState(historyState) is StatisticsSyncState.Idle)
    }

    @Test
    fun `retry state resolves to error sync state`() {
        // 조회 실패는 retry 가능한 에러로 노출한다.
        val resolved = resolveStatisticsSyncState(
            StatisticsHistoryState.Retry(IllegalStateException("boom"))
        )

        assertTrue(resolved is StatisticsSyncState.Error)
    }
}
