package com.app.umma.domain.model.chat

/**
 * Chat 세션 안에서만 쓰는 현재 대화 위치 스냅샷입니다.
 *
 * 이 모델은 LangState처럼 장기 능력을 저장하거나 평가하지 않습니다.
 * USER final transcript와 AI final transcript가 확정될 때마다 현재 주제, 직전 AI 움직임,
 * 반복을 피해야 할 좁은 힌트만 갱신해 다음 응답이 대화 위치를 잃지 않도록 돕습니다.
 */
data class ChatConversationSnapshot(
    val currentTopic: String? = null,
    val inferredUserIntent: String? = null,
    val lastAiMoveSummary: String? = null,
    val lastAiUtteranceFragment: String? = null,
    val flowNote: String? = null,
    val avoidRepeating: List<String> = emptyList(),
    val userSignal: ChatComprehensionSignal = ChatComprehensionSignal.Unknown
)

/**
 * 최근 사용자 발화가 대화에서 어떤 상태로 보이는지 나타내는 내부 신호입니다.
 *
 * enum 이름은 prompt나 turn hint에 직접 노출하지 않습니다.
 * 모델에게는 "현재 사용자가 따라오는지/막혔는지/짧게 반응했는지"를 자연어로만 전달합니다.
 */
enum class ChatComprehensionSignal {
    Unknown,
    Following,
    Struggling,
    MixedPrimaryLang,
    FragmentOnly
}
