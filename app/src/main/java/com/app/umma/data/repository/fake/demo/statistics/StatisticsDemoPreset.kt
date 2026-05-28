package com.app.umma.data.repository.fake.demo.statistics

/**
 * DEMO_FLOW_STATISTICS.md의 통계 데모/QA 시나리오와 1:1로 맞춘 preset이다.
 *
 * 이 enum은 통계 도메인의 테스트 데이터 선택만 담당한다.
 * 다른 도메인 데모 preset은 각 도메인 package에 별도로 추가해 공통 fake repository 충돌을 줄인다.
 */
enum class StatisticsDemoPreset {
    NormalStatistics,
    ExpressionRangeOverflow,
    DelayedLanguageSwitch,
    ShortHistory,
    EmptyHistory,
    PendingSyncFailure,
    FetchFailure,
    RefreshFailure,
    InitialExternalMetrics,
    MissingSelectedLanguage,
    MissingCurrentLangState,
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
