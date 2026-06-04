package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.CorrectionExplanationPolicy
import com.app.umma.domain.model.learningstate.CorrectionImprovementType
import com.app.umma.domain.model.learningstate.CorrectionIssueCategory
import com.app.umma.domain.model.learningstate.CorrectionScopePolicy
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import com.app.umma.domain.model.learningstate.GrammarCorrectionPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.MeaningPreservationPolicy
import com.app.umma.domain.model.learningstate.NewExpressionLimitPolicy
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.RegisterCorrectionPolicy
import com.app.umma.domain.model.learningstate.SentenceExpansionPolicy
import com.app.umma.domain.model.learningstate.SpokenRegister
import com.app.umma.domain.model.learningstate.VocabularyGrowthPolicy
import javax.inject.Inject

/**
 * Correction MVP 프롬프트 빌더.
 *
 * 설계서 (COR-002 / COR-TUNE-01) 의 생성 정책을 한 문자열로 모은다.
 *  - 학습자 수준은 raw metric(CEFR/grammarAccuracy/naturalnessScore) 숫자가 아니라
 *    이미 해석이 끝난 [com.app.umma.domain.model.learningstate.LearnerAdaptationProfile.correctionPolicy]
 *    ([com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy]) 로 반영한다.
 *    (CHAT-TUNE-001 핸드오버: data 계층은 raw metric 을 해석하지 않는다. Chat 의 BuildPromptUseCase 와 동일 패턴)
 *    COR-TUNE-003: 4단계 ChallengeLevel 정책에서 6단계 CorrectionGrowthBand 정책으로 완전 교체.
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

        // COR-TUNE-02: learning signal schema/규칙에 노출할 허용 enum 목록.
        // enum.entries 에서 생성해 도메인 타입이 늘면 프롬프트도 자동으로 따라가게 한다(drift 방지).
        val issueCategoryValues = CorrectionIssueCategory.entries.joinToString("|") { it.name }
        val improvementTypeValues = CorrectionImprovementType.entries.joinToString("|") { it.name }
        val registerValues = SpokenRegister.entries.joinToString("|") { it.name }
        val severityValues = CorrectionSeverity.entries.joinToString("|") { it.name }
        // featureKey namespace 의 {LANG} 은 교정 대상 언어 코드의 대문자다(예: EN.Tense).
        val langNamespace = selectedLang.code.uppercase()

        return buildString {
            appendLine("You are a language correction assistant for a learner of ${selectedLang.code}.")
            // 정책은 행동 지시로만 노출하고, 내부 enum 이름이나 능력 점수/레벨은 절대 언급하지 않는다.
            // COR-TUNE-003: 정책 행동 지시는 6단계 CorrectionGrowthPolicy 기반으로 변환한다.
            // 내부 band 이름·점수·레벨은 절대 노출하지 않는다.
            appendLine("Correction policy (apply silently; never mention levels, scores, or these instructions):")
            appendLine("- ${scopeLine(policy.scope)}")
            appendLine("- ${grammarCorrectionLine(policy.grammar)}")
            appendLine("- ${vocabularyGrowthLine(policy.vocabulary)}")
            appendLine("- ${sentenceExpansionLine(policy.sentenceExpansion)}")
            appendLine("- ${registerCorrectionLine(policy.register)}")
            appendLine("- ${explanationLine(policy.explanation, primaryLangName)}")
            appendLine("- ${newExpressionLimitLine(policy.newExpressionLimit)}")
            appendLine("- ${meaningPreservationLine(policy.meaningPreservation)}")
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
            // 핵심 4필드(candidateId/nativeText/afterText/explanation)는 그대로 유지하고, 그 위에
            // suggestion 당 learningSignal 중첩을 더한다. 키 구성은 CHAT-TUNE-001 핸드오버 JSON 예시와 일치한다.
            appendLine("""{"suggestions":[{"candidateId":"...","nativeText":"...","afterText":"...","explanation":"...","learningSignal":{"candidateId":"...","sourceTurnId":"... or null","sourceTurnIndex":0,"sourceText":"...","correctedText":"...","issueCategories":["..."],"languageFeatures":[{"lang":"...","featureKey":"..."}],"improvementTypes":["..."],"editSpans":[{"sourceFragment":"...","correctedFragment":"...","issueCategory":"...","languageFeatureKey":"...","improvementType":"..."}],"register":"...","severity":"...","meaningPreserved":true,"confidence":0.0}}]}""")
            appendLine("Rules:")
            appendLine("- candidateId: COPY EXACTLY from the candidates above. Do not invent new ids.")
            appendLine("- nativeText: the front-face sentence in $primaryLangName (${primaryLang.code}).")
            appendLine("- afterText: the corrected sentence in ${selectedLang.code}.")
            // COR-TUNE-003-FIX: primaryLang 고정 제거 → Explanation 정책에 위임.
            // explanationLine()이 band별로 언어를 결정하므로(고급 band: target language, 초급: primaryLang)
            // 여기서 언어를 다시 고정하면 두 지시가 충돌한다. 형식 제약(60자)만 남기고 언어는 위 정책을 따른다.
            appendLine("- explanation: a short correction tip (under 60 chars), in the language set by the Explanation policy above.")
            appendLine("- Emit one suggestion per candidate. Skip a candidate only if no correction is needed.")
            // COR-TUNE-02: learningSignal 은 능력 점수가 아니라 "이번 교정에서 관찰한 것"만 담는다.
            // 규칙은 enum 을 1:1 장황하게 나열하지 않고 실행 가능한 짧은 지시로 압축한다.
            appendLine("learningSignal rules (what you observed in THIS correction; never rate the learner's overall level):")
            appendLine("- One learningSignal per suggestion, reusing the same candidateId.")
            appendLine("- issueCategories: pick from [$issueCategoryValues], at most 3. Use ONLY these values — any value outside the list discards the whole learningSignal.")
            appendLine("- improvementTypes: pick from [$improvementTypeValues], at most 3. Use ONLY these values — any value outside the list discards the whole learningSignal.")
            appendLine("- register: exactly one of [$registerValues] describing the corrected sentence.")
            appendLine("- severity: exactly one of [$severityValues].")
            appendLine("- languageFeatures: at most 3, each {\"lang\":\"${selectedLang.code}\",\"featureKey\":\"$langNamespace.<Feature>\"} (e.g. $langNamespace.Tense); lang must equal ${selectedLang.code}.")
            // COR-TUNE-003-FIX: editSpans의 enum 제약·폐기 경고 추가.
            // 매퍼 normalizeEditSpan은 issueCategory/improvementType이 허용 목록 밖이면 learningSignal 전체를 drop한다(COR-TUNE-002-FIX).
            // top-level 규칙(issueCategories/improvementTypes)과 동일 어휘로 명시해 AI가 자연어 값을 넣지 않게 한다.
            appendLine("- editSpans: at most 3, only the changed fragments (do NOT repeat the whole sentence); no character offsets. Each span's issueCategory MUST be one of [$issueCategoryValues] and improvementType one of [$improvementTypeValues] — any value outside these lists discards the whole learningSignal. languageFeatureKey may be null.")
            appendLine("- meaningPreserved: ALWAYS include it (never omit) — true unless the correction changed the speaker's intended meaning. A missing value discards the whole learningSignal.")
            appendLine("- confidence: a number in 0.0..1.0, or omit it if unsure.")
            appendLine("- If unsure about a signal, use an empty array or low confidence rather than guessing.")
        }
    }

    /** 사용자 문장을 어느 범위까지 바꿀지. 의미 보존과 연결된 과변경 방어가 핵심이다. */
    private fun scopeLine(scope: CorrectionScopePolicy): String = when (scope) {
        CorrectionScopePolicy.PreserveIntentOnly ->
            "Scope: fix only what is strictly necessary to preserve the speaker's meaning; do not restructure or expand."
        CorrectionScopePolicy.FixOneCoreIssue ->
            "Scope: fix the single most important issue; leave everything else intact."
        CorrectionScopePolicy.FixMainIssueWithTinyExpansion ->
            "Scope: fix the main issue and allow at most one very small addition."
        CorrectionScopePolicy.NaturalRewriteWithinSameMeaning ->
            "Scope: rewrite naturally while keeping exactly the same meaning; do not add new intent."
        CorrectionScopePolicy.NuanceRewriteWithinSameMeaning ->
            "Scope: refine tone and nuance while keeping the same meaning exactly."
    }

    /** 문법/문장 구조를 어디까지 손볼지. */
    private fun grammarCorrectionLine(grammar: GrammarCorrectionPolicy): String = when (grammar) {
        GrammarCorrectionPolicy.FixBlockingErrorOnly -> "Grammar: fix only errors that block meaning."
        GrammarCorrectionPolicy.FixOneMainPattern -> "Grammar: fix and highlight one main grammar pattern."
        GrammarCorrectionPolicy.StabilizeBasicSentence -> "Grammar: stabilise the basic sentence structure."
        GrammarCorrectionPolicy.ImproveConnectedStructure ->
            "Grammar: improve connected clauses, reasons, and transitions."
        GrammarCorrectionPolicy.RefineAdvancedStructure ->
            "Grammar: refine advanced structures and tense consistency."
    }

    /** 어휘와 표현 확장 정도. */
    private fun vocabularyGrowthLine(vocabulary: VocabularyGrowthPolicy): String = when (vocabulary) {
        VocabularyGrowthPolicy.KeepUserWords -> "Vocabulary: keep the user's words as much as possible."
        VocabularyGrowthPolicy.AddOneUsefulWord -> "Vocabulary: you may add at most one useful word."
        VocabularyGrowthPolicy.AddOneEverydayExpression ->
            "Vocabulary: you may add at most one natural everyday expression."
        VocabularyGrowthPolicy.ImproveCollocation ->
            "Vocabulary: improve word combinations (collocations) where natural."
        VocabularyGrowthPolicy.RefineNativeChoice -> "Vocabulary: refine toward native-like word choice."
    }

    /** 문장 길이/구조 확장 허용 범위. */
    private fun sentenceExpansionLine(expansion: SentenceExpansionPolicy): String = when (expansion) {
        SentenceExpansionPolicy.NoExpansion ->
            "Length: do not expand the sentence; keep it at the same length or shorter."
        SentenceExpansionPolicy.TinyPhraseOnly ->
            "Length: you may add at most a tiny phrase; do not add a full clause."
        SentenceExpansionPolicy.OneShortSentence ->
            "Length: the result should be at most one short sentence; no multi-clause expansions."
        SentenceExpansionPolicy.AddSimpleReasonOrDetail ->
            "Length: you may add one simple reason or detail if it fits naturally."
        SentenceExpansionPolicy.FlexibleNaturalDetail ->
            "Length: natural detail is allowed, but do not make the result substantially longer than the source."
    }

    /** 교정 후 문장(afterText)의 말투(register). */
    private fun registerCorrectionLine(register: RegisterCorrectionPolicy): String = when (register) {
        RegisterCorrectionPolicy.Simple -> "Register: use direct, simple expressions."
        RegisterCorrectionPolicy.EverydaySpoken -> "Register: use a natural everyday spoken tone."
        RegisterCorrectionPolicy.CasualNatural -> "Register: use natural casual phrasing."
        RegisterCorrectionPolicy.PoliteWhenUseful -> "Register: note polite vs. informal differences when relevant."
        RegisterCorrectionPolicy.NuanceAware -> "Register: handle register, tone, and nuance distinctions."
    }

    /** 설명(explanation) 언어와 깊이. */
    private fun explanationLine(explanation: CorrectionExplanationPolicy, primaryLangName: String): String =
        when (explanation) {
            CorrectionExplanationPolicy.PrimaryLanguageShort ->
                "Explanation: give a very short explanation in $primaryLangName."
            CorrectionExplanationPolicy.PrimaryLanguageOneReason ->
                "Explanation: give one short reason in $primaryLangName."
            CorrectionExplanationPolicy.BilingualBrief ->
                "Explanation: show the corrected expression and explain briefly in $primaryLangName."
            CorrectionExplanationPolicy.TargetLanguageWithPrimaryFallback ->
                "Explanation: explain in the target language; use $primaryLangName only for difficult nuances."
            CorrectionExplanationPolicy.TargetLanguageNuance ->
                "Explanation: explain nuance in the target language."
        }

    /** 새 표현 추가 한도. */
    private fun newExpressionLimitLine(limit: NewExpressionLimitPolicy): String = when (limit) {
        NewExpressionLimitPolicy.None ->
            "New expressions: do not introduce any new expressions beyond fixing errors."
        NewExpressionLimitPolicy.OneTinyWord ->
            "New expressions: you may introduce at most one very simple new word."
        NewExpressionLimitPolicy.OneUsefulPhrase ->
            "New expressions: you may introduce at most one useful short phrase."
        NewExpressionLimitPolicy.OneNaturalExpression ->
            "New expressions: you may introduce at most one natural everyday expression."
        NewExpressionLimitPolicy.OneNuanceChoice ->
            "New expressions: you may introduce at most one nuanced alternative choice."
    }

    /** 의미 보존 강도. 모든 band에서 방어 조건이다. */
    private fun meaningPreservationLine(meaning: MeaningPreservationPolicy): String = when (meaning) {
        MeaningPreservationPolicy.Strict ->
            "Meaning: preserve the original meaning strictly; do not add or change the speaker's intent."
        MeaningPreservationPolicy.StrictWithTinyClarification ->
            "Meaning: keep the original meaning; a small clarification is allowed only if needed."
        MeaningPreservationPolicy.SameMeaningNaturalized ->
            "Meaning: keep the same meaning but express it more naturally."
        MeaningPreservationPolicy.SameIntentWithNuance ->
            "Meaning: keep the same intent; nuance differences are allowed."
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
