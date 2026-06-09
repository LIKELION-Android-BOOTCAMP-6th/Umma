package com.app.umma.domain.model.statistics

import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangAbilityStats
import com.app.umma.domain.model.learningstate.LangState

/**
 * Statistics 화면의 초기 진입 상태를 한 번에 묶은 스냅샷이다.
 *
 * 카드에는 현재 LangState에서 계산한 능력치를 보여주고,
 * 차트는 같은 능력치가 기록된 StatisticsHistory를 조회한다.
 */
data class StatisticsOverview(
    // 현재 사용자 식별자. Statistics history 조회와 계정 분리를 위해 필요하다.
    val userId: String,
    // Statistics 화면이 기준으로 삼는 현재 선택 언어다.
    val selectedLearningLanguage: LangCode,
    // 현재 선택 언어의 전체 LangState. 카드와 history 기록의 원천이다.
    val currentLangState: LangState,
    // 어휘/표현력처럼 아직 측정 준비 중인 legacy 표시값도 필요하므로 external을 함께 둔다.
    val currentExternalMetrics: ExternalMetrics,
    // 현재 카드가 직접 읽는 능력 집계값이다. history 기록도 이 값과 같은 산식을 사용해야 한다.
    val currentLangAbilityStats: LangAbilityStats = LangAbilityStats.initial(),
    // 카드/차트가 사용할 지표 식별자 목록. 화면 순서도 이 목록을 따른다.
    val availableMetricTypes: List<StatisticsMetricType>,
    // 현재 선택 언어의 history 조회가 가능한지 보여주는 계약이다.
    val historyQueryState: StatisticsHistoryQueryState
)

/**
 * Statistics 화면이 현재 history 를 조회할 수 있는지 나타내는 상태다.
 *
 * 실제 history 조회는 STAT-003/004에서 다루고,
 * STAT-001에서는 "조회할 수 있는 상태인지"만 먼저 확인한다.
 */
sealed interface StatisticsHistoryQueryState {
    /**
     * userId + language 기준으로 history 조회를 이어갈 수 있는 상태다.
     */
    data class Ready(
        val userId: String,
        val language: LangCode
    ) : StatisticsHistoryQueryState

    /**
     * 현재 화면 진입 시점에 조회 준비가 안 된 상태다.
     *
     * selected language 누락, userId 누락, LangState 누락처럼
     * 후속 flow가 안전하게 진행될 수 없는 경우를 표현한다.
     */
    data class Unavailable(val reason: String) : StatisticsHistoryQueryState
}
