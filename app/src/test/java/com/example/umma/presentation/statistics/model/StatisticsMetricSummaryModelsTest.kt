package com.example.umma.presentation.statistics.model

import com.example.umma.domain.model.learningstate.ExternalMetrics
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.model.statistics.StatisticsOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsMetricSummaryModelsTest {

    @Test
    fun `maps five metric cards from current external metrics`() {
        val overview = overview(
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.B2,
                grammarAccuracy = 0.73,
                expressionRange = 7,
                fluencyScore = 0.81,
                naturalnessScore = 0.68
            )
        )

        val items = overview.toMetricSummaryItems()

        // STAT-002의 핵심은 5개 지표가 모두 카드로 준비되는지다.
        assertEquals(5, items.size)
        assertEquals("어휘 레벨", items[0].title)
        assertEquals("B2", items[0].valueText)
        assertTrue(items[0].isAvailable)
        assertEquals("73%", items[1].valueText)
        assertEquals("7", items[2].valueText)
        assertEquals("81%", items[3].valueText)
        assertEquals("68%", items[4].valueText)
    }

    @Test
    fun `maps initial external metrics to empty values`() {
        val overview = overview(external = ExternalMetrics.initial())

        val items = overview.toMetricSummaryItems()

        // 초기 상태는 아직 보여줄 지표가 없으므로 카드 state가 Empty 로 내려가야 한다.
        assertEquals(5, items.size)
        assertTrue(items.all { !it.isAvailable })
        assertEquals("Empty", items[0].valueText)
        assertEquals("Empty", items[1].valueText)
        assertEquals("Empty", items[2].valueText)
        assertEquals("Empty", items[3].valueText)
        assertEquals("Empty", items[4].valueText)
    }

    private fun overview(external: ExternalMetrics): StatisticsOverview {
        val lang = LangCode.EN
        return StatisticsOverview(
            userId = "user-1",
            selectedLearningLanguage = lang,
            currentLangState = LangState.initial(lang),
            currentExternalMetrics = external,
            availableMetricTypes = com.example.umma.domain.model.statistics.StatisticsMetricType.entries,
            historyQueryState = com.example.umma.domain.model.statistics.StatisticsHistoryQueryState.Ready(
                userId = "user-1",
                language = lang
            )
        )
    }
}
