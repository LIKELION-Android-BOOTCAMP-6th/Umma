package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.LatestUserTurnRole
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildChatTurnContextSignalUseCaseTest {
    private val useCase = BuildChatTurnContextSignalUseCase()

    @Test
    fun `short noun phrase after assistant question is classified as answer`() {
        // 직전 AI가 선택/취향/대상을 묻는 질문을 했으면 짧은 명사구도 정상 답변일 수 있다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "Which topic do you want to talk about?")
            ),
            userFinalTranscript = "summer trip"
        )

        assertEquals(LatestUserTurnRole.ProgressingInContext, signal.latestUserTurnRole)
        assertTrue(signal.followsAssistantQuestion)
    }

    @Test
    fun `short noun phrase without question context remains fragment signal`() {
        // 같은 짧은 명사구라도 직전 질문 근거가 없으면 기존 fragment fallback을 유지해야 한다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "I can help you practice a simple conversation.")
            ),
            userFinalTranscript = "summer trip"
        )

        assertEquals(LatestUserTurnRole.StuckOrFragment, signal.latestUserTurnRole)
    }

    @Test
    fun `conversation start phrase is not classified as failed fragment`() {
        // "Hello, let's talk"은 세션 시작 의도이지 막힘이 아니므로 첫 turn 초보 보조를 과하게 열면 안 된다.
        val signal = useCase(
            recentFullContext = emptyList(),
            userFinalTranscript = "Hello, let's talk."
        )

        assertEquals(LatestUserTurnRole.TopicContinuation, signal.latestUserTurnRole)
    }

    @Test
    fun `explicit simplify request is classified as clarification before fluent length`() {
        // 사용자가 복잡해서 이해하지 못했다고 말하면 긴 영어 문장이어도 fluent continuation보다 설명 요청이 우선이다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "Keep moderation in mind so you feel refreshed later.")
            ),
            userFinalTranscript = "I'm sorry, that's two complex sentences I can't understand totally."
        )

        assertEquals(LatestUserTurnRole.ClarificationRequest, signal.latestUserTurnRole)
    }

    @Test
    fun `pardon request is classified as clarification request`() {
        // "pardon" 계열은 직전 질문의 답이 아니라 다시 말해 달라는 신호다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "What do you enjoy most about urban travel?")
            ),
            userFinalTranscript = "Excuse me, can you pardon?"
        )

        assertEquals(LatestUserTurnRole.ClarificationRequest, signal.latestUserTurnRole)
        assertTrue(signal.followsAssistantQuestion)
    }

    @Test
    fun `short japanese answer after clear question without punctuation is classified as answer`() {
        // 일본어 Realtime transcript는 물음표가 빠질 수 있으므로 명확한 질문 어미는 보수적으로 질문으로 본다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "今日はどうでしたか")
            ),
            userFinalTranscript = "はい"
        )

        assertEquals(LatestUserTurnRole.ProgressingInContext, signal.latestUserTurnRole)
        assertTrue(signal.followsAssistantQuestion)
    }

    @Test
    fun `short japanese answer after non question statement remains fragment signal`() {
        // 질문 어미가 없는 일본어 평서문까지 질문으로 넓히면 실제 막힘 신호를 놓칠 수 있다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "私は寿司が好きです")
            ),
            userFinalTranscript = "はい"
        )

        assertEquals(LatestUserTurnRole.StuckOrFragment, signal.latestUserTurnRole)
    }

    @Test
    fun `older assistant question is ignored when previous turn is user`() {
        // 최근 window 안에 AI 질문이 있어도 직전 turn이 USER면 현재 짧은 발화를 그 질문의 답으로 단정하면 안 된다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "Which topic do you want to talk about?"),
                turn("user-1", TurnSpeaker.USER, "I am not sure yet.")
            ),
            userFinalTranscript = "summer trip"
        )

        assertEquals(LatestUserTurnRole.StuckOrFragment, signal.latestUserTurnRole)
    }

    @Test
    fun `long user turn is classified as fluent even after assistant question`() {
        // 질문 뒤라도 사용자가 충분히 길게 말하면 단답 전용 정책이 아니라 fluent 정책이 우선되어야 한다.
        val signal = useCase(
            recentFullContext = listOf(
                turn("ai-1", TurnSpeaker.AI, "How was your day?")
            ),
            userFinalTranscript = "I went to a cafe after work and talked with my friend for a long time"
        )

        assertEquals(LatestUserTurnRole.FluentTurn, signal.latestUserTurnRole)
        assertTrue(signal.followsAssistantQuestion)
    }

    private fun turn(
        id: String,
        role: TurnSpeaker,
        text: String
    ): SessionTurn {
        // context signal은 speaker/text/순서만 필요하므로 나머지 저장 필드는 최소 fixture로 채운다.
        return SessionTurn(
            turnId = id,
            sessionId = "session-1",
            text = text,
            role = role,
            createdAt = 1_000L
        )
    }
}
