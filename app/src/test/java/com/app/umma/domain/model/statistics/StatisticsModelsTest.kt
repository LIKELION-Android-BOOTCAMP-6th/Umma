package com.app.umma.domain.model.statistics

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsModelsTest {

    @Test
    fun `history converts selected metric to chart point`() {
        val history = StatisticsHistory(
            id = "stats-1",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 100L,
            conversationBand = ConversationAbilityBand.BasicConversation,
            vocabularyLevel = VocabLevel.B2,
            grammarAccuracy = 0.81,
            expressionRange = 7,
            fluencyScore = 0.66,
            naturalnessScore = 0.72,
            sourceEventId = "event-1",
            syncStatus = SyncStatus.SYNCED
        )

        val bandPoint = history.toMetricPointOrNull(StatisticsMetricType.ConversationBand)
        val grammarPoint = history.toMetricPointOrNull(StatisticsMetricType.GrammarAccuracy)

        // chart 조회 UseCase는 선택된 metric 하나만 변환한다.
        // 전체 metric 목록을 한 번에 만드는 이전 helper는 실제 호출 경로가 없어 제거했다.
        assertEquals(StatisticsMetricType.ConversationBand, bandPoint?.metricType)
        assertEquals(4.0, bandPoint?.value ?: -1.0, 0.0)
        assertEquals("대화 Level", bandPoint?.displayValue)
        assertEquals(StatisticsMetricType.GrammarAccuracy, grammarPoint?.metricType)
        assertEquals(81.0, grammarPoint?.value ?: -1.0, 0.0)
        assertEquals("81%", grammarPoint?.displayValue)
    }

    @Test
    fun `history without conversation band returns null for band point`() {
        val history = StatisticsHistory(
            id = "legacy-stats-1",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 100L,
            vocabularyLevel = VocabLevel.B2,
            grammarAccuracy = 0.81,
            expressionRange = 7,
            fluencyScore = 0.66,
            naturalnessScore = 0.72,
            sourceEventId = "event-1",
            syncStatus = SyncStatus.SYNCED
        )

        val bandPoint = history.toMetricPointOrNull(StatisticsMetricType.ConversationBand)
        val grammarPoint = history.toMetricPointOrNull(StatisticsMetricType.GrammarAccuracy)

        // 이전 저장 데이터에는 conversationBand가 없으므로 band chart point는 만들지 않는다.
        // 다른 기존 지표는 같은 row에서 계속 변환되어 history 호환성을 유지한다.
        assertEquals(null, bandPoint)
        assertEquals(StatisticsMetricType.GrammarAccuracy, grammarPoint?.metricType)
        assertEquals("81%", grammarPoint?.displayValue)
    }

    @Test
    fun `metric type lookup uses metric key`() {
        // 백로그/화면/저장 계층에서 metric key 가 같아야 same contract 로 해석된다.
        assertEquals(
            StatisticsMetricType.GrammarAccuracy,
            StatisticsMetricType.fromMetricKey("grammarAccuracy")
        )
    }
}
