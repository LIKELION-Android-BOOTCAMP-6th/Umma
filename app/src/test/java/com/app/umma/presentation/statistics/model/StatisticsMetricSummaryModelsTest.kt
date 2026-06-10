package com.app.umma.presentation.statistics.model

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.AbilityReadinessState
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangAbilityStats
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.statistics.StatisticsOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsMetricSummaryModelsTest {

    @Test
    fun `maps six metric cards from current lang ability stats`() {
        val overview = overview(
            stats = measuredStats()
        )

        val items = overview.toMetricSummaryItems()

        // STAT-002의 핵심은 6개 지표가 순서대로 카드로 준비되는지다.
        assertEquals(6, items.size)
        assertEquals("종합 레벨", items[0].title)
        assertEquals("대화 Level", items[0].valueText)
        assertTrue(items[0].isAvailable)
        assertEquals("말하기", items[1].title)
        assertEquals("81%", items[1].valueText)
        assertEquals("문법", items[2].title)
        assertEquals("73%", items[2].valueText)
        assertEquals("이해력", items[3].title)
        assertEquals("68%", items[3].valueText)
        assertEquals("어휘", items[4].title)
        assertEquals("측정 준비 중", items[4].valueText)
        assertEquals("표현력", items[5].title)
        assertEquals("측정 준비 중", items[5].valueText)
    }

    @Test
    fun `maps initial lang ability stats to measuring states`() {
        val overview = overview(stats = LangAbilityStats.initial())

        val items = overview.toMetricSummaryItems()

        // 초기 상태는 아직 evidence가 없으므로 카드가 측정 준비/측정 중 상태로 내려가야 한다.
        assertEquals(6, items.size)
        assertEquals("측정 준비 중", items[0].valueText)
        assertEquals("측정 중", items[1].valueText)
        assertEquals("측정 중", items[2].valueText)
        assertEquals("측정 중", items[3].valueText)
        assertEquals("측정 준비 중", items[4].valueText)
        assertEquals("측정 준비 중", items[5].valueText)
        assertTrue(items.all { !it.isAvailable })
    }

    private fun overview(stats: LangAbilityStats): StatisticsOverview {
        val lang = LangCode.EN
        return StatisticsOverview(
            userId = "user-1",
            selectedLearningLanguage = lang,
            currentLangState = LangState.initial(lang),
            currentExternalMetrics = com.app.umma.domain.model.learningstate.ExternalMetrics.initial(),
            currentLangAbilityStats = stats,
            availableMetricTypes = com.app.umma.domain.model.statistics.StatisticsMetricType.entries,
            historyQueryState = com.app.umma.domain.model.statistics.StatisticsHistoryQueryState.Ready(
                userId = "user-1",
                language = lang
            )
        )
    }

    private fun measuredStats(): LangAbilityStats {
        return LangAbilityStats(
            conversation = com.app.umma.domain.model.learningstate.ConversationBandStats(
                currentBand = com.app.umma.domain.model.learningstate.ConversationAbilityBand.BasicConversation,
                confidence = ProfileConfidence.Medium,
                observedCount = 3,
                lastObservedAt = 1_000L
            ),
            speaking = com.app.umma.domain.model.learningstate.SpeakingFlowStats(
                score = 0.81,
                confidence = ProfileConfidence.Medium,
                observedCount = 3,
                lastObservedAt = 1_000L
            ),
            grammar = com.app.umma.domain.model.learningstate.GrammarAbilityStats(
                score = 0.73,
                weightedErrorDensity = 0.27,
                confidence = ProfileConfidence.Medium,
                observedCount = 3,
                lastObservedAt = 1_000L
            ),
            comprehension = com.app.umma.domain.model.learningstate.ComprehensionStats(
                score = 0.68,
                confidence = ProfileConfidence.Medium,
                observedCount = 3,
                lastObservedAt = 1_000L
            ),
            vocabulary = AbilityReadinessState.Preparing,
            expression = AbilityReadinessState.Preparing
        )
    }
}
