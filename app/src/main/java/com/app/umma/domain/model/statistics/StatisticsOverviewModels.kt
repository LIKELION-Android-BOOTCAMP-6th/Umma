package com.app.umma.domain.model.statistics

import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState

/**
 * Statistics 화면의 초기 진입 상태를 한 번에 묶은 스냅샷이다.
 *
 * STAT-001은 아직 카드/차트 렌더링이 아니라, 현재 선택 언어와
 * 그 언어의 LangState.external 을 안전하게 준비하는 단계에 집중한다.
 */
data class StatisticsOverview(
    // 현재 사용자 식별자. Statistics history 조회와 계정 분리를 위해 필요하다.
    val userId: String,
    // Statistics 화면이 기준으로 삼는 현재 선택 언어다.
    val selectedLearningLanguage: LangCode,
    // 현재 선택 언어의 전체 LangState.
    // STAT-001에서는 이 스냅샷을 준비만 하고, 002/003에서 카드/차트 입력으로 이어받는다.
    val currentLangState: LangState,
    // 화면이 바로 사용할 수 있도록 미리 꺼낸 외부 지표 스냅샷이다.
    val currentExternalMetrics: ExternalMetrics,
    // 이후 카드/차트가 사용할 지표 식별자 목록.
    // MVP 기준 5개 지표만 고정하고, 여기서 후속 이슈로 넘길 기준을 통일한다.
    val availableMetricTypes: List<StatisticsMetricType>,
    // 현재 선택 언어의 history 조회가 가능한지 보여주는 가벼운 계약이다.
    // 실제 history loading 은 003/004에서 다루고, 001은 "조회 가능 상태"만 준비한다.
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
