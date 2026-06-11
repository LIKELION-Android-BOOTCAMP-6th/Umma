package com.app.umma.presentation.dashboard

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCardEmptyFlagsTest {

    // summary==null 이면(신규/preload 직후) 모든 카드 Empty=true 로 안전하게 평가된다.
    @Test
    fun `null summary returns all empty true`() {
        val flags = dashboardCardEmptyFlags(null)
        assertTrue(flags.conversationEmpty)
        assertTrue(flags.studyEmpty)
        assertTrue(flags.feedbackEmpty)
        assertTrue(flags.statisticsEmpty)
    }

    // 모든 영역에 데이터가 있으면 4개 모두 false.
    @Test
    fun `full summary returns all empty false`() {
        val flags = dashboardCardEmptyFlags(fullSummary())
        assertFalse(flags.conversationEmpty)
        assertFalse(flags.studyEmpty)
        assertFalse(flags.feedbackEmpty)
        assertFalse(flags.statisticsEmpty)
    }

    // 대화 데이터만 없을 때 conversationEmpty=true, 나머지 false.
    @Test
    fun `no conversation data — only conversationEmpty is true`() {
        val flags = dashboardCardEmptyFlags(
            fullSummary().copy(recentTopic = null, recentMinutes = 0)
        )
        assertTrue(flags.conversationEmpty)
        assertFalse(flags.studyEmpty)
        assertFalse(flags.feedbackEmpty)
        assertFalse(flags.statisticsEmpty)
    }

    // 학습 데이터만 없을 때 studyEmpty=true, 나머지 false.
    @Test
    fun `no study data — only studyEmpty is true`() {
        val flags = dashboardCardEmptyFlags(
            fullSummary().copy(dueFlashcards = 0, savedFlashcards = 0)
        )
        assertFalse(flags.conversationEmpty)
        assertTrue(flags.studyEmpty)
        assertFalse(flags.feedbackEmpty)
        assertFalse(flags.statisticsEmpty)
    }

    // 교정 데이터만 없을 때 feedbackEmpty=true, 나머지 false.
    @Test
    fun `no feedback data — only feedbackEmpty is true`() {
        val flags = dashboardCardEmptyFlags(
            fullSummary().copy(correctionAvailable = false)
        )
        assertFalse(flags.conversationEmpty)
        assertFalse(flags.studyEmpty)
        assertTrue(flags.feedbackEmpty)
        assertFalse(flags.statisticsEmpty)
    }

    // 통계 데이터만 없을 때 statisticsEmpty=true, 나머지 false.
    @Test
    fun `no statistics data — only statisticsEmpty is true`() {
        val flags = dashboardCardEmptyFlags(
            fullSummary().copy(grammarDelta = 0, vocabDelta = 0, fluencyDelta = 0, naturalnessDelta = 0)
        )
        assertFalse(flags.conversationEmpty)
        assertFalse(flags.studyEmpty)
        assertFalse(flags.feedbackEmpty)
        assertTrue(flags.statisticsEmpty)
    }

    // recentTopic 있고 recentMinutes==0 이면 대화 Empty=false (topic 이 우선).
    @Test
    fun `recentTopic present but recentMinutes zero — conversationEmpty false`() {
        val flags = dashboardCardEmptyFlags(
            fullSummary().copy(recentTopic = "문법", recentMinutes = 0)
        )
        assertFalse(flags.conversationEmpty)
    }

    // recentTopic 없고 recentMinutes>0 이면 대화 Empty=false (minutes 가 있으므로).
    @Test
    fun `recentTopic null but recentMinutes positive — conversationEmpty false`() {
        val flags = dashboardCardEmptyFlags(
            fullSummary().copy(recentTopic = null, recentMinutes = 5)
        )
        assertFalse(flags.conversationEmpty)
    }

    // 통계 delta 중 하나라도 0 이 아니면 statisticsEmpty=false.
    @Test
    fun `only one statistics delta nonzero — statisticsEmpty false`() {
        val base = DashSummary.initial(LangCode.EN)
            .copy(recentTopic = null, recentMinutes = 0, correctionAvailable = false)

        assertFalse(dashboardCardEmptyFlags(base.copy(grammarDelta = 1)).statisticsEmpty)
        assertFalse(dashboardCardEmptyFlags(base.copy(vocabDelta = 1)).statisticsEmpty)
        assertFalse(dashboardCardEmptyFlags(base.copy(fluencyDelta = 1)).statisticsEmpty)
        assertFalse(dashboardCardEmptyFlags(base.copy(naturalnessDelta = 1)).statisticsEmpty)
    }

    /** 모든 카드에 데이터가 있는 기준 summary. */
    private fun fullSummary() = DashSummary(
        lang = LangCode.EN,
        recentMinutes = 30,
        recentTopic = "여행",
        correctionAvailable = true,
        dueFlashcards = 5,
        savedFlashcards = 10,
        grammarDelta = 3,
        fluencyDelta = 2,
        vocabDelta = 1,
        naturalnessDelta = 4,
    )
}
