package com.app.umma.domain.model.statistics

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsModelsTest {

    @Test
    fun `history converts to five metric points in MVP order`() {
        val history = StatisticsHistory(
            id = "stats-1",
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

        val points = history.toMetricPoints()

        // MVP chart 는 하나의 history 를 5개 지표 점으로 펼치는 계약을 유지해야 한다.
        assertEquals(5, points.size)
        assertEquals(StatisticsMetricType.VocabularyLevel, points[0].metricType)
        assertEquals(4.0, points[0].value, 0.0)
        assertEquals("B2", points[0].displayValue)
        assertEquals(StatisticsMetricType.GrammarAccuracy, points[1].metricType)
        assertEquals(81.0, points[1].value, 0.0)
        assertEquals("81%", points[1].displayValue)
        assertEquals(StatisticsMetricType.ExpressionRange, points[2].metricType)
        assertEquals(7.0, points[2].value, 0.0)
        assertEquals("7", points[2].displayValue)
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
