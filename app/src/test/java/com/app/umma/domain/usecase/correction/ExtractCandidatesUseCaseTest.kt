package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractCandidatesUseCaseTest {

    private val useCase = ExtractCandidatesUseCase()

    // ──────────────────────────────────────────────────────────────────────────
    // 기존 테스트 — 입력 정제 추가 후에도 회귀가 없는지 확인
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `session language mismatch returns empty list`() {
        // 언어 불일치는 가장 이른 분기에서 처리되어야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.JA,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "hello")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extracts user turns only and attaches nearby assistant context`() {
        // USER turn만 후보로, AI turn은 assistantContext 보조에만 쓰인다.
        // 단일 문장은 분할 없이 원문 그대로 1개 후보가 되어야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.AI, "Let's talk about your trip."),
                    ConversationTurn(TurnSpeaker.USER, "I go to Paris yesterday."),
                    ConversationTurn(TurnSpeaker.AI, "You can say I went to Paris yesterday."),
                    ConversationTurn(TurnSpeaker.USER, " "),
                    ConversationTurn(TurnSpeaker.USER, "It was very fun.")
                )
            )
        )

        assertEquals(2, result.size)
        assertEquals("I go to Paris yesterday.", result[0].sourceText)
        assertEquals("Let's talk about your trip.\nYou can say I went to Paris yesterday.", result[0].assistantContext)
        assertEquals("It was very fun.", result[1].sourceText)
        assertEquals("You can say I went to Paris yesterday.", result[1].assistantContext)
    }

    @Test
    fun `limits source turns to latest 100 entries`() {
        // MAX_SOURCE_TURNS 상한이 지켜지고 잘린 뒤의 sourceTurnIndex 보정이 정확해야 한다.
        val context = buildList {
            repeat(101) { index ->
                add(ConversationTurn(TurnSpeaker.USER, "turn-$index"))
            }
        }

        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = context
            )
        )

        assertEquals(100, result.size)
        assertEquals("turn-1", result.first().sourceText)
        assertEquals(1, result.first().sourceTurnIndex)
    }

    @Test
    fun `returns empty when there is no user turn`() {
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.AI, "Only assistant text")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `candidate id is stable enough for current turn ordering`() {
        // splitIndex 추가 후에도 기존 "lang.code-sourceTurnIndex-" prefix 형식이 유지되어야 한다.
        // 단일 문장(splitIndex=0)의 형식: "en-0-0-<hash>" → startsWith("en-0-") 통과.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "I need help with grammar")
                )
            )
        )

        assertEquals(1, result.size)
        assertTrue(result.first().id.startsWith("en-0-"))
        assertNull(result.first().sourceTurnId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 사소한 발화 필터 (COR-TUNE-004)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `trivial greetings are excluded from candidates`() {
        // "Hi"·"ok"·"hello" 같은 인사말은 교정 후보에서 제외해야 한다.
        // 교정 가치가 없는 발화가 교정 카드로 만들어지지 않는 게 핵심 요구다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "Hi"),
                    ConversationTurn(TurnSpeaker.USER, "ok"),
                    ConversationTurn(TurnSpeaker.USER, "hello")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `trivial filter is case and punctuation insensitive`() {
        // 대소문자나 끝 구두점이 달라도 동일 인사말로 인식해야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "Hi!"),
                    ConversationTurn(TurnSpeaker.USER, "OK."),
                    ConversationTurn(TurnSpeaker.USER, "Thanks!")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `sub-min-length utterance is excluded but boundary case is kept`() {
        // 1글자는 최소 길이 미만으로 제외하고, 2글자는 보수적 기준에서 보존해야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "a"),    // 1글자 → 제외
                    ConversationTurn(TurnSpeaker.USER, "go")    // 2글자, trivial 아님 → 보존
                )
            )
        )

        // 보수적 기준: 교정 가치가 있을 수 있는 짧은 발화는 최대한 후보로 남긴다.
        assertEquals(1, result.size)
        assertEquals("go", result.first().sourceText)
    }

    @Test
    fun `non-trivial normal utterance is kept as candidate`() {
        // 정상 발화는 필터에 걸리지 않고 그대로 후보가 되어야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "I went to the store.")
                )
            )
        )

        assertEquals(1, result.size)
        assertEquals("I went to the store.", result.first().sourceText)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 장문 분할 (COR-TUNE-004)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `multi sentence utterance is split into separate candidates`() {
        // 여러 문장은 각 문장을 별도 후보로 만들어야 한다.
        // 분할된 후보들은 원본 turn 순서(sourceTurnIndex)를 공유해야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "I go to Paris. It was very fun.")
                )
            )
        )

        assertEquals(2, result.size)
        assertEquals("I go to Paris.", result[0].sourceText)
        assertEquals("It was very fun.", result[1].sourceText)
        // 분할 후보들은 모두 동일한 원본 turn 순서를 유지해야 한다.
        assertEquals(0, result[0].sourceTurnIndex)
        assertEquals(0, result[1].sourceTurnIndex)
    }

    @Test
    fun `trivial fragment inside long utterance is dropped`() {
        // 장문 속에 있는 사소한 조각(예: "Ok.")은 분할 후에도 후보에서 제외해야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "Ok. I went to Paris yesterday.")
                )
            )
        )

        assertEquals(1, result.size)
        assertEquals("I went to Paris yesterday.", result.first().sourceText)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 분할 후보 ID 형식 및 추적 정합성 (COR-TUNE-004)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `source text keeps filler utterance unchanged for downstream prompt cleanup`() {
        // COR-TUNE-012: filler ?뺣━??afterText ?앹꽦 ?뺤콉?먯꽌留?泥섎━?섍퀬 sourceText???먮Ц 洹몃?濡?蹂댁〈?쒕떎.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "I mean, you know, I was like really tired.")
                )
            )
        )

        assertEquals(1, result.size)
        assertEquals("I mean, you know, I was like really tired.", result.first().sourceText)
    }

    @Test
    fun `split candidates have unique ids with split index included`() {
        // 같은 원본 turn에서 나온 분할 후보들의 ID는 서로 달라야 하고,
        // splitIndex 를 포함하는 형식이어야 한다 — "en-{turnIdx}-{splitIdx}-{hash}".
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "First sentence. Second sentence.")
                )
            )
        )

        assertEquals(2, result.size)
        // splitIndex=0 → "en-0-0-..." / splitIndex=1 → "en-0-1-..."
        assertTrue(result[0].id.startsWith("en-0-0-"))
        assertTrue(result[1].id.startsWith("en-0-1-"))
        // 두 후보의 ID는 달라야 한다.
        assertFalse(result[0].id == result[1].id)
    }

    @Test
    fun `single sentence candidate id keeps lang-turnIndex prefix format`() {
        // 단일 문장(splitIndex=0)도 기존 prefix 형식("en-0-")을 유지해야 한다.
        // splitIndex 추가 후에도 기존 계약이 깨지지 않음을 명시적으로 검증한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "I need help with grammar")
                )
            )
        )

        assertEquals(1, result.size)
        // 단일 문장: splitIndex=0 → "en-0-0-<hash>"
        assertTrue(result.first().id.startsWith("en-0-0-"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 중복 제외 (COR-TUNE-004)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `duplicate normalized text across turns is kept only once`() {
        // 같은 텍스트를 두 번 말해도 후보는 한 번만 생성해야 한다(배치 전역 dedup).
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "I go to Paris yesterday."),
                    ConversationTurn(TurnSpeaker.USER, "I go to Paris yesterday.")
                )
            )
        )

        assertEquals(1, result.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 빈 상태 안전 처리 (COR-TUNE-004)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `all trivial utterances produce empty candidate list safely`() {
        // 모든 발화가 필터링되어도 예외 없이 빈 리스트를 반환해야 한다.
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "Hi"),
                    ConversationTurn(TurnSpeaker.USER, "ok"),
                    ConversationTurn(TurnSpeaker.USER, "bye")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // candidateId 결정성 (COR-FIX-009)
    //
    // "unknown correction candidate id: ja-6-0-79967d5" 실패의 원인 후보 중 하나가
    // buildCandidateId() 의 비결정성(같은 입력인데 매 호출마다 다른 id)이었다.
    // 같은 입력 → 항상 같은 id 임을 일본어 포함으로 못 박아 회귀를 방지한다.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `same input produces identical candidate ids on repeated invocation`() {
        // 같은 input 객체를 두 번 호출해도 id·순서·sourceTurnIndex 가 완전히 같아야 한다.
        // AI 가 prompt 의 Candidates 섹션과 mapper 시점의 후보 목록을 같은 id 로 매칭하려면
        // 이 안정성이 buildCandidateId() 단에서부터 보장되어야 한다.
        val input = ExtractCandidatesInput(
            selectedLang = LangCode.EN,
            sessionLang = LangCode.EN,
            recentFullContext = listOf(
                ConversationTurn(TurnSpeaker.AI, "What did you do yesterday?"),
                ConversationTurn(TurnSpeaker.USER, "I go to Paris yesterday. It was very fun.")
            )
        )

        val first = useCase(input)
        val second = useCase(input)

        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(first.map { it.sourceTurnIndex }, second.map { it.sourceTurnIndex })
        assertEquals(first.map { it.sourceText }, second.map { it.sourceText })
    }

    @Test
    fun `japanese input produces stable ja-prefixed deterministic candidate ids`() {
        // 일본어 후보도 영어와 동일한 lang-turnIndex-splitIndex-hash 계약을 따라야 하고,
        // 같은 입력을 반복 호출해도 같은 id 가 나와야 한다(실제 실패 사례의 lang=ja 재현).
        val input = ExtractCandidatesInput(
            selectedLang = LangCode.JA,
            sessionLang = LangCode.JA,
            recentFullContext = listOf(
                ConversationTurn(TurnSpeaker.USER, "わたしは昨日学校に行きました。とても楽しかったです。")
            )
        )

        val first = useCase(input)
        val second = useCase(input)

        assertEquals(2, first.size)
        assertTrue(first[0].id.startsWith("ja-0-0-"))
        assertTrue(first[1].id.startsWith("ja-0-1-"))
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `leading and trailing whitespace around a sentence does not change its candidate id hash`() {
        // splitIntoSentences()/normalize() 가 trim 외의 변형(예: 내부 공백 정규화)을 sourceText 자체에
        // 적용하면 hashCode 가 달라져 candidateId 가 흔들릴 수 있다. 문장 앞뒤 공백만 다른 발화도
        // 같은 candidateId 를 내야 한다 — sourceText 는 trim 된 형태로 hash 에 들어가기 때문이다.
        val baseline = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.JA,
                sessionLang = LangCode.JA,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "わたしは学校に行きます。")
                )
            )
        )
        val padded = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.JA,
                sessionLang = LangCode.JA,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "  わたしは学校に行きます。  ")
                )
            )
        )

        assertEquals(1, baseline.size)
        assertEquals(1, padded.size)
        assertEquals(baseline.first().sourceText, padded.first().sourceText)
        assertEquals(baseline.first().id, padded.first().id)
    }
}
