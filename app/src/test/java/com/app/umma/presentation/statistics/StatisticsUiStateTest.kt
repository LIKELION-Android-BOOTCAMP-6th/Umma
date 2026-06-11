package com.app.umma.presentation.statistics

import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.LangAbilityStats
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.app.umma.domain.model.statistics.StatisticsMetricType
import com.app.umma.domain.model.statistics.StatisticsOverview
import com.app.umma.presentation.statistics.model.StatisticsMetricSummaryItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StatisticsUiState.isContentEmpty] 파생 프로퍼티 단위 테스트.
 *
 * STAT-UX-001: 데이터 없는 신규 언어 진입 시 Empty 분기가 올바르게 평가되는지,
 * 그리고 부분 데이터·null overview 등 오탐 케이스에서 false 를 반환하는지 검증한다.
 */
class StatisticsUiStateTest {

    @Test
    fun `isContentEmpty는 overview 존재 + 모든 카드 비가용 시 true를 반환한다`() {
        val state = StatisticsUiState(
            isLoading = false,
            overview = stubOverview(),
            metricSummaryCards = unavailableCards()
        )

        assertTrue(state.isContentEmpty)
    }

    @Test
    fun `isContentEmpty는 카드 중 하나라도 가용 시 false를 반환한다(부분 데이터 오탐 방지)`() {
        // 첫 번째 카드만 isAvailable = true 인 부분 데이터 상태
        val partialCards = unavailableCards().mapIndexed { index, card ->
            if (index == 0) card.copy(isAvailable = true) else card
        }
        val state = StatisticsUiState(
            isLoading = false,
            overview = stubOverview(),
            metricSummaryCards = partialCards
        )

        assertFalse(state.isContentEmpty)
    }

    @Test
    fun `isContentEmpty는 overview가 null이면 false를 반환한다`() {
        val state = StatisticsUiState(
            isLoading = false,
            overview = null,
            metricSummaryCards = unavailableCards()
        )

        assertFalse(state.isContentEmpty)
    }

    @Test
    fun `isContentEmpty는 metricSummaryCards가 비어 있으면 false를 반환한다`() {
        // 카드가 아직 변환되지 않은 중간 상태에서 Empty 분기로 오진입하지 않아야 한다.
        val state = StatisticsUiState(
            isLoading = false,
            overview = stubOverview(),
            metricSummaryCards = emptyList()
        )

        assertFalse(state.isContentEmpty)
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun stubOverview(): StatisticsOverview {
        val lang = LangCode.EN
        return StatisticsOverview(
            userId = "user-1",
            selectedLearningLanguage = lang,
            currentLangState = LangState.initial(lang),
            currentExternalMetrics = ExternalMetrics.initial(),
            currentLangAbilityStats = LangAbilityStats.initial(),
            availableMetricTypes = StatisticsMetricType.entries,
            historyQueryState = StatisticsHistoryQueryState.Ready(
                userId = "user-1",
                language = lang
            )
        )
    }

    /** 6개 지표 모두 측정 준비 중인 신규-언어 카드 목록 */
    private fun unavailableCards(): List<StatisticsMetricSummaryItem> {
        return StatisticsMetricType.entries.map { type ->
            StatisticsMetricSummaryItem(
                metricType = type,
                title = type.displayName,
                valueText = "측정 준비 중",
                isAvailable = false
            )
        }
    }
}
