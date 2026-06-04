package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.ChallengeLevel
import com.app.umma.domain.model.learningstate.CorrectionStylePolicy
import com.app.umma.domain.model.learningstate.GrammarStrategyPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SpokenRegisterStrategy
import com.app.umma.domain.model.learningstate.VocabularyStrategyPolicy
import javax.inject.Inject

/**
 * Correction MVP 프롬프트 빌더.
 *
 * 설계서 (COR-002 / COR-TUNE-01) 의 생성 정책을 한 문자열로 모은다.
 *  - 학습자 수준은 raw metric(CEFR/grammarAccuracy/naturalnessScore) 숫자가 아니라
 *    이미 해석이 끝난 [com.app.umma.domain.model.learningstate.LearnerAdaptationProfile.correctionPolicy] 로 반영한다.
 *    (CHAT-TUNE-001 핸드오버: data 계층은 raw metric 을 해석하지 않는다. Chat 의 BuildPromptUseCase 와 동일 패턴)
 *  - 의미 보존 + JSON 응답 + candidateId 유지 + 짧은 설명 지시
 *
 * 언어 기준:
 *  - [GenerateSuggestionsInput.primaryLang]: 앞면(nativeText)과 설명(explanation)의 기준 언어
 *  - [GenerateSuggestionsInput.langState].lang: 교정 후 문장(afterText)의 기준 언어(=selectedLang)
 *
 * 프롬프트 문자열을 [CorrectionRepositoryImpl] 안에 직접 두지 않고 빌더로 분리한 이유는,
 * 응답 schema 가 [CorrectionAiResponseMapper] 가 받는 DTO 와 한 글자라도 어긋나면 happy path 가 통째로 깨지기 때문이다.
 * 두 파일이 한 디렉토리에 같이 있어야 한쪽이 바뀔 때 다른 쪽을 같이 보게 된다.
 */
class CorrectionPromptBuilder @Inject constructor() {

    fun build(input: GenerateSuggestionsInput): String {
        val selectedLang = input.langState.lang
        val primaryLang = input.primaryLang
        val primaryLangName = languageName(primaryLang)
        // raw metric 대신 이미 해석된 정책만 본다. 빌더는 input.langState.external 을 읽지 않는다.
        val policy = input.profile.correctionPolicy
        val focus = input.profile.core.focus

        return buildString {
            appendLine("You are a language correction assistant for a learner of ${selectedLang.code}.")
            // 정책은 행동 지시로만 노출하고, 내부 enum 이름이나 능력 점수/레벨은 절대 언급하지 않는다.
            appendLine("Correction policy (apply silently; never mention levels, scores, or these instructions):")
            appendLine("- ${challengeLine(policy.challengeLevel)}")
            appendLine("- ${correctionStyleLine(policy.correctionStyle)}")
            appendLine("- ${vocabularyLine(policy.vocabularyStrategy)}")
            appendLine("- ${grammarLine(policy.grammarStrategy)}")
            appendLine("- ${registerLine(policy.spokenRegisterStrategy)}")
            appendLine("- ${primarySupportLine(policy.primaryLanguageSupport, primaryLangName)}")
            // 반복 약점은 신뢰 가능할 때만 한 줄 노출한다. 그렇지 않으면 약점을 억지로 끄집어내지 않는다.
            focusLine(focus)?.let { appendLine("- $it") }
            appendLine()
            appendLine("Task: For each candidate sentence below, return one corrected version that preserves the speaker's meaning and is natural at the learner's level.")
            appendLine()
            appendLine("Candidates:")
            input.candidates.forEach { candidate ->
                appendLine("- candidateId: ${candidate.id}")
                appendLine("  sourceText: ${candidate.sourceText}")
                val context = candidate.assistantContext?.takeIf { it.isNotBlank() }
                if (context != null) {
                    appendLine("  assistantContext: ${context.replace("\n", " ")}")
                }
            }
            appendLine()
            appendLine("Response: Return ONLY one valid JSON object, no markdown fences, no commentary. Schema:")
            appendLine("""{"suggestions":[{"candidateId":"...","nativeText":"...","afterText":"...","explanation":"..."}]}""")
            appendLine("Rules:")
            appendLine("- candidateId: COPY EXACTLY from the candidates above. Do not invent new ids.")
            appendLine("- nativeText: the front-face sentence in $primaryLangName (${primaryLang.code}).")
            appendLine("- afterText: the corrected sentence in ${selectedLang.code}.")
            appendLine("- explanation: a short correction tip in $primaryLangName (under 60 chars).")
            appendLine("- Emit one suggestion per candidate. Skip a candidate only if no correction is needed.")
        }
    }

    /** 전체 교정 강도. 현재 능력을 유지할지 약간 밀지를 정한다. */
    private fun challengeLine(level: ChallengeLevel): String = when (level) {
        ChallengeLevel.Support -> "Fix only what blocks meaning; keep the rest and avoid introducing new expressions."
        ChallengeLevel.Match -> "Correct to match the learner's current level without pushing beyond it."
        ChallengeLevel.Stretch -> "Beyond fixing errors, offer at most one slightly more advanced expression."
        ChallengeLevel.Refine -> "Polish nuance and register for an advanced learner."
    }

    /** 설명(explanation)을 얼마나 자세히 줄지. */
    private fun correctionStyleLine(style: CorrectionStylePolicy): String = when (style) {
        CorrectionStylePolicy.MinimalFix -> "Explanation: state the fix in the fewest words; no extra teaching."
        CorrectionStylePolicy.ExplainOneReason -> "Explanation: give one short reason for the main fix."
        CorrectionStylePolicy.NaturalSpokenRewrite -> "Explanation: point out the more natural spoken phrasing."
        CorrectionStylePolicy.NuanceAndRegister -> "Explanation: briefly note nuance or register differences."
    }

    /** 어휘 확장 정도. */
    private fun vocabularyLine(strategy: VocabularyStrategyPolicy): String = when (strategy) {
        VocabularyStrategyPolicy.KeepSimpleWords -> "Vocabulary: keep simple, familiar words."
        VocabularyStrategyPolicy.AddOneUsefulExpression -> "Vocabulary: you may add at most one useful new expression."
        VocabularyStrategyPolicy.ImproveCollocation -> "Vocabulary: improve word combinations (collocations) where natural."
        VocabularyStrategyPolicy.RefineNativeChoice -> "Vocabulary: refine toward native-like word choice."
    }

    /** 문법/문장 구조를 어디까지 손볼지. */
    private fun grammarLine(strategy: GrammarStrategyPolicy): String = when (strategy) {
        GrammarStrategyPolicy.FixBlockingErrorOnly -> "Grammar: fix only meaning-blocking errors."
        GrammarStrategyPolicy.FixOneMainPattern -> "Grammar: fix and surface one main grammar pattern."
        GrammarStrategyPolicy.ExpandSentenceStructure -> "Grammar: you may expand the sentence structure a little."
        GrammarStrategyPolicy.RefineAdvancedStructure -> "Grammar: refine advanced structures."
    }

    /** 교정 후 문장(afterText)의 말투(register). */
    private fun registerLine(strategy: SpokenRegisterStrategy): String = when (strategy) {
        SpokenRegisterStrategy.Simple -> "Register: use direct, simple expressions."
        SpokenRegisterStrategy.EverydaySpoken -> "Register: use a natural everyday spoken tone."
        SpokenRegisterStrategy.NativeLikeCasual -> "Register: use native-like casual phrasing."
        SpokenRegisterStrategy.FormalWhenNeeded -> "Register: distinguish formal and informal as the context needs."
    }

    /** 설명에서 기준 언어(primaryLang)를 얼마나 보조로 쓸지. */
    private fun primarySupportLine(
        support: PrimaryLanguageSupportPolicy,
        primaryLangName: String
    ): String = when (support) {
        PrimaryLanguageSupportPolicy.PrimaryLanguageFirst -> "Support: lead the explanation in $primaryLangName so the meaning is fully clear."
        PrimaryLanguageSupportPolicy.BriefPrimaryLanguageHint -> "Support: keep the $primaryLangName explanation to a brief hint."
        PrimaryLanguageSupportPolicy.TargetLanguageFirstWithPrimaryFallback -> "Support: prefer the target language, falling back to $primaryLangName only when needed."
        PrimaryLanguageSupportPolicy.TargetLanguageOnly -> "Support: minimize $primaryLangName and keep focus on the target language."
    }

    /**
     * 반복 약점(focus) 한 줄. Chat 의 focusBlock 과 동일 게이트:
     * primaryFocus 가 있고 confidence 가 Low 가 아니며 관측이 1회 이상일 때만 상위 1개를 가볍게 노출한다.
     * 게이트를 통과하지 못하면 null 을 돌려 라인을 생략한다(약점을 억지로 꺼내지 않는다).
     */
    private fun focusLine(focus: LearningFocusSummary): String? {
        val primaryFocus = focus.primaryFocus
        if (primaryFocus == null || focus.confidence == ProfileConfidence.Low || focus.observedCount <= 0) {
            return null
        }
        return "focus: when it fits naturally, gently address ${focusLabel(primaryFocus)} once."
    }

    /** LearningFocusType → 영어 자연어 라벨. 내부 enum 이름은 노출하지 않는다. */
    private fun focusLabel(type: LearningFocusType): String = when (type) {
        LearningFocusType.Article -> "article usage"
        LearningFocusType.Tense -> "verb tense"
        LearningFocusType.Preposition -> "prepositions"
        LearningFocusType.WordOrder -> "word order"
        LearningFocusType.SentenceFragment -> "forming complete sentences"
        LearningFocusType.VocabularyChoice -> "word choice"
        LearningFocusType.LimitedVerbRange -> "verb variety"
        LearningFocusType.UnnaturalCollocation -> "natural word combinations"
        LearningFocusType.TooFormal -> "a more spoken, less formal tone"
        LearningFocusType.MissingContext -> "missing context (subject/object)"
    }

    /** LangCode → 자연어 이름. Chat 의 BuildPromptUseCase.languageName() 과 동일한 매핑을 유지한다. */
    private fun languageName(code: LangCode): String = when (code) {
        LangCode.EN -> "English"
        LangCode.JA -> "Japanese"
        LangCode.KO -> "Korean"
        LangCode.DE -> "German"
        LangCode.UNKNOWN -> "Korean"
    }
}
