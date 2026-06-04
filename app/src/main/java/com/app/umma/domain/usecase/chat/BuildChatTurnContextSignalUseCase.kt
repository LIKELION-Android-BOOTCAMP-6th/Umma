package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatTurnContextSignal
import com.app.umma.domain.model.chat.LatestUserTurnRole
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import javax.inject.Inject

/**
 * 최근 확정 turn과 이번 USER final transcript를 조합해 이번 발화의 대화상 역할을 계산합니다.
 *
 * 이 UseCase는 prompt를 만들거나 저장 모델을 갱신하지 않습니다. `recentFullContext` 전체를 매 turn
 * 모델에 다시 주입하지 않고, domain 내부에서 짧은 맥락 신호로만 압축하기 위한 경계입니다.
 */
class BuildChatTurnContextSignalUseCase @Inject constructor() {
    operator fun invoke(
        recentFullContext: List<SessionTurn>,
        userFinalTranscript: String
    ): ChatTurnContextSignal {
        // final transcript가 비어 있으면 발화 역할을 추정하지 않고 기존 기본 정책에 맡긴다.
        val normalized = userFinalTranscript.trim()
        if (normalized.isBlank()) return ChatTurnContextSignal.Neutral

        // 최근 전체 원문을 보지 않고, 직전 흐름을 판단하는 데 필요한 작은 window만 사용한다.
        val recentTurns = recentFullContext.takeLast(MAX_CONTEXT_TURNS)
        // "직전 AI 질문에 대한 답"만 이 경로로 보정한다. 중간에 USER turn이 끼어 있으면 이전 질문을 현재 답변 근거로 쓰지 않는다.
        val previousTurn = recentTurns.lastOrNull()
        val followsAssistantQuestion = previousTurn
            ?.takeIf { it.role == TurnSpeaker.AI }
            ?.text
            ?.let(::looksLikeAssistantQuestion)
            ?: false

        // 명시적 되묻기는 짧아도 답변이 아니라 설명 요청이다.
        if (looksLikeClarificationRequest(normalized)) {
            return ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.ClarificationRequest,
                followsAssistantQuestion = followsAssistantQuestion
            )
        }

        // 충분히 긴 발화는 단어 조각이 아니므로 기존 fluent 정책으로 처리할 수 있게 표시한다.
        if (looksLikeFluentTurn(normalized)) {
            return ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.FluentTurn,
                followsAssistantQuestion = followsAssistantQuestion
            )
        }

        // 직전 AI가 질문했고 사용자가 짧은 단어/명사구로 답했다면, 막힘이 아니라 정상적인 대화 진행으로 본다.
        if (followsAssistantQuestion && looksLikeShortAnswer(normalized)) {
            return ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.ProgressingInContext,
                followsAssistantQuestion = true
            )
        }

        // 짧지만 질문 답변 근거가 없으면 여전히 조각난 발화일 수 있으므로 기존 fallback을 유지한다.
        if (looksLikeShortAnswer(normalized)) {
            return ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.StuckOrFragment,
                followsAssistantQuestion = followsAssistantQuestion
            )
        }

        // 위 조건에 걸리지 않는 보통 길이 발화는 최근 주제를 이어갈 가능성이 높다.
        return ChatTurnContextSignal(
            latestUserTurnRole = LatestUserTurnRole.TopicContinuation,
            followsAssistantQuestion = followsAssistantQuestion
        )
    }

    private fun looksLikeAssistantQuestion(text: String): Boolean {
        // 물음표는 언어와 관계없이 가장 안정적인 질문 신호다.
        if (text.contains("?") || text.contains("？")) return true

        // Realtime 응답은 구두점이 누락될 수 있어 대표적인 질문/선택 유도 표현도 보조 신호로 사용한다.
        val lowerText = text.lowercase()
        return QUESTION_HINTS.any { hint -> lowerText.contains(hint) }
    }

    private fun looksLikeClarificationRequest(text: String): Boolean {
        // 사용자가 되묻는 경우는 짧아도 직전 질문의 답변이 아니라 설명 요청으로 다뤄야 한다.
        val lowerText = text.lowercase()
        return CLARIFICATION_HINTS.any { hint -> lowerText.contains(hint) }
    }

    private fun looksLikeShortAnswer(text: String): Boolean {
        // 공백 기반 토큰 수와 문자 길이를 같이 봐서 영어/한국어/일본어의 짧은 답변을 모두 포착한다.
        val tokens = text.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        return tokens.size <= SHORT_ANSWER_TOKEN_LIMIT || text.length <= SHORT_ANSWER_CHAR_LIMIT
    }

    private fun looksLikeFluentTurn(text: String): Boolean {
        // 충분한 단어 수가 있으면 최근 질문의 단답보다 사용자의 자연 발화로 보는 것이 안전하다.
        val tokens = text.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        return tokens.size >= FLUENT_TOKEN_THRESHOLD
    }

    private companion object {
        // 맥락 신호는 직전 흐름만 필요하므로 전체 recentFullContext를 순회하지 않는다.
        private const val MAX_CONTEXT_TURNS = 5
        // 짧은 명사구/단어 답변을 포착하는 토큰 기준이다.
        private const val SHORT_ANSWER_TOKEN_LIMIT = 4
        // 공백이 적은 언어의 짧은 답변을 포착하는 문자 기준이다.
        private const val SHORT_ANSWER_CHAR_LIMIT = 18
        // 이 정도 길이면 단어 조각 fallback보다 fluent 정책이 우선되어야 한다.
        private const val FLUENT_TOKEN_THRESHOLD = 8
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val QUESTION_HINTS = listOf(
            "what ",
            "which ",
            "who ",
            "where ",
            "when ",
            "why ",
            "how ",
            "do you",
            "did you",
            "are you",
            "would you",
            "could you",
            "can you",
            "what do you like",
            "do you like",
            "would you like",
            "tell me",
            "choose",
            "pick",
            "which do you prefer",
            "do you prefer",
            "무엇",
            "뭐",
            "어떤",
            "어디",
            "언제",
            "왜",
            "어떻게",
            "선택",
            "어땠",
            "ですか",
            "ますか",
            "でしょうか",
            "どうですか",
            "どうでしたか",
            "何がいい",
            "なにがいい",
            "どれがいい",
            "どちらがいい"
        )
        private val CLARIFICATION_HINTS = listOf(
            "what do you mean",
            "say again",
            "again please",
            "i don't understand",
            "i dont understand",
            "다시",
            "무슨 뜻",
            "이해 안",
            "설명"
        )
    }
}
