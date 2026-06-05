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

    // ──────────────────────────────────────────────────────────────────────────
    // 경계 어댑터 회귀 가드 — COR-TUNE-004 분할/필터 도입 후 sourceTurnId 정합성 확인
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `split candidates from one turn all retain same source turn id`() {
        // 단일 SessionTurn 이 분할되어 여러 후보가 생겨도, 모든 분할 후보가
        // 동일한 sourceTurnId 를 갖는지 확인한다.
        // ExtractSessionCandidatesUseCase 의 sourceTurnIndex 기반 매칭이 분할에도 안전한지 검증하는 회귀 가드.
        val result = useCase(
            selectedLang = LangCode.EN,
            sessionLang = LangCode.EN,
            sessionTurns = listOf(
                sessionTurn("ai-1", TurnSpeaker.AI, "Tell me about your day.", 1_000L),
                sessionTurn("user-1", TurnSpeaker.USER, "I went to the market. It was crowded.", 2_000L)
            )
        )

        assertEquals(2, result.size)
        // 두 분할 후보 모두 원본 SessionTurn 의 turnId 를 가리켜야 한다.
        assertEquals("user-1", result[0].sourceTurnId)
        assertEquals("user-1", result[1].sourceTurnId)
        // sourceTurnIndex 도 동일한 원본 turn 순서를 유지해야 한다.
        assertEquals(1, result[0].sourceTurnIndex)
        assertEquals(1, result[1].sourceTurnIndex)
    }

    @Test
    fun `trivial session turn produces no candidate`() {
        // 경계 어댑터를 통해서도 사소한 발화는 후보에서 제외되어야 한다.
        val result = useCase(
            selectedLang = LangCode.EN,
            sessionLang = LangCode.EN,
            sessionTurns = listOf(
                sessionTurn("user-1", TurnSpeaker.USER, "hi", 1_000L)
            )
        )

        assertTrue(result.isEmpty())
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
