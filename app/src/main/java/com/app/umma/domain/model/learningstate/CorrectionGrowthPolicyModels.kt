package com.app.umma.domain.model.learningstate

/**
 * 교정 성장 단계(COR-TUNE-003 / CHAT-TUNE-004 핸드오버).
 *
 * 이 band는 Chat의 [ConversationAbilityBand]를 그대로 복사한 것이 아니라,
 * "교정 결과가 사용자의 처리 가능 범위 안에 있는지"를 기준으로 산출한 Correction 전용 축이다.
 *
 * 내부 이해용 비유(아기 수준, 3세 수준 등)는 팀 코멘트에만 쓰인다.
 * 코드·prompt·저장 모델·사용자 화면에는 나이 비유를 노출하지 않는다.
 */
enum class CorrectionGrowthBand {
    // 단어 조각이나 기준언어 혼합 발화 수준. 의미 전달을 먼저 살리고 매우 짧은 학습언어 문장으로 정리한다.
    MeaningFirst,

    // 짧은 구/고정 표현은 가능하지만 문장 뼈대가 약한 수준. 핵심 패턴 하나만 고친다.
    PatternFix,

    // 짧은 문장은 가능하지만 어순/시제/기본 문법이 흔들리는 수준. 문장을 완성된 형태로 다듬고 작은 구조 확장 하나만 허용한다.
    SentenceShape,

    // 기본 일상 대화가 가능한 수준. 더 자연스러운 일상 표현이나 collocation 하나를 추가할 수 있다.
    EverydayNatural,

    // 이유, 감정, 상황 설명이 가능한 수준. 연결 표현과 구어체를 다듬는다.
    ConnectedExpression,

    // 의미 전달은 안정적이고 뉘앙스/말투가 성장 지점인 수준. register, 뉘앙스, 원어민식 선택을 세밀하게 다룬다.
    NuanceRefine
}

/**
 * 교정 강도 정책 계약(COR-TUNE-003 / CHAT-TUNE-004 핸드오버).
 *
 * 이 모델은 저장 모델이 아니라 AI 기능을 위한 domain read model이다.
 * [LearnerAdaptationProfile.correctionPolicy]에 담겨 [CorrectionPromptBuilder]에 전달된다.
 *
 * Correction은 raw [LangState] metric을 직접 해석하지 않고, 이미 해석된 이 정책만 사용한다.
 * [primaryLanguageSupport]는 Chat과 동일한 의미를 유지해야 하므로 [PrimaryLanguageSupportPolicy]를 재사용한다.
 *
 * band별 기본 매핑은 [CorrectionGrowthPolicy.defaultsForBand]에 있다.
 */
data class CorrectionGrowthPolicy(
    // 교정 강도의 큰 방향. 같은 band 안에서도 세부 정책으로 약점·confidence를 반영한다.
    val band: CorrectionGrowthBand,
    // 사용자 문장을 어느 범위까지 바꿀 수 있는지.
    val scope: CorrectionScopePolicy,
    // 문법을 어디까지 고칠지.
    val grammar: GrammarCorrectionPolicy,
    // 어휘와 표현을 얼마나 확장할지.
    val vocabulary: VocabularyGrowthPolicy,
    // 문장 길이와 구조 확장을 얼마나 허용할지.
    val sentenceExpansion: SentenceExpansionPolicy,
    // 교정 문장의 말투.
    val register: RegisterCorrectionPolicy,
    // 교정 설명의 언어와 깊이.
    val explanation: CorrectionExplanationPolicy,
    // 새 표현을 얼마나 넣을 수 있는지.
    val newExpressionLimit: NewExpressionLimitPolicy,
    // 기준언어 보조 정도. Chat과 동일한 의미로 재사용한다.
    val primaryLanguageSupport: PrimaryLanguageSupportPolicy,
    // 의미 보존 강도. 모든 band에서 방어 조건이다.
    val meaningPreservation: MeaningPreservationPolicy
) {
    companion object {
        /**
         * [CorrectionGrowthBand]에 대한 기본 정책 매핑 (CHAT-TUNE-004 핸드오버 "Band별 기본 정책" 표).
         *
         * 이 기본값은 confidence·active focus 보수 조정 이전의 계약상 기본값이다.
         * 실제 산출에서는 [BuildLearnerAdaptationProfileUseCase]가 confidence·focus를 반영해 band를 낮출 수 있다.
         */
        fun defaultsForBand(band: CorrectionGrowthBand): CorrectionGrowthPolicy = when (band) {
            CorrectionGrowthBand.MeaningFirst -> CorrectionGrowthPolicy(
                band = band,
                scope = CorrectionScopePolicy.PreserveIntentOnly,
                grammar = GrammarCorrectionPolicy.FixBlockingErrorOnly,
                vocabulary = VocabularyGrowthPolicy.KeepUserWords,
                sentenceExpansion = SentenceExpansionPolicy.NoExpansion,
                register = RegisterCorrectionPolicy.Simple,
                explanation = CorrectionExplanationPolicy.PrimaryLanguageShort,
                newExpressionLimit = NewExpressionLimitPolicy.None,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.PrimaryLanguageFirst,
                meaningPreservation = MeaningPreservationPolicy.Strict
            )
            CorrectionGrowthBand.PatternFix -> CorrectionGrowthPolicy(
                band = band,
                scope = CorrectionScopePolicy.FixOneCoreIssue,
                grammar = GrammarCorrectionPolicy.FixOneMainPattern,
                vocabulary = VocabularyGrowthPolicy.AddOneUsefulWord,
                sentenceExpansion = SentenceExpansionPolicy.TinyPhraseOnly,
                register = RegisterCorrectionPolicy.Simple,
                explanation = CorrectionExplanationPolicy.PrimaryLanguageOneReason,
                newExpressionLimit = NewExpressionLimitPolicy.OneTinyWord,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.PrimaryLanguageFirst,
                meaningPreservation = MeaningPreservationPolicy.StrictWithTinyClarification
            )
            CorrectionGrowthBand.SentenceShape -> CorrectionGrowthPolicy(
                band = band,
                scope = CorrectionScopePolicy.FixMainIssueWithTinyExpansion,
                grammar = GrammarCorrectionPolicy.StabilizeBasicSentence,
                vocabulary = VocabularyGrowthPolicy.AddOneUsefulWord,
                sentenceExpansion = SentenceExpansionPolicy.OneShortSentence,
                register = RegisterCorrectionPolicy.EverydaySpoken,
                explanation = CorrectionExplanationPolicy.BilingualBrief,
                newExpressionLimit = NewExpressionLimitPolicy.OneUsefulPhrase,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.BriefPrimaryLanguageHint,
                meaningPreservation = MeaningPreservationPolicy.StrictWithTinyClarification
            )
            CorrectionGrowthBand.EverydayNatural -> CorrectionGrowthPolicy(
                band = band,
                scope = CorrectionScopePolicy.NaturalRewriteWithinSameMeaning,
                grammar = GrammarCorrectionPolicy.StabilizeBasicSentence,
                vocabulary = VocabularyGrowthPolicy.AddOneEverydayExpression,
                sentenceExpansion = SentenceExpansionPolicy.AddSimpleReasonOrDetail,
                register = RegisterCorrectionPolicy.EverydaySpoken,
                explanation = CorrectionExplanationPolicy.BilingualBrief,
                newExpressionLimit = NewExpressionLimitPolicy.OneNaturalExpression,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.BriefPrimaryLanguageHint,
                meaningPreservation = MeaningPreservationPolicy.SameMeaningNaturalized
            )
            CorrectionGrowthBand.ConnectedExpression -> CorrectionGrowthPolicy(
                band = band,
                scope = CorrectionScopePolicy.NaturalRewriteWithinSameMeaning,
                grammar = GrammarCorrectionPolicy.ImproveConnectedStructure,
                vocabulary = VocabularyGrowthPolicy.ImproveCollocation,
                sentenceExpansion = SentenceExpansionPolicy.AddSimpleReasonOrDetail,
                register = RegisterCorrectionPolicy.CasualNatural,
                explanation = CorrectionExplanationPolicy.TargetLanguageWithPrimaryFallback,
                newExpressionLimit = NewExpressionLimitPolicy.OneNaturalExpression,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.TargetLanguageFirstWithPrimaryFallback,
                meaningPreservation = MeaningPreservationPolicy.SameMeaningNaturalized
            )
            CorrectionGrowthBand.NuanceRefine -> CorrectionGrowthPolicy(
                band = band,
                scope = CorrectionScopePolicy.NuanceRewriteWithinSameMeaning,
                grammar = GrammarCorrectionPolicy.RefineAdvancedStructure,
                vocabulary = VocabularyGrowthPolicy.RefineNativeChoice,
                sentenceExpansion = SentenceExpansionPolicy.FlexibleNaturalDetail,
                register = RegisterCorrectionPolicy.NuanceAware,
                explanation = CorrectionExplanationPolicy.TargetLanguageNuance,
                newExpressionLimit = NewExpressionLimitPolicy.OneNuanceChoice,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.TargetLanguageOnly,
                meaningPreservation = MeaningPreservationPolicy.SameIntentWithNuance
            )
        }
    }
}

/**
 * 교정이 사용자 문장을 어느 범위까지 바꿀 수 있는지.
 */
enum class CorrectionScopePolicy {
    // 뜻이 통하도록 최소한만 정리한다.
    PreserveIntentOnly,
    // 가장 중요한 오류 하나만 고친다.
    FixOneCoreIssue,
    // 핵심 오류를 고치고 아주 작은 확장 하나만 허용한다.
    FixMainIssueWithTinyExpansion,
    // 의미를 유지하면서 더 자연스럽게 바꾼다.
    NaturalRewriteWithinSameMeaning,
    // 의미를 유지하면서 말투와 뉘앙스를 세밀하게 다듬는다.
    NuanceRewriteWithinSameMeaning
}

/**
 * 문법을 어디까지 고칠지.
 */
enum class GrammarCorrectionPolicy {
    // 의미 전달을 막는 오류만 고친다.
    FixBlockingErrorOnly,
    // 핵심 문법 패턴 하나만 고친다.
    FixOneMainPattern,
    // 기본 문장 구조를 안정화한다.
    StabilizeBasicSentence,
    // 이유, 조건, 상황 설명 연결을 다듬는다.
    ImproveConnectedStructure,
    // 고급 구조와 뉘앙스가 연결된 문법을 다룬다.
    RefineAdvancedStructure
}

/**
 * 어휘와 표현을 얼마나 확장할지.
 */
enum class VocabularyGrowthPolicy {
    // 사용자가 쓴 단어를 최대한 유지한다.
    KeepUserWords,
    // 유용한 단어 하나만 추가한다.
    AddOneUsefulWord,
    // 일상 표현 하나를 추가한다.
    AddOneEverydayExpression,
    // 더 자연스러운 단어 조합으로 다듬는다.
    ImproveCollocation,
    // 원어민이 더 자주 쓰는 선택으로 다듬는다.
    RefineNativeChoice
}

/**
 * 문장 길이와 구조 확장을 얼마나 허용할지.
 */
enum class SentenceExpansionPolicy {
    // 문장을 늘리지 않는다.
    NoExpansion,
    // 아주 짧은 구만 추가할 수 있다.
    TinyPhraseOnly,
    // 짧은 문장 하나 수준으로 정리한다.
    OneShortSentence,
    // 간단한 이유나 상황 설명 하나를 추가할 수 있다.
    AddSimpleReasonOrDetail,
    // 자연스러운 세부 설명을 제한적으로 허용한다. sourceText보다 과하게 길어지면 의미 변경 위험으로 본다.
    FlexibleNaturalDetail
}

/**
 * 교정 문장의 말투.
 */
enum class RegisterCorrectionPolicy {
    // 쉽고 단순한 말투를 우선한다.
    Simple,
    // 일상 대화에서 자연스러운 말투를 우선한다.
    EverydaySpoken,
    // 캐주얼하고 자연스러운 표현을 사용할 수 있다.
    CasualNatural,
    // 필요한 경우 공손한 표현 차이를 다룬다.
    PoliteWhenUseful,
    // 상황별 말투와 뉘앙스를 세밀하게 다룬다.
    NuanceAware
}

/**
 * 교정 설명의 언어와 깊이.
 */
enum class CorrectionExplanationPolicy {
    // primaryLang으로 아주 짧게 설명한다.
    PrimaryLanguageShort,
    // primaryLang으로 이유 하나만 설명한다.
    PrimaryLanguageOneReason,
    // 교정 표현은 selectedLang, 설명은 짧게 primaryLang으로 보조한다.
    BilingualBrief,
    // 기본은 selectedLang이지만 어려운 뉘앙스만 primaryLang으로 보조한다.
    TargetLanguageWithPrimaryFallback,
    // selectedLang 중심으로 뉘앙스까지 설명한다.
    TargetLanguageNuance
}

/**
 * 새 표현을 얼마나 넣을 수 있는지.
 *
 * 기본 원칙은 "한 번에 하나"이며, 낮은 단계에서는 새 표현보다 기존 의도 보존을 우선한다.
 */
enum class NewExpressionLimitPolicy {
    // 새 표현을 추가하지 않는다.
    None,
    // 아주 쉬운 단어 하나만 추가한다.
    OneTinyWord,
    // 유용한 짧은 표현 하나만 추가한다.
    OneUsefulPhrase,
    // 일상적으로 자주 쓰는 표현 하나를 추가한다.
    OneNaturalExpression,
    // 뉘앙스가 다른 표현 선택지 하나를 다룬다.
    OneNuanceChoice
}

/**
 * 의미 보존 강도. 모든 band에서 기본 방어 조건이다.
 *
 * 낮은 단계에서는 사용자가 말한 뜻을 임의로 확장하지 않는다.
 * 높은 단계에서도 더 멋진 문장을 만들기 위해 사용자 의도를 바꾸지 않는다.
 */
enum class MeaningPreservationPolicy {
    // 원래 뜻을 거의 그대로 유지한다.
    Strict,
    // 원래 뜻을 유지하되 아주 작은 명확화만 허용한다.
    StrictWithTinyClarification,
    // 같은 뜻을 더 자연스럽게 표현한다.
    SameMeaningNaturalized,
    // 같은 의도를 유지하면서 뉘앙스 차이를 다룬다.
    SameIntentWithNuance
}
