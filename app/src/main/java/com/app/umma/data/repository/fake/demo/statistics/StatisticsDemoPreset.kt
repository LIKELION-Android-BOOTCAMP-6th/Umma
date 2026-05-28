package com.app.umma.data.repository.fake.demo.statistics

/**
 * DEMO_FLOW_STATISTICS.md의 통계 데모/QA 시나리오와 1:1로 맞춘 preset이다.
 *
 * 이 enum은 통계 도메인의 테스트 데이터 선택만 담당한다.
 * 다른 도메인 데모 preset은 각 도메인 package에 별도로 추가해 공통 fake repository 충돌을 줄인다.
 */
enum class StatisticsDemoPreset {
    /**
     * 기본 정상 상태.
     *
     * History seed: EN/JA 모두 line chart를 그릴 수 있는 history를 제공한다.
     * LearningState seed: selected language는 EN이고, EN/JA external은 각 언어의 최신 history와 맞춘다.
     * ViewModel 기대 상태: Statistics overview, 지표 카드, 일반 chart dialog가 모두 정상 표시된다.
     */
    NormalStatistics,

    /**
     * 표현 폭 y축 상한 확장 상태.
     *
     * History seed: EN expressionRange가 10을 넘는 history를 포함한다.
     * LearningState seed: EN external.expressionRange도 최신 history의 overflow 값과 맞춘다.
     * ViewModel 기대 상태: 표현 폭 카드 클릭 시 차트 y축이 데이터 최대값에 맞게 확장된다.
     */
    ExpressionRangeOverflow,

    /**
     * 학습 언어 전환 중 이전 언어 응답이 늦게 도착하는 상태.
     *
     * History seed: EN/JA history를 모두 제공하되 EN observe 응답만 지연한다.
     * LearningState seed: selected language 변경이 가능한 EN/JA LangState를 제공한다.
     * ViewModel 기대 상태: JA 전환 후 늦게 도착한 EN 카드/차트 결과가 최신 JA 화면을 덮지 않는다.
     */
    DelayedLanguageSwitch,

    /**
     * history가 1건뿐인 데이터 부족 상태.
     *
     * History seed: 현재 사용자 EN 최신 history 1건만 제공한다.
     * LearningState seed: EN external은 해당 단일 최신 history와 맞춘다.
     * ViewModel 기대 상태: overview/지표 카드는 표시되지만 chart dialog는 Empty 상태가 된다.
     */
    ShortHistory,

    /**
     * history가 전혀 없는 상태.
     *
     * History seed: 빈 목록을 제공한다.
     * LearningState seed: 기본 EN/JA external은 유지한다.
     * ViewModel 기대 상태: Statistics 화면은 진입되지만 chart source 없음으로 Empty chart가 표시된다.
     */
    EmptyHistory,

    /**
     * pending write-back이 실패하는 상태.
     *
     * History seed: EN history를 모두 PENDING으로 제공하고 refresh는 no-op으로 유지한다.
     * LearningState seed: 기본 EN/JA external을 유지한다.
     * ViewModel 기대 상태: 기존 카드/차트는 유지되고, pending sync 실패는 화면 차단 없이 보조 상태로만 남는다.
     */
    PendingSyncFailure,

    /**
     * history 조회 자체가 실패하는 상태.
     *
     * History seed: 기본 history를 유지하지만 observeHistory가 Retry 상태를 반환한다.
     * LearningState seed: 기본 EN/JA external을 유지한다.
     * ViewModel 기대 상태: overview는 표시되고, chart dialog는 Error와 다시 시도 버튼을 표시한다.
     */
    FetchFailure,

    /**
     * background refresh만 실패하는 상태.
     *
     * History seed: 기본 local history는 유지하고 refreshHistory만 실패시킨다.
     * LearningState seed: 기본 EN/JA external을 유지한다.
     * ViewModel 기대 상태: 기존 카드/차트는 사라지지 않고 refresh 실패는 보조 문구로만 표시된다.
     */
    RefreshFailure,

    /**
     * overview external metrics가 초기값인 상태.
     *
     * History seed: 기본 history를 유지한다.
     * LearningState seed: selected EN LangState.external만 ExternalMetrics.initial()로 둔다.
     * ViewModel 기대 상태: 지표 카드는 Empty 값으로 표시되고, history가 있으면 chart는 기존 기록 기준으로 열릴 수 있다.
     */
    InitialExternalMetrics,

    /**
     * selected language가 없는 overview 구성 실패 상태.
     *
     * History seed: 기본 history를 유지한다.
     * LearningState seed: userPref를 null로 둔다.
     * ViewModel 기대 상태: overview 조립 실패 Error 화면과 다시 시도 버튼이 표시된다.
     */
    MissingSelectedLanguage,

    /**
     * selected language는 있지만 해당 LangState가 없는 overview 구성 실패 상태.
     *
     * History seed: 기본 history를 유지한다.
     * LearningState seed: userPref는 유지하고 langStates를 비운다.
     * ViewModel 기대 상태: 현재 언어 LangState 없음 Error 화면과 다시 시도 버튼이 표시된다.
     */
    MissingCurrentLangState,

    /**
     * 빠른 지표 전환 중 이전 chart 응답이 늦게 도착하는 상태.
     *
     * History seed: 기본 history를 유지하되 모든 language observe 응답을 지연한다.
     * LearningState seed: 기본 EN/JA external을 유지한다.
     * ViewModel 기대 상태: 여러 지표를 빠르게 눌러도 마지막으로 선택한 지표의 chart만 화면에 남는다.
     */
    DelayedMetricSwitch
}

/**
 * 통계 데모 시나리오의 활성 preset 선택 지점이다.
 *
 * mockDebug 수동 테스트 시 DEMO_FLOW_STATISTICS.md의 "활성 preset"에 맞춰 이 값만 변경한다.
 * 테스트 대상이 통계가 아니면 NormalStatistics로 유지한다.
 */
object StatisticsDemoPresetConfig {
    val activePreset: StatisticsDemoPreset = StatisticsDemoPreset.NormalStatistics
}
