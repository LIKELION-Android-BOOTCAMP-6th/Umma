package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionEditSpan
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.CorrectionImprovementType
import com.app.umma.domain.model.learningstate.CorrectionIssueCategory
import com.app.umma.domain.model.learningstate.CorrectionLearningSignal
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LanguageFeatureSignal
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpokenRegister
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.VocabLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COR-TUNE-006: 교정 결과 과확장 런타임 가드 단위 테스트.
 *
 * 길이비 위반·새 표현 수 위반·meaningPreserved=false 각각의 drop 동작과,
 * 정상 교정(동일·더 짧은)이 통과되는지, 공백 없는 언어(JA) 글자 수 측정이 올바른지 확인한다.
 * 기존 CorrectionAiResponseMapperTest(COR-TUNE-002-FIX) 회귀와 무관하게 독립적으로 실행된다.
 */
class CorrectionOverexpansionGuardTest {

    private val guard = CorrectionOverexpansionGuard()

    // ──────────────────────────────────────────────────────────────────────────
    // 길이비 위반 — drop
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `NoExpansion 정책에서 길이비가 상한을 초과하면 해당 suggestion이 drop된다`() {
        // NoExpansion 상한 1.5. "hi" (1단어) → 7단어 = ratio 7.0 → drop.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(beforeText = "hi", afterText = "Hi there, how are you doing today")
        )

        val result = guard.filter(suggestions, input)

        assertTrue("NoExpansion 길이 위반 suggestion이 남아 있음", result.isEmpty())
    }

    @Test
    fun `TinyPhraseOnly 정책에서 길이비가 상한을 초과하면 drop된다`() {
        // TinyPhraseOnly 상한 2.0. "go school" (2단어) → 5단어 = ratio 2.5 → drop.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.PatternFix))
        val suggestions = listOf(
            suggestion(beforeText = "go school", afterText = "I go to school every day")
        )

        val result = guard.filter(suggestions, input)

        assertTrue("TinyPhraseOnly 길이 위반 suggestion이 남아 있음", result.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 정상 교정 — 통과
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `더 짧아진 교정은 정책에 관계없이 통과한다`() {
        // ratio ≤ 1.0 → 항상 통과.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(
                beforeText = "I go to school every single day without fail",
                afterText = "I go to school every day"
            )
        )

        val result = guard.filter(suggestions, input)

        assertEquals("더 짧아진 교정이 drop됨", 1, result.size)
    }

    @Test
    fun `길이 비율이 상한 이내이면 통과한다`() {
        // NoExpansion 상한 1.5. "i go school" (3단어) → "I go to school." (4단어) = ratio ≈ 1.33 → 통과.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(beforeText = "i go school", afterText = "I go to school.")
        )

        val result = guard.filter(suggestions, input)

        assertEquals("ratio 1.33은 NoExpansion 상한(1.5) 이내인데 drop됨", 1, result.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // meaningPreserved — drop / 통과
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `meaningPreserved=false이면 길이와 무관하게 drop된다`() {
        // 길이는 정상(ratio 1.33)이지만 의미가 바뀐 경우.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(
                beforeText = "i go school",
                afterText = "I go to school.",
                learningSignal = signalWith(meaningPreserved = false)
            )
        )

        val result = guard.filter(suggestions, input)

        assertTrue("meaningPreserved=false인데 drop되지 않음", result.isEmpty())
    }

    @Test
    fun `meaningPreserved=true이고 길이 위반 없으면 통과한다`() {
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(
                beforeText = "i go school",
                afterText = "I go to school.",
                learningSignal = signalWith(meaningPreserved = true)
            )
        )

        val result = guard.filter(suggestions, input)

        assertEquals("meaningPreserved=true, 길이 정상인데 drop됨", 1, result.size)
    }

    @Test
    fun `learningSignal이 null이면 meaningPreserved 검사를 건너뛰고 길이만 본다`() {
        // signal 없음(learningSignal=null) + 길이 정상 → 통과.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(
                beforeText = "i go school",
                afterText = "I go to school.",
                learningSignal = null
            )
        )

        val result = guard.filter(suggestions, input)

        assertEquals("signal 없고 길이 정상인데 drop됨", 1, result.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 일본어 — 글자 수 기준 측정
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `일본어 과확장은 글자 수 기준으로 감지된다`() {
        // JA: 공백 없음 → 글자(코드포인트) 수로 측정.
        // "学校に行く" = 6글자. 과도하게 긴 문장 = ratio >> 1.5 → drop.
        val input = inputWith(
            policy = CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst),
            lang = LangCode.JA
        )
        val suggestions = listOf(
            suggestion(
                beforeText = "学校に行く",
                afterText = "毎朝元気よく学校に行って友達とたくさん楽しく話しながら過ごしています",
                lang = LangCode.JA
            )
        )

        val result = guard.filter(suggestions, input)

        assertTrue("JA 글자 수 기준 과확장이 감지되지 않음", result.isEmpty())
    }

    @Test
    fun `일본어 공백 없는 원문에서 단어 수 기준으로는 감지 불가인 케이스를 글자 수로 잡는다`() {
        // JA는 공백이 없어 split 하면 전부 "1단어". 단어 수만 쓰면 ratio = 1/1 = 1.0 → 통과(오감지).
        // 글자 수 기준이면 ratio = 28/5 = 5.6 → drop(정확).
        val input = inputWith(
            policy = CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst),
            lang = LangCode.JA
        )
        val before = "行く" // 2글자
        val after = "毎朝早起きして元気よく学校に行って友達と話す" // 21글자, ratio ≈ 10.5
        val suggestions = listOf(
            suggestion(beforeText = before, afterText = after, lang = LangCode.JA)
        )

        val result = guard.filter(suggestions, input)

        assertTrue("JA 단어 수 오감지 케이스를 글자 수로 잡지 못함", result.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 새 표현 수 (보조 신호)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `None 정책에서 신규 토큰이 상한을 초과하면 drop된다`() {
        // None 정책(maxNewTokens=3). 길이비가 상한(1.5) 이내이지만 신규 토큰이 4개 → drop.
        // before 10단어, after 14단어 → ratio 1.4 < 1.5 → 길이 통과.
        // 신규 토큰 "brand", "new", "fresh", "ideas" = 4 > 3 → 토큰 검사에서 drop.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(
                beforeText = "This sentence has a lot of words and context",
                afterText = "This sentence has a lot of words and context with brand new fresh ideas"
            )
        )

        val result = guard.filter(suggestions, input)

        assertTrue("새 표현 수 위반(4 > 3)이 감지되지 않음", result.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 혼합 — 위반 / 정상 suggestion 공존
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `위반 suggestion만 drop되고 정상 suggestion은 유지된다`() {
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val good = suggestion(id = "good", beforeText = "i go school", afterText = "I go to school.")
        val bad = suggestion(id = "bad", beforeText = "hi", afterText = "Hi there, how are you doing today")

        val result = guard.filter(listOf(good, bad), input)

        assertEquals("정상 suggestion이 남지 않음", 1, result.size)
        assertEquals("위반 아닌 suggestion이 drop됨", "good", result.single().id)
    }

    @Test
    fun `위반 없으면 전체 목록이 그대로 반환된다`() {
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        val suggestions = listOf(
            suggestion(id = "s1", beforeText = "i go school", afterText = "I go to school."),
            suggestion(id = "s2", beforeText = "she like coffee", afterText = "She likes coffee.")
        )

        val result = guard.filter(suggestions, input)

        assertEquals("위반 없는데 suggestion이 줄어듦", 2, result.size)
    }

    @Test
    fun `빈 목록 입력은 빈 목록을 반환한다`() {
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))

        val result = guard.filter(emptyList(), input)

        assertTrue(result.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // COR-TUNE-002-FIX 회귀 무손상 확인
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `CorrectionAiResponseMapper 계약(핵심 4필드)은 이 가드와 독립적으로 유지된다`() {
        // 가드는 CorrectionSuggestion 목록을 받아 필터링만 하고, mapper·DTO·learningSignal 계약을 변경하지 않는다.
        // 기존 mapper 테스트에서 쓰던 suggestion 모양(beforeText=candidateSourceText, learningSignal nullable)이
        // 가드를 통과해야 mapper 회귀 무손상이 유지된다.
        val input = inputWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.EverydayNatural))
        val suggestions = listOf(
            suggestion(
                beforeText = "i go school",
                afterText = "I go to school.",    // mapper happy-path 교정문
                learningSignal = null              // mapper: signal 누락은 null (정상)
            )
        )

        val result = guard.filter(suggestions, input)

        // 가드가 mapper 기존 테스트 케이스를 방해하지 않는다.
        assertEquals("mapper 회귀 케이스가 가드에서 drop됨", 1, result.size)
        assertEquals("I go to school.", result.single().afterText)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** 정책을 지정한 테스트용 GenerateSuggestionsInput. */
    private fun inputWith(
        policy: CorrectionGrowthPolicy,
        lang: LangCode = LangCode.EN
    ): GenerateSuggestionsInput = GenerateSuggestionsInput(
        candidates = listOf(
            CorrectionCandidate(id = "test-1", lang = lang, sourceTurnIndex = 0, sourceText = "test")
        ),
        langState = LangState.initial(lang),
        primaryLang = LangCode.KO,
        profile = profileWith(policy)
    )

    /**
     * 테스트용 CorrectionSuggestion.
     * beforeText/afterText/lang/learningSignal 만 검사에 영향을 주므로 나머지 필드는 고정값을 쓴다.
     */
    private fun suggestion(
        beforeText: String,
        afterText: String,
        id: String = "corr-test-1",
        lang: LangCode = LangCode.EN,
        learningSignal: CorrectionLearningSignal? = null
    ): CorrectionSuggestion = CorrectionSuggestion(
        id = id,
        lang = lang,
        sourceCandidateIds = listOf("test-1"),
        sourceTurnIndex = 0,
        beforeText = beforeText,
        nativeText = "테스트",
        afterText = afterText,
        explanation = "test explanation",
        learningSignal = learningSignal
    )

    /** meaningPreserved 값만 지정한 최소 유효 CorrectionLearningSignal. */
    private fun signalWith(meaningPreserved: Boolean): CorrectionLearningSignal =
        CorrectionLearningSignal(
            candidateId = "test-1",
            sourceTurnId = null,
            sourceTurnIndex = 0,
            sourceText = "test",
            correctedText = "corrected",
            issueCategories = listOf(CorrectionIssueCategory.GrammarForm),
            languageFeatures = emptyList<LanguageFeatureSignal>(),
            improvementTypes = listOf(CorrectionImprovementType.GrammarFixed),
            editSpans = emptyList<CorrectionEditSpan>(),
            register = SpokenRegister.Simple,
            severity = CorrectionSeverity.MinorForm,
            meaningPreserved = meaningPreserved,
            confidence = null
        )

    /** correctionPolicy만 달리한 LearnerAdaptationProfile. */
    private fun profileWith(correctionPolicy: CorrectionGrowthPolicy): LearnerAdaptationProfile =
        LearnerAdaptationProfile(
            core = LearnerAbilityProfile(
                cefrLevel = VocabLevel.A1,
                levelConfidence = ProfileConfidence.Medium,
                grammarStage = SkillStage.Foundation,
                vocabularyStage = SkillStage.Foundation,
                fluencyStage = SkillStage.Foundation,
                naturalnessStage = SkillStage.Foundation,
                focus = LearningFocusSummary(
                    primaryFocus = null,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Low,
                    observedCount = 0
                )
            ),
            chatPolicy = ChatAdaptationPolicy(
                conversationBand = ConversationAbilityBand.SimpleSentence,
                intentSupport = IntentSupportPolicy.TrustMeaning,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                recastStyle = RecastStylePolicy.SimpleInline,
                expressionGrowth = ExpressionGrowthPolicy.OneSimplePattern,
                questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            ),
            correctionPolicy = correctionPolicy
        )
}
