package com.example.umma.data.repository.correction

import com.example.umma.domain.model.correction.CorrectionCandidate
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MVP 프롬프트 회귀 테스트.
 *
 * Mapper 와 한 디렉토리에 있는 이유는, 프롬프트 schema 가 [CorrectionAiResponseMapper] 가 받는 DTO 와
 * 한 글자라도 어긋나면 happy path 가 통째로 깨지기 때문이다. 두 파일의 schema 일치를 이 테스트가 못박는다.
 */
class CorrectionPromptBuilderTest {

    private val builder = CorrectionPromptBuilder()

    @Test
    fun `prompt includes every candidate id and source text`() {
        val candidates = listOf(
            CorrectionCandidate(
                id = "en-0-abc",
                lang = LangCode.EN,
                sourceTurnIndex = 0,
                sourceText = "i go school"
            ),
            CorrectionCandidate(
                id = "en-2-def",
                lang = LangCode.EN,
                sourceTurnIndex = 2,
                sourceText = "she don't like coffee",
                assistantContext = "We were talking about cafes."
            )
        )
        val input = GenerateSuggestionsInput(
            candidates = candidates,
            langState = LangState.initial(LangCode.EN)
        )

        val prompt = builder.build(input)

        assertTrue("candidateId en-0-abc 누락", prompt.contains("en-0-abc"))
        assertTrue("candidateId en-2-def 누락", prompt.contains("en-2-def"))
        assertTrue("sourceText 누락", prompt.contains("i go school"))
        assertTrue("sourceText 누락", prompt.contains("she don't like coffee"))
        assertTrue("assistantContext 누락", prompt.contains("We were talking about cafes."))
    }

    @Test
    fun `prompt declares the exact JSON schema keys mapper expects`() {
        // 이 키 셋이 깨지면 CorrectionAiResponseMapper 의 @Serializable DTO 와 어긋나 happy path 가 무너진다.
        val input = GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            ),
            langState = LangState.initial(LangCode.EN)
        )

        val prompt = builder.build(input)

        listOf("suggestions", "candidateId", "nativeText", "afterText", "explanation").forEach { key ->
            assertTrue("schema key '$key' 누락", prompt.contains(key))
        }
    }

    @Test
    fun `prompt embeds learner cefr level so model can calibrate difficulty`() {
        val input = GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hello"
                )
            ),
            langState = LangState.initial(LangCode.EN)
        )

        val prompt = builder.build(input)

        // LangState.initial 의 기본 vocabularyLevel 은 A1.
        assertTrue("CEFR level (A1) 누락", prompt.contains("A1"))
    }

    @Test
    fun `prompt omits assistantContext line when null or blank`() {
        // 빈 context 가 그대로 들어가면 토큰 낭비 + 모델 혼란.
        val input = GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hi",
                    assistantContext = null
                )
            ),
            langState = LangState.initial(LangCode.EN)
        )

        val prompt = builder.build(input)

        assertFalse("null context 가 라벨로 노출됨", prompt.contains("assistantContext:"))
    }

    @Test
    fun `prompt forbids markdown fences so mapper json decode survives`() {
        // mapper 는 markdown fence 가 섞이면 json decode 단계에서 즉시 깨진다.
        val input = GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hi"
                )
            ),
            langState = LangState.initial(LangCode.EN)
        )

        val prompt = builder.build(input)

        assertTrue("markdown 금지 지시 누락", prompt.contains("no markdown"))
    }
}
