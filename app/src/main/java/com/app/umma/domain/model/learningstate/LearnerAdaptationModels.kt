package com.app.umma.domain.model.learningstate

/**
 * Chat과 Correction이 함께 읽는 학습자 적응 프로필.
 *
 * 이 모델은 저장 모델이 아니라 `LangState`를 AI 기능이 안전하게 사용할 수 있도록
 * 정책 enum으로 압축한 domain read model이다.
 */
data class LearnerAdaptationProfile(
    // Chat/Correction이 공통으로 공유하는 능력 해석 결과.
    val core: LearnerAbilityProfile,
    // Chat prompt builder가 대화 길이와 질문 방식을 정할 때 쓰는 정책.
    val chatPolicy: ChatAdaptationPolicy,
    // Correction prompt builder가 교정 강도와 설명 방식을 정할 때 쓰는 정책.
    val correctionPolicy: CorrectionAdaptationPolicy
)

/**
 * 저장된 LangState를 교육 전략으로 바꾸기 전의 공통 능력 판단.
 *
 * raw metric 숫자를 노출하지 않고 stage/confidence/focus처럼 prompt 정책으로
 * 바로 바꿀 수 있는 값만 담는다.
 */
data class LearnerAbilityProfile(
    // CEFR 어휘 레벨은 이미 domain enum이므로 profile의 큰 난이도 단서로만 사용한다.
    val cefrLevel: VocabLevel,
    // CEFR 레벨을 얼마나 믿고 challenge에 반영할 수 있는지.
    val levelConfidence: ProfileConfidence,
    // 문법 정확도와 문장 오류 대응 능력의 단계.
    val grammarStage: SkillStage,
    // 어휘 선택, 다양성, 표현 폭을 함께 본 어휘 단계.
    val vocabularyStage: SkillStage,
    // 발화 속도, 머뭇거림, 발화 길이를 함께 본 유창성 단계.
    val fluencyStage: SkillStage,
    // 구어체 자연스러움과 표현 선택을 함께 본 자연스러움 단계.
    val naturalnessStage: SkillStage,
    // 최근 반복 약점 중 prompt에 반영해도 되는 요약 focus.
    val focus: LearningFocusSummary
)

/**
 * 저장된 activeFocus를 prompt가 바로 사용할 수 있는 크기로 줄인 값.
 *
 * `analysisMeta.activeFocus` 전체를 prompt builder가 직접 순회하면 prompt가 비대해지고,
 * 오래된 focus까지 노출될 수 있으므로 여기에서 상위 1~2개로 제한한다.
 */
data class LearningFocusSummary(
    // 가장 먼저 도와줄 반복 약점. 없으면 현재 profile에서 focus를 강제하지 않는다.
    val primaryFocus: LearningFocusType?,
    // 보조로 참고할 두 번째 약점. prompt builder는 필요할 때만 사용한다.
    val secondaryFocus: LearningFocusType?,
    // focus 요약 자체를 얼마나 믿을 수 있는지.
    val confidence: ProfileConfidence,
    // 선택된 focus들이 총 몇 번 관측됐는지.
    val observedCount: Int
)

/**
 * Chat 대화에서 사용할 적응 정책.
 */
data class ChatAdaptationPolicy(
    // 사용자의 현재 능력보다 얼마나 도전적인 대화를 허용할지.
    val challengeLevel: ChallengeLevel,
    // AI 답변을 얼마나 길게 만들지.
    val responseLength: ResponseLengthPolicy,
    // 사용자가 다음 말을 이어가기 쉽도록 어떤 질문을 던질지.
    val questionStyle: QuestionStylePolicy,
    // 설명/힌트에 primaryLang을 얼마나 쓸 수 있는지.
    val primaryLanguageSupport: PrimaryLanguageSupportPolicy
)

/**
 * Correction 교정에서 사용할 적응 정책.
 */
data class CorrectionAdaptationPolicy(
    // 교정 결과를 현재 능력에 맞출지, 약간 확장할지 정하는 공통 challenge.
    val challengeLevel: ChallengeLevel,
    // 교정 설명을 얼마나 자세히 제공할지.
    val correctionStyle: CorrectionStylePolicy,
    // 어휘를 단순 유지할지, 표현을 조금 확장할지.
    val vocabularyStrategy: VocabularyStrategyPolicy,
    // 문법 오류만 고칠지, 문장 구조까지 확장할지.
    val grammarStrategy: GrammarStrategyPolicy,
    // 구어체/격식체 register를 어느 정도 다룰지.
    val spokenRegisterStrategy: SpokenRegisterStrategy,
    // 교정 설명에서 primaryLang을 얼마나 보조로 쓸지.
    val primaryLanguageSupport: PrimaryLanguageSupportPolicy
)

/**
 * metric 숫자를 바로 prompt에 넣지 않기 위한 skill 단계.
 */
enum class SkillStage {
    // 짧고 쉬운 패턴 중심의 지원이 필요한 단계.
    Foundation,
    // 기본 문장을 만들 수 있지만 한 번에 하나씩 확장이 필요한 단계.
    Developing,
    // 기본 대화는 안정적이고 자연스러운 후속 질문을 받을 수 있는 단계.
    Stable,
    // 다양한 표현, 연결어, 구어체 표현을 조금씩 늘릴 수 있는 단계.
    Expanding,
    // 뉘앙스, register, collocation까지 다듬을 수 있는 단계.
    Refined
}

/**
 * Chat/Correction이 현재 능력보다 얼마나 더 밀어도 되는지.
 */
enum class ChallengeLevel {
    // 이해와 안정감을 우선하고 새 표현은 거의 넣지 않는다.
    Support,
    // 현재 능력에 맞춰 대화와 교정을 제공한다.
    Match,
    // 현재 능력보다 조금 높은 표현을 하나씩 제안한다.
    Stretch,
    // 고급 사용자를 대상으로 뉘앙스와 register까지 다듬는다.
    Refine
}

/**
 * profile 판단에 사용할 신뢰도.
 */
enum class ProfileConfidence {
    // 분석 이력이 부족하거나 지표가 서로 충돌해 보수적으로 해석해야 한다.
    Low,
    // 일부 반복 근거가 있지만 아직 높은 challenge를 확정하기에는 부족하다.
    Medium,
    // 여러 근거가 같은 방향으로 누적돼 profile 정책을 적극 반영할 수 있다.
    High
}

/**
 * Chat 응답 길이 정책.
 */
enum class ResponseLengthPolicy {
    // 한 번에 짧은 한 문장으로 답한다.
    OneShortSentence,
    // 짧은 답변과 쉬운 후속 질문 하나를 제공한다.
    ShortTwoStep,
    // 자연스럽지만 장황하지 않은 답변을 제공한다.
    NaturalBrief,
    // 고급 사용자에게 필요한 설명과 예시를 더 유연하게 허용한다.
    Flexible
}

/**
 * Chat follow-up 질문 방식.
 */
enum class QuestionStylePolicy {
    // yes/no 또는 짧은 답이 가능한 구체 질문을 우선한다.
    OneConcreteQuestion,
    // 선택지를 줘 사용자가 다음 발화를 만들 수 있게 돕는다.
    GuidedChoiceQuestion,
    // 이유, 경험, 선호를 묻는 자연스러운 후속 질문을 사용한다.
    OpenFollowUp,
    // register, 뉘앙스, 더 자연스러운 표현 선택을 유도한다.
    NuanceFollowUp
}

/**
 * Correction 설명 방식.
 */
enum class CorrectionStylePolicy {
    // 의미 전달을 막는 핵심 오류만 최소 수정한다.
    MinimalFix,
    // 한 가지 주요 이유만 짧게 설명한다.
    ExplainOneReason,
    // 자연스러운 구어체 rewrite를 제안한다.
    NaturalSpokenRewrite,
    // 뉘앙스와 register 차이까지 설명한다.
    NuanceAndRegister
}

/**
 * Correction 어휘 전략.
 */
enum class VocabularyStrategyPolicy {
    // 사용자가 이미 아는 쉬운 단어를 유지한다.
    KeepSimpleWords,
    // 유용한 표현 하나만 추가한다.
    AddOneUsefulExpression,
    // collocation과 자연스러운 조합을 개선한다.
    ImproveCollocation,
    // 고급 사용자를 위해 원어민식 단어 선택을 다듬는다.
    RefineNativeChoice
}

/**
 * Correction 문법 전략.
 */
enum class GrammarStrategyPolicy {
    // 의미 전달을 막는 오류만 우선 고친다.
    FixBlockingErrorOnly,
    // 한 번에 하나의 주요 문법 패턴을 설명한다.
    FixOneMainPattern,
    // 문장 구조를 조금 더 자연스럽게 확장한다.
    ExpandSentenceStructure,
    // 고급 문장 구조를 더 정교하게 다듬는다.
    RefineAdvancedStructure
}

/**
 * Correction 구어체/격식체 register 전략.
 */
enum class SpokenRegisterStrategy {
    // 단순하고 직접적인 표현을 우선한다.
    Simple,
    // 일상 대화에서 자연스러운 구어체를 제안한다.
    EverydaySpoken,
    // 원어민이 실제로 자주 쓰는 캐주얼 표현을 제안한다.
    NativeLikeCasual,
    // 상황에 따라 격식 표현도 구분해 준다.
    FormalWhenNeeded
}

/**
 * primaryLang 보조 설명 정책.
 */
enum class PrimaryLanguageSupportPolicy {
    // 설명과 힌트를 primaryLang 중심으로 제공한다.
    PrimaryLanguageFirst,
    // 핵심 힌트만 primaryLang으로 짧게 보조한다.
    BriefPrimaryLanguageHint,
    // 기본은 selectedLang으로 하되 막힐 때만 primaryLang 보조를 허용한다.
    TargetLanguageFirstWithPrimaryFallback,
    // selectedLang만 사용하고 primaryLang 보조 설명은 넣지 않는다.
    TargetLanguageOnly
}
