package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.CorrectionImprovementType
import com.app.umma.domain.model.learningstate.CorrectionIssueCategory
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SpokenRegister
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CorrectionAiResponseMapperTest {

    private val mapper = CorrectionAiResponseMapper()

    @Test
    fun `maps valid AI response into correction suggestions`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "Use 'go to school' instead of 'go school'."
                }
              ]
            }
        """.trimIndent()

        val suggestions = mapper.map(rawJson, baseInput())

        assertEquals(1, suggestions.size)
        val suggestion = suggestions.single()
        assertEquals("corr-en-0-a", suggestion.id)
        assertEquals(LangCode.EN, suggestion.lang)
        assertEquals(listOf("en-0-a"), suggestion.sourceCandidateIds)
        assertEquals(0, suggestion.sourceTurnIndex)
        assertEquals("i go school", suggestion.beforeText)
        assertEquals("나는 학교에 간다", suggestion.nativeText)
        assertEquals("I go to school.", suggestion.afterText)
        assertEquals("Use 'go to school' instead of 'go school'.", suggestion.explanation)
    }

    @Test
    fun `returns empty list when AI response has no suggestions`() {
        val suggestions = mapper.map("""{"suggestions":[]}""", baseInput())

        assertEquals(emptyList<Any>(), suggestions)
    }

    @Test
    fun `fails when AI response references unknown candidate`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "missing",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "demo"
                }
              ]
            }
        """.trimIndent()

        assertThrowsIllegalArgument {
            mapper.map(rawJson, baseInput())
        }
    }

    @Test
    fun `fails when required text field is blank`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "   ",
                  "explanation": "demo"
                }
              ]
            }
        """.trimIndent()

        assertThrowsIllegalArgument {
            mapper.map(rawJson, baseInput())
        }
    }

    // --- COR-TUNE-02: learning signal 정규화 ---

    @Test
    fun `maps learning signal and trusts candidate passthrough over AI values`() {
        // sourceTurnId/sourceTurnIndex/sourceText/correctedText 는 AI 값이 아니라 신뢰 출처에서 채워야 한다.
        // AI 가 일부러 다른 값을 보내도 mapper 는 candidate/afterText 를 신뢰한다.
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 어제 친구를 만났어",
                  "afterText": "I met my friend yesterday.",
                  "explanation": "시제 정리",
                  "learningSignal": {
                    "candidateId": "AI-WRONG",
                    "sourceTurnId": "AI-WRONG-TURN",
                    "sourceTurnIndex": 99,
                    "sourceText": "AI-WRONG-SOURCE",
                    "correctedText": "AI-WRONG-CORRECTED",
                    "issueCategories": ["GrammarForm", "MissingContext"],
                    "languageFeatures": [{"lang":"EN","featureKey":"EN.Tense"}],
                    "improvementTypes": ["GrammarFixed"],
                    "editSpans": [
                      {"sourceFragment":"meet","correctedFragment":"met","issueCategory":"GrammarForm","languageFeatureKey":"EN.Tense","improvementType":"GrammarFixed"}
                    ],
                    "register": "EverydaySpoken",
                    "severity": "MajorPattern",
                    "meaningPreserved": true,
                    "confidence": 0.82
                  }
                }
              ]
            }
        """.trimIndent()

        val candidate = CorrectionCandidate(
            id = "en-0-a",
            lang = LangCode.EN,
            sourceTurnId = "cand-turn-7",
            sourceTurnIndex = 7,
            sourceText = "I meet friend yesterday"
        )

        val signal = mapper.map(rawJson, inputOf(candidate)).single().learningSignal
        assertNotNull(signal)
        requireNotNull(signal)
        // 전달 데이터는 신뢰 출처 기준.
        assertEquals("en-0-a", signal.candidateId)
        assertEquals("cand-turn-7", signal.sourceTurnId)
        assertEquals(7, signal.sourceTurnIndex)
        assertEquals("I meet friend yesterday", signal.sourceText)
        assertEquals("I met my friend yesterday.", signal.correctedText)
        // AI 분석 필드.
        assertEquals(
            listOf(CorrectionIssueCategory.GrammarForm, CorrectionIssueCategory.MissingContext),
            signal.issueCategories
        )
        assertEquals(listOf("EN.Tense"), signal.languageFeatures.map { it.featureKey })
        assertEquals(LangCode.EN, signal.languageFeatures.single().lang)
        assertEquals(listOf(CorrectionImprovementType.GrammarFixed), signal.improvementTypes)
        assertEquals(1, signal.editSpans.size)
        assertEquals(SpokenRegister.EverydaySpoken, signal.register)
        assertEquals(CorrectionSeverity.MajorPattern, signal.severity)
        assertEquals(true, signal.meaningPreserved)
        assertEquals(0.82, signal.confidence!!, 0.0001)
    }

    @Test
    fun `drops signal but keeps suggestion when register is unknown`() {
        val suggestion = mapper.map(signalJson(register = "Mysterious"), baseInput()).single()
        // 핵심 4필드 흐름은 살아 있어야 한다.
        assertEquals("I go to school.", suggestion.afterText)
        // unknown register → signal 전체 drop.
        assertNull(suggestion.learningSignal)
    }

    @Test
    fun `drops signal but keeps suggestion when severity is unknown`() {
        val suggestion = mapper.map(signalJson(severity = "Catastrophic"), baseInput()).single()
        assertEquals("I go to school.", suggestion.afterText)
        assertNull(suggestion.learningSignal)
    }

    @Test
    fun `drops signal when confidence is out of range`() {
        val suggestion = mapper.map(signalJson(confidence = "1.5"), baseInput()).single()
        assertNull(suggestion.learningSignal)
    }

    @Test
    fun `allows null confidence and keeps signal`() {
        // confidence 키를 생략하면 null 로 허용하고 signal 은 유지한다.
        val suggestion = mapper.map(signalJson(confidence = null), baseInput()).single()
        val signal = suggestion.learningSignal
        assertNotNull(signal)
        assertNull(signal!!.confidence)
    }

    @Test
    fun `excludes unknown issue category element but keeps signal`() {
        val suggestion = mapper.map(
            signalJson(issueCategories = """["GrammarForm","NotAReal","WordOrder"]"""),
            baseInput()
        ).single()
        val signal = requireNotNull(suggestion.learningSignal)
        // unknown 원소만 제외하고 알려진 값은 보존한다.
        assertEquals(
            listOf(CorrectionIssueCategory.GrammarForm, CorrectionIssueCategory.WordOrder),
            signal.issueCategories
        )
    }

    @Test
    fun `caps issue categories to three`() {
        val suggestion = mapper.map(
            signalJson(issueCategories = """["GrammarForm","WordOrder","VocabularyChoice","Collocation"]"""),
            baseInput()
        ).single()
        val signal = requireNotNull(suggestion.learningSignal)
        assertEquals(3, signal.issueCategories.size)
    }

    @Test
    fun `excludes feature with unknown featureKey but keeps signal`() {
        val suggestion = mapper.map(
            signalJson(languageFeatures = """[{"lang":"EN","featureKey":"EN.Bogus"}]"""),
            baseInput()
        ).single()
        val signal = requireNotNull(suggestion.learningSignal)
        // allowlist 밖 featureKey 는 그 feature 만 제외하고 signal 은 유지한다.
        assertTrue(signal.languageFeatures.isEmpty())
    }

    @Test
    fun `excludes feature when lang does not match selected lang`() {
        val suggestion = mapper.map(
            signalJson(languageFeatures = """[{"lang":"JA","featureKey":"EN.Tense"}]"""),
            baseInput()
        ).single()
        val signal = requireNotNull(suggestion.learningSignal)
        assertTrue(signal.languageFeatures.isEmpty())
    }

    @Test
    fun `keeps suggestion with null signal when learning signal is absent`() {
        // learningSignal 누락은 실패가 아니다. suggestion.learningSignal=null 로 통과한다.
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "demo"
                }
              ]
            }
        """.trimIndent()

        val suggestion = mapper.map(rawJson, baseInput()).single()
        assertNull(suggestion.learningSignal)
    }

    /**
     * 핵심 4필드는 고정하고 learningSignal 의 한 부분만 바꿔 가며 정규화를 검증하기 위한 JSON 빌더.
     * confidence=null 이면 키 자체를 생략해 "누락" 상황을 만든다.
     */
    private fun signalJson(
        issueCategories: String = """["GrammarForm"]""",
        languageFeatures: String = """[{"lang":"EN","featureKey":"EN.Tense"}]""",
        improvementTypes: String = """["GrammarFixed"]""",
        editSpans: String = "[]",
        register: String = "EverydaySpoken",
        severity: String = "MajorPattern",
        meaningPreserved: String = "true",
        confidence: String? = "0.8"
    ): String {
        val confidenceLine = if (confidence == null) "" else ""","confidence":$confidence"""
        return """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "demo",
                  "learningSignal": {
                    "issueCategories": $issueCategories,
                    "languageFeatures": $languageFeatures,
                    "improvementTypes": $improvementTypes,
                    "editSpans": $editSpans,
                    "register": "$register",
                    "severity": "$severity",
                    "meaningPreserved": $meaningPreserved$confidenceLine
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun inputOf(candidate: CorrectionCandidate): GenerateSuggestionsInput {
        return GenerateSuggestionsInput(
            candidates = listOf(candidate),
            langState = LangState.initial(candidate.lang),
            primaryLang = LangCode.KO,
            profile = BuildLearnerAdaptationProfileUseCase()(LangState.initial(candidate.lang))
        )
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected path: invalid AI response should fail before reaching the domain layer.
        }
    }

    private fun baseInput(): GenerateSuggestionsInput {
        return GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            ),
            langState = LangState.initial(LangCode.EN),
            primaryLang = LangCode.KO,
            profile = BuildLearnerAdaptationProfileUseCase()(LangState.initial(LangCode.EN))
        )
    }
}
