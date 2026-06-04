package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MVP 프롬프트 회귀 테스트.
 *
 * Mapper 와 한 디렉토리에 있는 이유는, 프롬프트 schema 가 [CorrectionAiResponseMapper] 가 받는 DTO 와
 * 한 글자라도 어긋나면 happy path 가 통째로 깨지기 때문이다. 두 파일의 schema 일치를 이 테스트가 못박는다.
 *
 * COR-TUNE-01: 학습자 수준은 raw metric(CEFR/grammarAccuracy/naturalnessScore) 숫자가 아니라
 * 이미 해석된 [LearnerAdaptationProfile.correctionPolicy] 행동 지시로만 반영되어야 한다.
 */
class CorrectionPromptBuilderTest {

    private val builder = CorrectionPromptBuilder()
    private val profileUseCase = BuildLearnerAdaptationProfileUseCase()

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
        val input = inputOf(candidates = candidates)

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
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            )
        )

        val prompt = builder.build(input)

        listOf("suggestions", "candidateId", "nativeText", "afterText", "explanation").forEach { key ->
            assertTrue("schema key '$key' 누락", prompt.contains(key))
        }
    }

    @Test
    fun `prompt declares learningSignal schema keys and allowed enums`() {
        // COR-TUNE-02: 응답 schema 에 suggestion 당 learningSignal 중첩과 허용 enum/규칙이 노출되어야
        // mapper 가 받는 DTO/정규화 계약과 어긋나지 않는다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            lang = LangCode.EN
        )

        val prompt = builder.build(input)

        // learningSignal 중첩 키 노출.
        listOf(
            "learningSignal", "issueCategories", "languageFeatures", "featureKey",
            "improvementTypes", "editSpans", "register", "severity", "meaningPreserved", "confidence"
        ).forEach { key ->
            assertTrue("learningSignal schema key '$key' 누락", prompt.contains(key))
        }
        // 허용 enum 대표값 노출 (issue/improvement/register/severity).
        assertTrue("issueCategory enum 누락", prompt.contains("GrammarForm"))
        assertTrue("improvementType enum 누락", prompt.contains("GrammarFixed"))
        assertTrue("register enum 누락", prompt.contains("EverydaySpoken"))
        assertTrue("severity enum 누락", prompt.contains("MajorPattern"))
        // featureKey namespace 는 selectedLang(EN) 기준 대문자 prefix 예시를 보여줘야 한다.
        assertTrue("featureKey namespace 예시 누락", prompt.contains("EN.Tense"))
        // 배열 캡 ≤3 지시 노출.
        assertTrue("배열 캡(at most 3) 지시 누락", prompt.contains("at most 3"))
    }

    @Test
    fun `prompt does not leak raw learner metrics`() {
        // COR-TUNE-01: CEFR 라벨이나 grammarAccuracy/naturalnessScore 같은 "%.2f" 숫자가 프롬프트에 노출되면 안 된다.
        // LangState.initial 의 기본 vocabularyLevel(A1)도 더 이상 프롬프트에 들어가지 않는다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hello"
                )
            )
        )

        val prompt = builder.build(input)

        assertFalse("CEFR level(A1) 이 프롬프트에 노출됨", prompt.contains("A1"))
        assertFalse("CEFR 라벨이 프롬프트에 노출됨", prompt.contains("CEFR"))
        assertFalse("grammar accuracy raw 라벨 노출", prompt.contains("Grammar accuracy"))
        // COR-TUNE-02 이후 CorrectionSeverity.NaturalnessOnly enum 토큰이 schema 규칙에 등장하므로,
        // raw 메트릭 누수 검증은 실제 metric 라벨/필드명으로 특정한다("Naturalness" 단독 검사는 enum 과 충돌).
        assertFalse("naturalness raw 점수 라벨 노출", prompt.contains("Naturalness score"))
        assertFalse("naturalness raw 필드명 노출", prompt.contains("naturalnessScore"))
        assertFalse(
            "raw metric 숫자(%.2f 포맷)가 프롬프트에 노출됨",
            Regex("""\d\.\d{2}""").containsMatchIn(prompt)
        )
    }

    @Test
    fun `prompt omits assistantContext line when null or blank`() {
        // 빈 context 가 그대로 들어가면 토큰 낭비 + 모델 혼란.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hi",
                    assistantContext = null
                )
            )
        )

        val prompt = builder.build(input)

        assertFalse("null context 가 라벨로 노출됨", prompt.contains("assistantContext:"))
    }

    @Test
    fun `prompt forbids markdown fences so mapper json decode survives`() {
        // mapper 는 markdown fence 가 섞이면 json decode 단계에서 즉시 깨진다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "hi"
                )
            )
        )

        val prompt = builder.build(input)

        assertTrue("markdown 금지 지시 누락", prompt.contains("no markdown"))
    }

    @Test
    fun `nativeText rule uses primaryLang language name not hardcoded Korean`() {
        // primaryLang=KO 이면 "Korean"이 앞면 언어로 지정되어야 한다.
        val inputKo = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "hi")
            ),
            lang = LangCode.EN,
            primaryLang = LangCode.KO
        )
        val promptKo = builder.build(inputKo)
        assertTrue("nativeText 규칙에 Korean 누락", promptKo.contains("Korean"))
        assertTrue("nativeText 규칙에 ko 코드 누락", promptKo.contains("(ko)"))

        // primaryLang=EN, selectedLang=JA 이면 앞면은 English, 교정문은 ja 로 지정되어야 한다.
        val inputEnJa = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "ja-0-a", lang = LangCode.JA, sourceTurnIndex = 0, sourceText = "わたしが学校")
            ),
            lang = LangCode.JA,
            primaryLang = LangCode.EN
        )
        val promptEnJa = builder.build(inputEnJa)
        assertTrue("nativeText 앞면 언어가 English 여야 함", promptEnJa.contains("English"))
        assertTrue("afterText 교정문 언어가 ja 여야 함", promptEnJa.contains("in ja"))
    }

    @Test
    fun `explanation rule uses primaryLang for tip language`() {
        // explanation 팁 언어도 primaryLang 을 따라야 한다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "hi")
            ),
            lang = LangCode.EN,
            primaryLang = LangCode.KO
        )
        val prompt = builder.build(input)
        assertTrue("explanation 팁 언어 Korean 누락", prompt.contains("Korean"))
    }

    @Test
    fun `MeaningFirst policy yields meaning-preservation and no-expansion wording`() {
        // COR-TUNE-003: MeaningFirst band는 의미 보존·최소 수정·확장 금지가 핵심이다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst))
        )

        val prompt = builder.build(input)

        assertTrue("Correction policy 블록 누락", prompt.contains("Correction policy"))
        // PreserveIntentOnly scope 문구 확인.
        assertTrue("MeaningFirst 의 의미 보존 scope 지시 누락", prompt.contains("do not restructure or expand"))
        // NoExpansion 문구 확인.
        assertTrue("MeaningFirst 의 문장 확장 금지 지시 누락", prompt.contains("do not expand the sentence"))
        // NuanceRefine 전용 문구가 새어 나오지 않는지 확인.
        assertFalse("MeaningFirst 에 뉘앙스/register 문구가 새어 나옴", prompt.contains("handle register, tone, and nuance"))
    }

    @Test
    fun `NuanceRefine policy surfaces nuance and register behaviour`() {
        // COR-TUNE-003: NuanceRefine band는 뉘앙스·register·원어민식 선택이 핵심이다.
        val input = inputOf(
            candidates = listOf(
                CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")
            ),
            profile = profileWith(CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.NuanceRefine))
        )

        val prompt = builder.build(input)

        assertTrue("NuanceRefine 의 뉘앙스 문구 누락", prompt.contains("nuance"))
        assertTrue("NuanceRefine 의 register 문구 누락", prompt.contains("register"))
        // NuanceRewriteWithinSameMeaning scope 문구 확인.
        assertTrue("NuanceRefine 의 scope 지시 누락", prompt.contains("refine tone and nuance"))
        // MeaningFirst 전용 확장 금지 문구가 새어 나오지 않는지 확인.
        assertFalse("NuanceRefine 에 do-not-expand 문구가 새어 나옴", prompt.contains("do not expand the sentence"))
    }

    @Test
    fun `focus line appears only when focus is trustworthy`() {
        val candidate = CorrectionCandidate(id = "en-0-a", lang = LangCode.EN, sourceTurnIndex = 0, sourceText = "i go school")

        // 신뢰 가능한 focus(Tense, Medium, 관측 3회) → 한 줄 노출.
        val trustworthy = inputOf(
            candidates = listOf(candidate),
            profile = profileWith(
                correctionPolicy = meaningFirstPolicy(),
                focus = LearningFocusSummary(
                    primaryFocus = LearningFocusType.Tense,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Medium,
                    observedCount = 3
                )
            )
        )
        val withFocus = builder.build(trustworthy)
        assertTrue("신뢰 가능한 focus 라인 누락", withFocus.contains("focus:"))
        assertTrue("focus 라벨(verb tense) 누락", withFocus.contains("verb tense"))

        // primaryFocus 가 없으면(=신뢰 불가) focus 라인은 생략된다.
        val noFocusInput = inputOf(
            candidates = listOf(candidate),
            profile = profileWith(correctionPolicy = meaningFirstPolicy(), focus = noFocus())
        )
        val withoutFocus = builder.build(noFocusInput)
        assertFalse("focus 가 없는데 focus 라인이 노출됨", withoutFocus.contains("focus:"))

        // confidence Low 면 focus 가 있어도 생략된다.
        val lowConfidence = inputOf(
            candidates = listOf(candidate),
            profile = profileWith(
                correctionPolicy = meaningFirstPolicy(),
                focus = LearningFocusSummary(
                    primaryFocus = LearningFocusType.Tense,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Low,
                    observedCount = 5
                )
            )
        )
        assertFalse("저신뢰 focus 가 노출됨", builder.build(lowConfidence).contains("focus:"))
    }

    // --- helpers ---

    /**
     * 테스트용 입력 생성. profile 을 명시하지 않으면 LangState.initial 에서 해석한 보수적 profile 을 쓴다.
     */
    private fun inputOf(
        candidates: List<CorrectionCandidate>,
        lang: LangCode = LangCode.EN,
        primaryLang: LangCode = LangCode.KO,
        profile: LearnerAdaptationProfile = profileUseCase(LangState.initial(lang))
    ): GenerateSuggestionsInput = GenerateSuggestionsInput(
        candidates = candidates,
        langState = LangState.initial(lang),
        primaryLang = primaryLang,
        profile = profile
    )

    /** MeaningFirst 기본 정책. focusLine 게이트 테스트 등 "policy 무관" 케이스에서 사용한다. */
    private fun meaningFirstPolicy(): CorrectionGrowthPolicy =
        CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst)

    private fun noFocus(): LearningFocusSummary = LearningFocusSummary(
        primaryFocus = null,
        secondaryFocus = null,
        confidence = ProfileConfidence.Low,
        observedCount = 0
    )

    /**
     * correctionPolicy 만 다르게 한 LearnerAdaptationProfile 직접 조립.
     * 프롬프트 빌더 단위 테스트라 BuildLearnerAdaptationProfileUseCase 의 휴리스틱과 분리해 정책→문구만 검증한다.
     */
    private fun profileWith(
        correctionPolicy: CorrectionGrowthPolicy,
        focus: LearningFocusSummary = noFocus()
    ): LearnerAdaptationProfile = LearnerAdaptationProfile(
        core = LearnerAbilityProfile(
            cefrLevel = VocabLevel.A1,
            levelConfidence = ProfileConfidence.Medium,
            grammarStage = SkillStage.Foundation,
            vocabularyStage = SkillStage.Foundation,
            fluencyStage = SkillStage.Foundation,
            naturalnessStage = SkillStage.Foundation,
            focus = focus
        ),
        chatPolicy = anyChatPolicy(),
        correctionPolicy = correctionPolicy
    )

    private fun anyChatPolicy(): ChatAdaptationPolicy = ChatAdaptationPolicy(
        conversationBand = ConversationAbilityBand.SimpleSentence,
        intentSupport = IntentSupportPolicy.TrustMeaning,
        primaryBridge = PrimaryBridgePolicy.FallbackOnly,
        recastStyle = RecastStylePolicy.SimpleInline,
        expressionGrowth = ExpressionGrowthPolicy.OneSimplePattern,
        questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
        responseLength = ResponseLengthPolicy.NaturalBrief,
        speechSpeed = SpeechSpeedPolicy.NormalLearning
    )
}
