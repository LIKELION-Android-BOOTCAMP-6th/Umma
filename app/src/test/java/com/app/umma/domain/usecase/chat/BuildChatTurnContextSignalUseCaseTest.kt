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
