package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractSessionCandidatesUseCaseTest {

    private val useCase = ExtractSessionCandidatesUseCase(
        extractCandidatesUseCase = ExtractCandidatesUseCase()
    )

    @Test
    fun `maps RT session turns to correction candidates and keeps source turn id`() {
        // RT-003 의 SessionTurn 은 저장소 read model 이고, CorrectionCandidate 는 교정 내부 모델이다.
        // 이 테스트는 두 모델 사이를 변환하면서 원본 turnId 와 assistant 문맥을 잃지 않는지 확인한다.
        val result = useCase(
            selectedLang = LangCode.EN,
            sessionLang = LangCode.EN,
            sessionTurns = listOf(
                sessionTurn("ai-1", TurnSpeaker.AI, "Let's talk about travel.", 1_000L),
                sessionTurn("user-1", TurnSpeaker.USER, "I go to Paris yesterday.", 2_000L),
                sessionTurn("ai-2", TurnSpeaker.AI, "You can say I went to Paris yesterday.", 3_000L)
            )
        )

        assertEquals(1, result.size)
        assertEquals("user-1", result.first().sourceTurnId)
        assertEquals(1, result.first().sourceTurnIndex)
        assertEquals("I go to Paris yesterday.", result.first().sourceText)
        assertEquals(
            "Let's talk about travel.\nYou can say I went to Paris yesterday.",
            result.first().assistantContext
        )
    }

    @Test
    fun `language mismatch does not expose session turns to correction`() {
        // 현재 선택 언어와 Session Memory 언어가 다르면 다른 언어 대화가 교정 후보로 섞이면 안 된다.
        val result = useCase(
            selectedLang = LangCode.EN,
            sessionLang = LangCode.JA,
            sessionTurns = listOf(
                sessionTurn("user-1", TurnSpeaker.USER, "hello", 1_000L)
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `keeps latest 100 turn limit and restores absolute source turn id`() {
        // 후보 추출 UseCase 는 최근 100턴으로 잘라내지만 sourceTurnIndex 는 원본 순서를 기준으로 남긴다.
        // 그래서 잘려나간 첫 turn 이후의 후보도 실제 SessionTurn.turnId 로 다시 추적할 수 있어야 한다.
        val turns = buildList {
            repeat(101) { index ->
                add(sessionTurn("user-$index", TurnSpeaker.USER, "turn-$index", index.toLong()))
            }
        }

        val result = useCase(
            selectedLang = LangCode.EN,
            sessionLang = LangCode.EN,
            sessionTurns = turns
        )

        assertEquals(100, result.size)
        assertEquals(1, result.first().sourceTurnIndex)
        assertEquals("user-1", result.first().sourceTurnId)
        assertEquals("turn-1", result.first().sourceText)
    }

    private fun sessionTurn(
        id: String,
        role: TurnSpeaker,
        text: String,
        createdAt: Long
    ): SessionTurn {
        return SessionTurn(
            turnId = id,
            sessionId = "session-en",
            text = text,
            role = role,
            createdAt = createdAt,
            durationMs = 1_000L,
            tokenCount = text.split(" ").size,
            confidence = 0.9
        )
    }
}
