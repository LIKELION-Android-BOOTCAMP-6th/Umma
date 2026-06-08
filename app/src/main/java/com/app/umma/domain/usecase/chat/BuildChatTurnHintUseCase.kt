package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatComprehensionSignal
import com.app.umma.domain.model.chat.ChatConversationSnapshot
import javax.inject.Inject

/**
 * 세션용 대화 스냅샷을 다음 response.create에 넣을 짧은 turn hint로 변환합니다.
 *
 * 이 hint는 system prompt의 persona나 band 정책을 반복하지 않습니다.
 * 모델에게 "쉽게/천천히/짧게" 같은 장기 지시를 다시 주는 대신,
 * 현재 대화 위치와 반복 주의만 2~4줄로 알려주는 것이 책임입니다.
 */
class BuildChatTurnHintUseCase @Inject constructor() {

    operator fun invoke(snapshot: ChatConversationSnapshot): String? {
        if (!snapshot.hasUsableContext()) return null

        val lines = buildList {
            add("현재 대화 위치:")
            add("세션 prompt의 current_style과 style_reference 리듬은 유지한다.")
            snapshot.inferredUserIntent?.let { add(it) }
            snapshot.flowNote?.let { add(it) }
            snapshot.currentTopic?.let { add("최근 사용자의 흐름은 \"$it\" 쪽이다.") }
            snapshot.lastAiUtteranceFragment?.let { add("직전 AI 말 일부: \"$it\"") }
            if (snapshot.lastAiMoveSummary != null) {
                add("직전 AI 흐름을 그대로 반복하지 말고 이어받는다.")
            }
            snapshot.userSignal.toGuidanceLine()?.let { add(it) }
        }

        // response.create instructions는 매 turn 붙을 수 있으므로, 비대해지지 않게 상한을 둔다.
        return lines
            .distinct()
            .take(MAX_HINT_LINES)
            .joinToString("\n")
    }

    private fun ChatConversationSnapshot.hasUsableContext(): Boolean {
        return currentTopic != null ||
            inferredUserIntent != null ||
            lastAiMoveSummary != null ||
            lastAiUtteranceFragment != null ||
            flowNote != null ||
            userSignal != ChatComprehensionSignal.Unknown
    }

    private fun ChatComprehensionSignal.toGuidanceLine(): String? {
        return when (this) {
            ChatComprehensionSignal.Unknown -> null
            ChatComprehensionSignal.Following ->
                "사용자의 반응을 대화 반응으로 받아들이고 현재 흐름을 유지한다."
            ChatComprehensionSignal.Struggling ->
                "사용자가 확인한 내용을 새 부담으로 되돌리지 말고 흐름을 더 작게 이어간다."
            ChatComprehensionSignal.MixedPrimaryLang ->
                "언어가 섞인 반응도 소통으로 받아들이고 의도를 이어간다."
            ChatComprehensionSignal.FragmentOnly ->
                "짧은 반응만으로도 충분한 흐름으로 이어간다."
        }
    }

    private companion object {
        const val MAX_HINT_LINES = 6
    }
}
