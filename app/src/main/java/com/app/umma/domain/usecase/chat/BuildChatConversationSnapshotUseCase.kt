package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatComprehensionSignal
import com.app.umma.domain.model.chat.ChatConversationSnapshot
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import javax.inject.Inject

/**
 * 확정된 final turn을 세션용 대화 위치 스냅샷으로 압축합니다.
 *
 * 이 UseCase는 AI 요약 호출을 하지 않고, 단어별 응답 규칙도 만들지 않습니다.
 * 목적은 "사용자가 방금 무엇을 하려 했는지"와 "AI가 직전에 무엇을 반복했는지"를
 * 아주 좁게 기억해 다음 turn hint가 정책 반복이 아니라 대화 위치 요약이 되게 하는 것입니다.
 */
class BuildChatConversationSnapshotUseCase @Inject constructor() {

    operator fun invoke(
        previous: ChatConversationSnapshot,
        turn: SessionTurn
    ): ChatConversationSnapshot {
        val normalizedText = turn.text.normalizeForSnapshot()
        if (normalizedText.isBlank()) return previous

        return when (turn.role) {
            TurnSpeaker.USER -> {
                // USER final은 현재 대화의 방향을 가장 직접적으로 보여준다.
                // 직전 AI 발화와 나란히 보아야 "표현 뜻 확인"을 새 주제 질문으로 오해하지 않는다.
                previous.copy(
                    currentTopic = normalizedText.toShortFragment(),
                    inferredUserIntent = inferUserIntent(
                        userText = normalizedText,
                        previousAiText = previous.lastAiUtteranceFragment
                    ),
                    flowNote = inferFlowNote(
                        userText = normalizedText,
                        previousAiText = previous.lastAiUtteranceFragment
                    ),
                    userSignal = inferUserSignal(
                        userText = normalizedText,
                        previousAiText = previous.lastAiUtteranceFragment
                    )
                )
            }
            TurnSpeaker.AI -> {
                val aiMoveSummary = summarizeAiMove(normalizedText)
                previous.copy(
                    lastAiMoveSummary = aiMoveSummary,
                    lastAiUtteranceFragment = normalizedText.toShortFragment(),
                    // 반복 방지는 전역 금지어가 아니라 직전 AI 움직임을 피하기 위한 세션 상태다.
                    avoidRepeating = (previous.avoidRepeating + aiMoveSummary)
                        .distinct()
                        .takeLast(MAX_AVOID_ITEMS)
                )
            }
        }
    }

    private fun inferUserIntent(
        userText: String,
        previousAiText: String?
    ): String {
        // 구체 표현별 분기 대신, 직전 AI 말과의 관계를 넓게 본다.
        // 초저숙련에서는 "X는 Y지?"가 새 주제가 아니라 직전 표현 뜻 확인인 경우가 많다.
        return when {
            previousAiText.hasTargetLikeText() && userText.isQuestionLike() ->
                "사용자는 직전 AI 말에 나온 표현의 의미나 흐름을 확인하려는 것으로 보인다."
            userText.hasDifficultyCue() ->
                "사용자는 방금 흐름이 어렵거나 선택하기 어려워 작게 이어받을 필요가 있어 보인다."
            userText.isAcceptanceLike() ->
                "사용자는 방금 표현을 받아들이고 대화를 계속할 준비가 된 것으로 보인다."
            userText.isQuestionLike() ->
                "사용자는 방금 말의 의미나 다음 흐름을 확인하려는 것으로 보인다."
            userText.length <= SHORT_FRAGMENT_LENGTH ->
                "사용자는 아주 짧게 반응한 상태다."
            else ->
                "사용자는 방금 말한 내용으로 대화를 이어가려는 것으로 보인다."
        }
    }

    private fun inferFlowNote(
        userText: String,
        previousAiText: String?
    ): String? {
        val previousAiWasTargetHeavy = previousAiText.hasTargetLikeText()

        return when {
            userText.isQuestionLike() && previousAiWasTargetHeavy ->
                "표현은 짧게 확인하고, 설명에 머물지 말고 현재 말이나 세션의 주제 후보로 작게 이어간다."
            userText.isAcceptanceLike() ->
                "이미 받아들인 표현을 다시 설명하지 말고 현재 말이나 세션의 주제 후보로 작게 이어간다."
            previousAiWasTargetHeavy && userText.hasDifficultyCue() ->
                "직전 AI 말이 사용자에게 부담이었을 수 있으므로 의미를 짧게 받치고 대화 흐름을 작게 되돌린다."
            else -> null
        }
    }

    private fun inferUserSignal(
        userText: String,
        previousAiText: String?
    ): ChatComprehensionSignal {
        // 한국어와 다른 문자가 섞인 정도와 직전 AI 말의 부담 정도만 넓게 본다.
        // 특정 실패 문장을 prompt 규칙으로 만들지 않고, "대화가 끊겼을 가능성"만 hint에 전달한다.
        val hasHangul = userText.hasHangul()
        val hasNonHangulLetter = userText.hasNonHangulLetter()
        val previousAiWasTargetHeavy = previousAiText.hasTargetLikeText()

        return when {
            userText.isQuestionLike() -> ChatComprehensionSignal.Struggling
            userText.hasDifficultyCue() -> ChatComprehensionSignal.Struggling
            userText.isAcceptanceLike() -> ChatComprehensionSignal.Following
            hasHangul && hasNonHangulLetter -> ChatComprehensionSignal.MixedPrimaryLang
            previousAiWasTargetHeavy && userText.length <= SHORT_FRAGMENT_LENGTH -> ChatComprehensionSignal.FragmentOnly
            userText.length <= SHORT_FRAGMENT_LENGTH -> ChatComprehensionSignal.FragmentOnly
            else -> ChatComprehensionSignal.Following
        }
    }

    private fun summarizeAiMove(text: String): String {
        // AI 발화도 원문 전체를 보존하지 않고, 직전 움직임을 알아볼 정도만 남긴다.
        return text.toShortFragment()
    }

    private fun String.normalizeForSnapshot(): String {
        return trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun String.toShortFragment(): String {
        if (length <= MAX_FRAGMENT_LENGTH) return this
        return take(MAX_FRAGMENT_LENGTH).trimEnd() + "..."
    }

    private fun String?.hasTargetLikeText(): Boolean {
        val text = this ?: return false
        // "학습언어가 실제로 무엇인지"를 여기서 판정하지 않는다.
        // 한국어 기준언어와 다른 문자 흐름이 충분하면 직전 AI 말이 사용자에게 부담일 수 있다고만 본다.
        return text.count { it.isLetter() && it !in '\uAC00'..'\uD7A3' } >= TARGET_LIKE_LETTER_THRESHOLD
    }

    private fun String.hasHangul(): Boolean {
        return any { it in '\uAC00'..'\uD7A3' }
    }

    private fun String.hasNonHangulLetter(): Boolean {
        return any { it.isLetter() && it !in '\uAC00'..'\uD7A3' }
    }

    private fun String.isQuestionLike(): Boolean {
        return contains("?") || contains("？")
    }

    private fun String.hasDifficultyCue(): Boolean {
        // 단어별 응답 분기를 만들지 않고, 대화가 끊겼다는 넓은 신호만 먼저 분리한다.
        return contains("모르") ||
            contains("못 알아") ||
            contains("어려") ||
            contains("わから")
    }

    private fun String.isAcceptanceLike(): Boolean {
        // "알아/알겠어/맞아" 계열은 지원 언어가 섞여도 이해 실패가 아니라 진행 가능 신호에 가깝다.
        return contains("알아") ||
            contains("알겠") ||
            contains("맞아")
    }

    private companion object {
        const val SHORT_FRAGMENT_LENGTH = 12
        const val MAX_FRAGMENT_LENGTH = 48
        const val MAX_AVOID_ITEMS = 3
        const val TARGET_LIKE_LETTER_THRESHOLD = 4
    }
}
