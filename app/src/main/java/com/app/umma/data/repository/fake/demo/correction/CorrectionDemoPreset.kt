package com.app.umma.data.repository.fake.demo.correction

/**
 * DEMO_FLOW_CORRECTION_REVISED.md / TEST_FLOW_CORRECTION.md에서 사용하는
 * Correction 데모 preset 선택 지점이다.
 *
 * mockDebug 수동 검증 시 [CorrectionDemoPresetConfig.activePreset] 값만 바꾼 뒤
 * 앱을 다시 빌드/실행한다.
 */
enum class CorrectionDemoPreset {
    /** 교정 결과 카드가 1개 이상 표시되는 기본 상태. */
    Content,

    /** 선택 언어/세션/교정 가능 신호가 부족해 초기 Empty UI로 빠지는 상태. */
    EmptyInitial,

    /** 교정 생성은 시작되지만 suggestion 결과가 0개인 상태. */
    EmptyResult,

    /** AI 요청 실패, 파싱 실패, 필수 필드 누락 등을 대신하는 Error/Retry 상태. */
    Error,

    /** 저장 또는 완료 파이프라인 실패 후 Retry 상태에 남는 상태. */
    SaveFail,

    /** local 저장은 성공했지만 remote sync pending으로 남는 상태. */
    PendingSync,

    /** 성공 이벤트는 발생했지만 저장 카드 수가 0개인 예외 fallback 상태. */
    ZeroSavedSuccess,

    /** Dashboard 주제 칩에 짧은 topic title이 표시되는 상태. */
    TopicTitleSuccess,

    /** topic title이 없거나 요약 실패로 기존 topic/fallback 정책을 확인하는 상태. */
    TopicTitleEmpty
}

object CorrectionDemoPresetConfig {
    val activePreset: CorrectionDemoPreset = CorrectionDemoPreset.Content
}
