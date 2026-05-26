package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.CorrectionResult
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildLangStateUpdateInputUseCaseTest {

    private val useCase = BuildLangStateUpdateInputUseCase()

    @Test
    fun `builds lang state update input from user turns and stable event parts`() {
        val command = sampleCommand(
            turns = listOf(
                sampleTurn(turnId = "ai-1", role = TurnSpeaker.AI, text = "Try past tense."),
                sampleTurn(turnId = "user-1", role = TurnSpeaker.USER, text = " I go yesterday. "),
                sampleTurn(turnId = "user-blank", role = TurnSpeaker.USER, text = "   "),
                sampleTurn(turnId = "user-2", role = TurnSpeaker.USER, text = "I need book ticket."),
            ),
            stableEventParts = listOf("suggestion-2", "suggestion-1"),
        )

        // 같은 입력에서 analyzedAt만 바꾸면, 중복 방지 규칙이 실제로 시간값에 흔들리지 않는지 볼 수 있다.
        val first = useCase(command).getOrThrow()
        val second = useCase(command.copy(analyzedAt = command.analyzedAt + 10_000L)).getOrThrow()

        // Session Memory 스코프는 LiveSession id가 아니라 사용자와 언어 기준으로 고정된다.
        assertEquals("uid-1_en", first.sessionMemoryKey)
        assertEquals("uid-1", first.uid)
        assertEquals(LangCode.EN, first.lang)
        assertEquals(command.currentState, first.currentState)

        // LS 입력에는 USER 발화만 들어가고, 공백 발화와 AI 발화는 분석 본문에서 제외된다.
        assertEquals(2, first.recentUserTurns.size)
        assertTrue(first.recentUserTurns.all { it.speaker == TurnSpeaker.USER })
        assertEquals("I go yesterday.", first.recentUserTurns.first().text)

        // analyzedAt이 달라져도 같은 완료 요청이면 중복 방지 id가 유지되어야 한다.
        assertEquals(first.analysisEventId, second.analysisEventId)
        assertFalse(first.analysisEventId.isNullOrBlank())
        assertTrue(first.flashcardReviewEvents.isEmpty())
        assertNotNull(first.correctionResult)
    }

    @Test
    fun `fails when selected language differs from update target`() {
        val result = useCase(
            sampleCommand(
                selectedLang = LangCode.JA,
                lang = LangCode.EN,
            )
        )

        // 선택 언어가 바뀐 뒤 도착한 완료 요청은 stale 요청으로 보고 막는다.
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when current lang state is missing`() {
        val result = useCase(
            sampleCommand(currentState = null)
        )

        // currentState가 없으면 LS-006 이동 평균 계산의 기준 상태가 없다.
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when there is no meaningful user turn`() {
        val result = useCase(
            sampleCommand(
                turns = listOf(
                    sampleTurn(role = TurnSpeaker.AI, text = "Assistant only."),
                    sampleTurn(role = TurnSpeaker.USER, text = "   "),
                )
            )
        )

        // user turn이 없으면 recentUserTurns를 만들 수 없고 분석 입력도 성립하지 않는다.
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when correction context is empty`() {
        val result = useCase(
            sampleCommand(turns = emptyList())
        )

        // RT-003 context 자체가 비어 있으면 LS 입력으로 변환할 사용자 발화가 없다.
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when uid is blank`() {
        val result = useCase(
            sampleCommand(uid = "   ")
        )

        // uid는 sessionMemoryKey와 analysisEventId의 기준값이라 공백이면 조립을 중단한다.
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when stable event parts are missing`() {
        val result = useCase(
            sampleCommand(stableEventParts = listOf(" ", ""))
        )

        // stableEventParts는 재시도 중복 방지 id의 caller 제공 재료다.
        assertTrue(result.isFailure)
    }

    private fun sampleCommand(
        uid: String = "uid-1",
        lang: LangCode = LangCode.EN,
        selectedLang: LangCode = lang,
        currentState: LangState? = LangState.initial(lang),
        turns: List<SessionTurn> = listOf(
            sampleTurn(role = TurnSpeaker.USER, text = "I go yesterday.")
        ),
        stableEventParts: List<String> = listOf("suggestion-1"),
    ): BuildLangStateUpdateInputCommand {
        // 테스트 입력은 실제 COR-006 호출부가 넘길 수 있는 최소 조합에 맞춰 둔다.
        return BuildLangStateUpdateInputCommand(
            uid = uid,
            lang = lang,
            selectedLang = selectedLang,
            currentState = currentState,
            correctionContextTurns = turns,
            stableEventParts = stableEventParts,
            analyzedAt = 1_700_000_000_000L,
            correctionResult = CorrectionResult(
                correctedText = "I went yesterday.",
                correctionCount = 1,
                notes = "Use past tense."
            ),
            correctionAvailableOverride = false,
        )
    }

    private fun sampleTurn(
        turnId: String = "turn-1",
        role: TurnSpeaker,
        text: String,
    ): SessionTurn {
        // SessionTurn은 RT-003 문맥의 원본 입력이므로, USER/AI 분기와 메타데이터를 함께 넣어 변환을 검증한다.
        return SessionTurn(
            turnId = turnId,
            sessionId = "live-session-1",
            text = text,
            role = role,
            createdAt = 1_700_000_000_000L,
            durationMs = 2_000L,
            tokenCount = 12,
            confidence = 0.95,
        )
    }
}
