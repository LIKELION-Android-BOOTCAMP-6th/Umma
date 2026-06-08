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
import kotlinx.serialization.SerializationException
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

    // --- COR-FIX-008-A: 최상위 JSON 배열 허용 ---

    @Test
    fun `maps top-level array response into correction suggestions`() {
        val rawJson = """
            [
              {
                "candidateId": "en-0-a",
                "nativeText": "나는 학교에 간다",
                "afterText": "I go to school.",
                "explanation": "Use 'go to school' instead of 'go school'."
              }
            ]
        """.trimIndent()

        val suggestions = mapper.map(rawJson, baseInput())

        assertEquals(1, suggestions.size)
        val suggestion = suggestions.single()
        assertEquals("corr-en-0-a", suggestion.id)
        assertEquals("I go to school.", suggestion.afterText)
    }

    @Test
    fun `fails when top-level array references unknown candidate`() {
        val rawJson = """
            [
              {
                "candidateId": "missing",
                "nativeText": "나는 학교에 간다",
                "afterText": "I go to school.",
                "explanation": "demo"
              }
            ]
        """.trimIndent()

        assertThrowsIllegalArgument {
            mapper.map(rawJson, baseInput())
        }
    }

    @Test
    fun `fails when top-level array contains blank required field`() {
        val rawJson = """
            [
              {
                "candidateId": "en-0-a",
                "nativeText": "나는 학교에 간다",
                "afterText": "   ",
                "explanation": "demo"
              }
            ]
        """.trimIndent()

        assertThrowsIllegalArgument {
            mapper.map(rawJson, baseInput())
        }
    }

    @Test
    fun `fails when top-level JSON is neither object nor array`() {
        assertThrowsIllegalArgument {
            mapper.map("\"just a string\"", baseInput())
        }
    }

    // --- COR-FIX-008-C/D: explanation 누락-공백 정책(재시도 후 drop), malformed JSON ---

    @Test
    fun `throws BlankExplanationException when explanation is missing in default mode`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school."
                }
              ]
            }
        """.trimIndent()

        assertThrowsType<BlankExplanationException> {
            mapper.map(rawJson, baseInput())
        }
    }

    @Test
    fun `throws BlankExplanationException when explanation is blank in default mode`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "   "
                }
              ]
            }
        """.trimIndent()

        assertThrowsType<BlankExplanationException> {
            mapper.map(rawJson, baseInput())
        }
    }

    @Test
    fun `drops suggestion with blank explanation when dropBlankExplanation is enabled`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school."
                }
              ]
            }
        """.trimIndent()

        val suggestions = mapper.map(rawJson, baseInput(), dropBlankExplanation = true)

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `fails with SerializationException when a token sits outside JSON string values`() {
        val rawJson = """{ "suggestions": [ { "candidateId": "en-0-a", "nativeText": "나는 학교에 간다", "afterText": "I go to school.", "explanation": "tip" X} ] }"""

        assertThrowsType<SerializationException> {
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
    fun `drops signal when issue category element is unknown`() {
        val suggestion = mapper.map(
            signalJson(issueCategories = """["GrammarForm","NotAReal","WordOrder"]"""),
            baseInput()
        ).single()
        // unknown enum 원소가 하나라도 있으면 부분 제외하지 않고 signal 전체를 drop 한다.
        assertEquals("I go to school.", suggestion.afterText)
        assertNull(suggestion.learningSignal)
    }

    @Test
    fun `drops signal when improvement type element is unknown`() {
        val suggestion = mapper.map(
            signalJson(improvementTypes = """["GrammarFixed","NotReal"]"""),
            baseInput()
        ).single()
        assertEquals("I go to school.", suggestion.afterText)
        assertNull(suggestion.learningSignal)
    }

    @Test
    fun `drops signal when edit span enum is unknown`() {
        // editSpan 의 issueCategory/improvementType 이 unknown 이면 그 span 만 빼지 않고 signal 전체를 drop 한다.
        val suggestion = mapper.map(
            signalJson(
                editSpans = """[{"sourceFragment":"go","correctedFragment":"go to","issueCategory":"NotReal","languageFeatureKey":"EN.Tense","improvementType":"GrammarFixed"}]"""
            ),
            baseInput()
        ).single()
        assertEquals("I go to school.", suggestion.afterText)
        assertNull(suggestion.learningSignal)
    }

    @Test
    fun `drops signal when meaningPreserved is missing`() {
        // meaningPreserved 누락은 의미 보존이 검증되지 않은 것이므로 signal 전체를 drop 한다.
        val suggestion = mapper.map(signalJson(meaningPreserved = null), baseInput()).single()
        assertEquals("I go to school.", suggestion.afterText)
        assertNull(suggestion.learningSignal)
    }

    @Test
    fun `keeps signal with meaningPreserved false`() {
        // false 는 "의미 변형"을 알리는 중요한 관찰값이므로 유지한다.
        val suggestion = mapper.map(signalJson(meaningPreserved = "false"), baseInput()).single()
        val signal = requireNotNull(suggestion.learningSignal)
        assertEquals(false, signal.meaningPreserved)
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

    // --- COR-FIX-008-B: languageFeatures 문자열/이상타입 정규화 (allowlist 확장 없음) ---

    @Test
    fun `normalizes string languageFeature entry within allowlist`() {
        val suggestion = mapper.map(
            signalJson(languageFeatures = """["EN.Tense"]"""),
            baseInput()
        ).single()
        val signal = requireNotNull(suggestion.learningSignal)
        assertEquals(listOf("EN.Tense"), signal.languageFeatures.map { it.featureKey })
        assertEquals(LangCode.EN, signal.languageFeatures.single().lang)
    }

    @Test
    fun `excludes string languageFeature entry outside allowlist but keeps suggestion and signal`() {
        val suggestion = mapper.map(
            signalJson(languageFeatures = """["EN.NounPhrase"]"""),
            baseInput()
        ).single()
        // 파싱 견고성과 allowlist 멤버십은 별개다 — 문자열 배열도 throw 없이 정규화되고,
        // allowlist 밖 feature 만 제외되며 suggestion·signal 은 그대로 유지된다.
        assertEquals("I go to school.", suggestion.afterText)
        val signal = requireNotNull(suggestion.learningSignal)
        assertTrue(signal.languageFeatures.isEmpty())
    }

    @Test
    fun `keeps only valid entries when languageFeatures mixes strings numbers and objects`() {
        val suggestion = mapper.map(
            signalJson(languageFeatures = """["EN.Tense", 123, {"lang":"EN","featureKey":"EN.Article"}]"""),
            baseInput()
        ).single()
        assertEquals("I go to school.", suggestion.afterText)
        val signal = requireNotNull(suggestion.learningSignal)
        assertEquals(
            setOf("EN.Tense", "EN.Article"),
            signal.languageFeatures.map { it.featureKey }.toSet()
        )
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

    // --- COR-TUNE-011-FIX (Method B): AI 가 보고하는 발화 원문 언어(sourceLang) 보수적 파싱 ---
    // detectedLang seam 폐기로 sourceLang 의 유일한 출처가 AI 응답이 되었다. mapper 는 신뢰 데이터가
    // 아닌 AI 분석값으로 보고 "확실히 인식되는 ISO 코드만" 살리고 나머지는 모두 null(=불명, 게이트 통과)로
    // 떨어뜨린다 — 과제외로 정상 학습 신호를 잃지 않는 null=통과 원칙(COR-TUNE-002-FIX 와 동일 결).

    @Test
    fun `parses AI-reported sourceLang into matching LangCode`() {
        val suggestion = mapper.map(rawJsonWithSourceLang("ko"), baseInput()).single()
        assertEquals(LangCode.KO, suggestion.sourceLang)
    }

    @Test
    fun `parses another supported sourceLang code reported by AI`() {
        val suggestion = mapper.map(rawJsonWithSourceLang("en"), baseInput()).single()
        assertEquals(LangCode.EN, suggestion.sourceLang)
    }

    @Test
    fun `treats missing sourceLang from AI as null (unknown, evaluation gate passes)`() {
        val suggestion = mapper.map(rawJsonWithSourceLang(null), baseInput()).single()
        assertNull(suggestion.sourceLang)
    }

    @Test
    fun `treats AI-reported unknown sourceLang as null, not LangCode_UNKNOWN`() {
        // "unknown" 은 AI 가 확신이 없을 때 쓰라고 프롬프트가 지시한 escape hatch 값이다.
        // LangCode.UNKNOWN 을 그대로 두면 게이트가 selectedLang 과 "다름"으로 오판해 잘못 제외하므로,
        // 반드시 null 로 정규화해 "불명 → 평가에 반영(통과)"이 유지되어야 한다.
        val suggestion = mapper.map(rawJsonWithSourceLang("unknown"), baseInput()).single()
        assertNull(suggestion.sourceLang)
    }

    @Test
    fun `treats unsupported sourceLang code from AI as null (conservative)`() {
        val suggestion = mapper.map(rawJsonWithSourceLang("xx"), baseInput()).single()
        assertNull(suggestion.sourceLang)
    }

    @Test
    fun `trims surrounding whitespace before parsing AI-reported sourceLang`() {
        val suggestion = mapper.map(rawJsonWithSourceLang(" ko "), baseInput()).single()
        assertEquals(LangCode.KO, suggestion.sourceLang)
    }

    @Test
    fun `existing fixtures without sourceLang key still map with null sourceLang (no regression)`() {
        // signalJson/baseInput 은 sourceLang 키를 내려보내지 않는다 — DTO 기본값(null)으로 통과해야 한다.
        val suggestion = mapper.map(signalJson(), baseInput()).single()
        assertNull(suggestion.sourceLang)
    }

    /**
     * 핵심 4필드는 고정하고 sourceLang 만 바꿔 가며 AI 보고값 정규화를 검증하기 위한 JSON 빌더.
     * sourceLang 이 null 이면 키 자체를 생략해 "AI 가 누락한" 상황을 만든다.
     */
    private fun rawJsonWithSourceLang(sourceLang: String?): String {
        val sourceLangField = if (sourceLang == null) "" else ",\"sourceLang\":\"$sourceLang\""
        return """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school."$sourceLangField,
                  "explanation": "demo"
                }
              ]
            }
        """.trimIndent()
    }

    /**
     * 핵심 4필드는 고정하고 learningSignal 의 한 부분만 바꿔 가며 정규화를 검증하기 위한 JSON 빌더.
     * meaningPreserved/confidence 는 null 이면 키 자체를 생략해 "누락" 상황을 만든다.
     */
    private fun signalJson(
        issueCategories: String = """["GrammarForm"]""",
        languageFeatures: String = """[{"lang":"EN","featureKey":"EN.Tense"}]""",
        improvementTypes: String = """["GrammarFixed"]""",
        editSpans: String = "[]",
        register: String = "EverydaySpoken",
        severity: String = "MajorPattern",
        meaningPreserved: String? = "true",
        confidence: String? = "0.8"
    ): String {
        val meaningPreservedLine = if (meaningPreserved == null) "" else ""","meaningPreserved":$meaningPreserved"""
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
                    "severity": "$severity"$meaningPreservedLine$confidenceLine
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

    /**
     * COR-FIX-008: BlankExplanationException/SerializationException 처럼 서로 다른 계층에 속한
     * 예외를 정확한 타입으로 검증하기 위한 범용 helper. (SerializationException 은
     * IllegalArgumentException 의 하위 타입이라 위 helper 로는 구분할 수 없다)
     */
    private inline fun <reified T : Throwable> assertThrowsType(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) {
                fail("Expected ${T::class.simpleName} but was ${e::class.simpleName}: ${e.message}")
            }
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
