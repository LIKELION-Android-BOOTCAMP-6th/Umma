package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.CorrectionSessionContext
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.CorrectionExplanationPolicy
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
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
            appendLine("Return at most 10 suggestions; if more candidates need correcting, keep the 10 most impactful.")
            appendLine("Safety:")
            appendLine("- Do not correct, naturalize, translate, or make harmful content more actionable.")
            appendLine("- Skip candidates involving self-harm instructions, child sexual content, hate or harassment, crime, fraud, explicit sexual content, or dangerous professional advice.")
            appendLine("- Safe language-learning help is allowed only when it does not preserve or strengthen harmful intent.")
            appendLine()
            // COR-TUNE-005: band별 few-shot anchor. 모델 출력을 band 기대 형태(길이/강도)에 맞춘다.
            // 예시는 anchor일 뿐, candidate 언어 조합을 강제하지 않는다(교정은 항상 selectedLang).
            // 언어를 하드코딩하지 않고 selectedLang.code를 참조한다. band 이름·점수·레벨은 노출하지 않는다.
            appendLine("Example of the expected correction style for this learner (illustrative only; always correct in ${selectedLang.code} and do not copy this example):")
            appendLine("- ${bandFewShotExample(policy.band, selectedLang)}")
            appendLine()
            // COR-TUNE-010: Candidates 보다 위에서 "의도 파악" 을 먼저 지시한다.
            // 이전 세션 기억 + 현재 세션 흐름을 candidate 1문장 + assistantContext 한마디보다 넓게 보여줘
            // AI 가 "통째로 번역" 하지 않고 학습자가 하려던 말의 의도를 먼저 추론한 뒤 교정하게 한다.
            // 세션 맥락이 전부 비어 있으면(SessionMemory 조회 실패 등) 블록 자체를 생략해 토큰을 아끼고
            // 기존 candidate 기반 교정 흐름을 그대로 유지한다(폴백).
            appendIntentContextBlock(input.sessionContext)
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
            // 핵심 4필드(candidateId/nativeText/afterText/explanation)는 그대로 유지하고, 그 옆에
            // sourceLang(원문 언어 보고)과 suggestion 당 learningSignal 중첩을 더한다.
            // sourceLang 은 learningSignal 안이 아니라 suggestion 최상위에 둔다 — 신호가 정규화 단계에서
            // drop 되어도 평가 게이트(COR-TUNE-011)가 읽을 수 있어야 하고, 원문 언어는 "신호"가 아니라
            // 발화 자체의 속성이기 때문이다(COR-TUNE-011-FIX_AI_Sourced_Utterance_Language_Gating).
            // 키 구성은 CHAT-TUNE-001 핸드오버 JSON 예시와 일치한다.
            appendLine("""{"suggestions":[{"candidateId":"...","nativeText":"...","afterText":"...","explanation":"...","sourceLang":"...","learningSignal":{"candidateId":"...","sourceTurnId":"... or null","sourceTurnIndex":0,"sourceText":"...","correctedText":"...","issueCategories":["..."],"languageFeatures":[{"lang":"...","featureKey":"..."}],"improvementTypes":["..."],"editSpans":[{"sourceFragment":"...","correctedFragment":"...","issueCategory":"...","languageFeatureKey":"...","improvementType":"..."}],"register":"...","severity":"...","meaningPreserved":true,"confidence":0.0}}]}""")
            appendLine("Rules:")
            // COR-FIX-008-A/D: Gemini가 가끔 최상위 bare array나 문자열 밖 토큰으로 깨진 JSON을
            // 직접 돌려준 적이 있다 — mapper fallback으로도 방어하지만, 프롬프트로 빈도를 줄인다.
            appendLine("- The top-level JSON value MUST be an object with a \"suggestions\" array, exactly as in the schema above. Do NOT return a bare array as the top-level JSON value.")
            appendLine("- Do not put any marker, grade, label, letter, or extra character outside JSON string values. Escape quotation marks inside string values such as explanation. The response must parse with a strict JSON parser.")
            // COR-FIX-009: candidateId hallucination(예: "ja-6-0-79967d5") 방어. 한 줄 지시로는
            // 모델이 "비슷한 ID를 만들어도 된다"고 오해할 여지가 있어, 복사/생성금지/skip 세 규칙으로 명시한다.
            // mapper의 unknown candidateId strict 검증(IllegalArgumentException, 재시도 없음)은 그대로 유지하고
            // 여기서는 애초에 unknown이 발생하지 않도록 계약을 단단히 한다.
            appendLine("- candidateId: COPY EXACTLY one of the candidateId values from the Candidates section above. Do not invent new ids.")
            appendLine("- Never create, infer, shorten, hash, translate, or reformat a candidateId.")
            appendLine("- If you cannot use an exact candidateId from the Candidates section, skip that candidate.")
            appendLine("- nativeText: a natural $primaryLangName (${primaryLang.code}) translation of the corrected sentence (afterText) — i.e. what the learner meant to say. Do NOT describe the correction here; what was changed belongs ONLY in explanation.")
            appendLine("- afterText: the corrected sentence in ${selectedLang.code}.")
            // COR-TUNE-003-FIX: primaryLang 고정 제거 → Explanation 정책에 위임.
            // explanationLine()이 band별로 언어를 결정하므로(고급 band: target language, 초급: primaryLang)
            // 여기서 언어를 다시 고정하면 두 지시가 충돌한다. 형식 제약(60자)만 남기고 언어는 위 정책을 따른다.
            appendLine("- explanation: unlike nativeText, this field is only for a short correction tip explaining what changed and why (under 60 chars), in the language set by the Explanation policy above. Every suggestion MUST include a non-empty explanation — never omit it. If the reason is simple, still give a short tip.")
            // COR-TUNE-011-FIX (Method B): detectedLang seam 이 폐기되어, 발화 원문 언어는 이제 AI 가 직접 보고한다.
            // afterText(=항상 selectedLang)와 혼동하지 않도록 "원문(sourceText) 기준"임을 명시하고,
            // 확신이 없을 때 "unknown"을 쓰게 해 mapper 가 보수적으로 null(=평가 통과)로 떨어뜨릴 escape hatch 를 둔다.
            appendLine("- sourceLang: the ISO code (e.g. \"ko\", \"en\", \"ja\", \"de\") of the language the learner ACTUALLY used in sourceText (not the corrected afterText). If you are not sure, use \"unknown\".")
            appendLine("- Emit one suggestion per safe candidate. Skip a candidate if it is unsafe or if no correction is needed.")
            appendLine("- Preserve sourceText as the original utterance context. Do not rewrite, trim, sanitize, or remove filler from sourceText itself; cleanup applies only to afterText.")
            appendLine("- Filler and repetition cleanup: in afterText, remove unnecessary filler words, hesitation markers, and repeated discourse markers when doing so does not change the speaker's meaning.")
            appendLine("- Keep afterText natural and concise for learning and flashcard use.")
            appendLine("- Examples of removable filler/discourse markers include English \"um\", \"uh\", discourse-marker \"like\", and some uses of \"I mean\"; Japanese \"なんか\" and discourse-marker \"その\"; Korean \"음\", \"어\", \"그니까\", \"약간\", \"뭐가\", and some uses of \"아니\".")
            appendLine("- Do NOT remove an expression if it carries real meaning, contrast, emphasis, correction, or the speaker's intended nuance.")
            appendLine("- Keep meaning-bearing uses such as \"I like coffee.\", corrective/emphatic \"I mean\", Japanese \"なんか\" meaning \"something\", referential \"その\", Korean degree-marker \"약간\", and negative \"아니\".")
            // COR-TUNE-02: learningSignal 은 능력 점수가 아니라 "이번 교정에서 관찰한 것"만 담는다.
            // 규칙은 enum 을 1:1 장황하게 나열하지 않고 실행 가능한 짧은 지시로 압축한다.
            appendLine("learningSignal rules (what you observed in THIS correction; never rate the learner's overall level):")
            appendLine("- One learningSignal per suggestion, reusing the same candidateId.")
            appendLine("- issueCategories: pick from [$issueCategoryValues], at most 3. Use ONLY these values — any value outside the list discards the whole learningSignal.")
            appendLine("- improvementTypes: pick from [$improvementTypeValues], at most 3. Use ONLY these values — any value outside the list discards the whole learningSignal.")
            appendLine("- register: exactly one of [$registerValues] describing the corrected sentence.")
            appendLine("- severity: exactly one of [$severityValues].")
            appendLine("- languageFeatures: at most 3, each {\"lang\":\"${selectedLang.code}\",\"featureKey\":\"$langNamespace.<Feature>\"} (e.g. $langNamespace.Tense); lang must equal ${selectedLang.code}. MUST be an array of objects, never strings — correct: [{\"lang\":\"${selectedLang.code}\",\"featureKey\":\"$langNamespace.Tense\"}], incorrect: [\"$langNamespace.Tense\"].")
            // COR-TUNE-003-FIX: editSpans의 enum 제약·폐기 경고 추가.
            // 매퍼 normalizeEditSpan은 issueCategory/improvementType이 허용 목록 밖이면 learningSignal 전체를 drop한다(COR-TUNE-002-FIX).
            // top-level 규칙(issueCategories/improvementTypes)과 동일 어휘로 명시해 AI가 자연어 값을 넣지 않게 한다.
            appendLine("- editSpans: at most 3, only the changed fragments (do NOT repeat the whole sentence); no character offsets. If filler cleanup is the main correction, include only a simple span when you are confident. Each span's issueCategory MUST be one of [$issueCategoryValues] and improvementType one of [$improvementTypeValues] — any value outside these lists discards the whole learningSignal. languageFeatureKey may be null.")
            appendLine("- meaningPreserved: ALWAYS include it (never omit) — true unless the correction changed the speaker's intended meaning. A missing value discards the whole learningSignal.")
            appendLine("- confidence: a number in 0.0..1.0, or omit it if unsure.")
            appendLine("- If unsure about a signal, use an empty array or low confidence rather than guessing.")
        }
    }

    /**
     * COR-TUNE-010: Candidates 위에 "이전 세션 기억 + 현재 세션 흐름 → 의도 파악" 블록을 덧붙인다.
     *
     * 목적: 교정이 candidate 1문장 + assistantContext 한마디만 보고 "통째로 번역" 하지 않도록,
     * AI 가 후보를 고치기 전에 학습자의 의도를 먼저 추론하게 한다. 의도 파악 결과는 출력 schema 에
     * 드러나지 않는 내부 추론 단계일 뿐이며, 핵심 4필드/learningSignal 규칙은 그대로다.
     *
     * 토큰 비대 방지(주입 상한):
     *  - [MAX_CONTEXT_TOPIC_SUMMARIES]/[MAX_CONTEXT_KEY_SENTENCES]: SessionMemory 가 이미 압축해 둔
     *    리스트라 최근 항목일수록 앞쪽에 있다 — `take()` 로 최신 우선만 자른다.
     *  - [MAX_CONTEXT_CURRENT_TURNS]: 현재 세션은 길어질 수 있어 `takeLast()` 로 최신 turn 만 남긴다.
     *  - 세 묶음(주제/요약/문장/현재 turn)이 전부 비어 있으면 블록 자체를 생략한다 — 단발 Gemini 호출의
     *    프롬프트가 의미 없이 길어지는 것을 막고, SessionMemory 조회 실패 시 기존 동작을 그대로 보존한다.
     *  - 내부 band 이름·점수·레벨·raw metric 은 여기서도 노출하지 않는다(빌더 전체 원칙과 동일).
     */
    private fun StringBuilder.appendIntentContextBlock(context: CorrectionSessionContext) {
        val recentTopics = context.recentTopics.take(MAX_CONTEXT_TOPIC_SUMMARIES)
        val topicSummaries = context.topicSummaries.take(MAX_CONTEXT_TOPIC_SUMMARIES)
        val topicKeySentences = context.topicKeySentences.take(MAX_CONTEXT_KEY_SENTENCES)
        val currentTurns = context.currentSessionTurns.takeLast(MAX_CONTEXT_CURRENT_TURNS)

        if (recentTopics.isEmpty() && topicSummaries.isEmpty() && topicKeySentences.isEmpty() && currentTurns.isEmpty()) {
            return
        }

        appendLine("Conversation context / intent (read this FIRST: infer what the learner is actually trying to say, then correct each candidate to match that intent — do not translate it wholesale):")
        if (recentTopics.isNotEmpty()) {
            appendLine("- Topics this learner has talked about before: ${recentTopics.joinToString(", ")}")
        }
        if (topicSummaries.isNotEmpty()) {
            appendLine("- What was corrected for this learner before:")
            topicSummaries.forEach { summary -> appendLine("  - ${summary.replace("\n", " ")}") }
        }
        if (topicKeySentences.isNotEmpty()) {
            appendLine("- Sentences this learner has practiced before:")
            topicKeySentences.forEach { sentence -> appendLine("  - ${sentence.replace("\n", " ")}") }
        }
        if (currentTurns.isNotEmpty()) {
            appendLine("- Current conversation flow (oldest to newest):")
            currentTurns.forEach { turn -> appendLine("  - ${turn.speaker}: ${turn.text.replace("\n", " ")}") }
        }
        appendLine()
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

    /**
     * COR-TUNE-005: band별 few-shot anchor 한 줄.
     *
     * 고정 외국어 문장을 박지 않고 "source 형태 → selectedLang corrected 형태" 변환 패턴만 기술한다.
     * 따라서 어떤 selectedLang 조합에도 안전하며(언어 비종속), 토큰을 최소화한다(band당 1줄, 빌드당 1줄만 삽입).
     * 설명 언어는 여기서 다시 고정하지 않고 Explanation 정책(explanationLine)에 위임한다(COR-TUNE-003-FIX 유지).
     */
    private fun bandFewShotExample(band: CorrectionGrowthBand, selectedLang: LangCode): String {
        val lang = selectedLang.code
        return when (band) {
            CorrectionGrowthBand.MeaningFirst ->
                "disconnected words or fragments become one or two very short complete sentences in $lang that keep the exact meaning and add no new words."
            CorrectionGrowthBand.PatternFix ->
                "a short phrase with one broken core pattern becomes the same phrase in $lang with only that single pattern fixed and nothing else changed."
            CorrectionGrowthBand.SentenceShape ->
                "a short sentence with shaky word order or grammar becomes one complete, well-formed short sentence in $lang with at most one tiny addition."
            CorrectionGrowthBand.EverydayNatural ->
                "an understandable but stiff or literal sentence becomes the same meaning in $lang expressed with one more natural everyday phrase."
            CorrectionGrowthBand.ConnectedExpression ->
                "two ideas stated flatly become the same ideas in $lang joined with a natural connective and a more spoken phrasing."
            CorrectionGrowthBand.NuanceRefine ->
                "a correct but plain sentence becomes the same meaning in $lang refined for tone and register with a more native-like word choice."
        }
    }

    /** LangCode → 자연어 이름. Chat 의 BuildPromptUseCase.languageName() 과 동일한 매핑을 유지한다. */
    private fun languageName(code: LangCode): String = when (code) {
        LangCode.EN -> "English"
        LangCode.JA -> "Japanese"
        LangCode.KO -> "Korean"
        LangCode.DE -> "German"
        LangCode.UNKNOWN -> "Korean"
    }

    private companion object {
        // COR-TUNE-010: 의도 파악 맥락 블록의 주입 상한(token budget SSOT).
        // 단발 Gemini 2.5-flash 호출(1-pass)에 Correction policy + few-shot + Candidates + 응답 schema 가
        // 이미 함께 실리므로, 맥락 블록은 "넓지만 무한하지 않게" 둔다 — 최신/고빈도 우선으로 자른다(.take/.takeLast).
        // 요약 5개 · 핵심문장 5개 · 현재 세션 turn 20개로 candidate 1문장 + assistantContext 한마디보다는
        // 충분히 넓되, SessionMemory 누적이 늘어도 프롬프트 길이가 선형으로 폭주하지 않게 막는다.
        const val MAX_CONTEXT_TOPIC_SUMMARIES = 5
        const val MAX_CONTEXT_KEY_SENTENCES = 5
        const val MAX_CONTEXT_CURRENT_TURNS = 20
    }
}
