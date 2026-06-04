package com.app.umma.domain.model.chat

/**
 * 이번 USER final transcript를 최근 대화 흐름 안에서 어떻게 해석할지 나타내는 임시 신호입니다.
 *
 * 이 모델은 장기 학습 상태가 아니며 저장하지 않습니다. 짧은 단어/구가 실제로 막힘인지,
 * 최근 흐름 안에서 정상적으로 이어지는 말인지 구분하기 위해 turn override 계산에만 사용합니다.
 */
data class ChatTurnContextSignal(
    // 이번 사용자 발화가 최근 대화에서 맡는 역할입니다.
    val latestUserTurnRole: LatestUserTurnRole,
    // 직전 AI 발화가 질문으로 보였는지 남겨, 테스트와 후속 튜닝에서 분류 이유를 확인할 수 있게 합니다.
    val followsAssistantQuestion: Boolean = false
) {
    companion object {
        // 최근 맥락을 읽지 못했거나 판단 근거가 없을 때 쓰는 안전한 기본값입니다.
        val Neutral = ChatTurnContextSignal(latestUserTurnRole = LatestUserTurnRole.Unknown)
    }
}

/**
 * 최근 turn과 이번 USER final transcript를 함께 봤을 때의 발화 역할입니다.
 */
enum class LatestUserTurnRole {
    // 짧더라도 최근 대화 흐름 안에서 정상적으로 이어지는 발화입니다. 반복적인 보정은 완화해야 합니다.
    ProgressingInContext,
    // 이전 주제를 이어 설명하거나 확장하는 발화입니다.
    TopicContinuation,
    // 사용자가 새로운 주제로 방향을 바꾸는 발화입니다.
    TopicShift,
    // 사용자가 되묻거나 설명을 요청하는 발화입니다.
    ClarificationRequest,
    // 맥락을 봐도 조각나거나 막힌 신호가 강한 발화입니다.
    StuckOrFragment,
    // 충분히 긴 자연 발화라 별도 보정이 크지 않아도 되는 발화입니다.
    FluentTurn,
    // 근거가 부족해 기존 transcript 기반 판단으로 fallback해야 하는 상태입니다.
    Unknown
}
