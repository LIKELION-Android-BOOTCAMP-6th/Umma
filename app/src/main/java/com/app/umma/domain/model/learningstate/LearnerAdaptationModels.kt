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
    // Chat에서만 쓰는 전체 대화 가능 단계. Correction의 ChallengeLevel과 분리해 대화 흐름을 더 세밀하게 조절한다.
    val conversationBand: ConversationAbilityBand,
    // 사용자의 불완전한 발화를 AI가 얼마나 적극적으로 의도 추론할지.
    val intentSupport: IntentSupportPolicy,
    // primaryLang을 어느 정도 섞어 사용자의 이해 단절을 막을지.
    val primaryBridge: PrimaryBridgePolicy,
    // 사용자가 말하려던 뜻을 응답 안에서 어떤 강도로 자연스럽게 다시 보여줄지.
    val recastStyle: RecastStylePolicy,
    // 현재 능력보다 약간 높은 표현을 얼마나 노출할지.
    val expressionGrowth: ExpressionGrowthPolicy,
    // 사용자가 다음 turn을 만들 때 받을 응답 부담.
    val questionLoad: QuestionLoadPolicy,
    // AI 답변을 얼마나 길게 만들지.
    val responseLength: ResponseLengthPolicy,
    // Realtime 음성 출력 속도 정책. prompt보다 실제 audio 설정에서 우선 사용한다.
    val speechSpeed: SpeechSpeedPolicy
)

/**
 * 현재 사용자 turn 하나에만 적용할 Chat 임시 보정 정책.
 *
 * 장기 실력 판단은 [LearnerAdaptationProfile]이 담당하고, 이 모델은 방금 들어온
 * USER final transcript가 보여준 순간적인 막힘/회복 신호를 이번 AI 응답에만 반영한다.
 * 따라서 이 값은 저장하지 않고, `response.create.instructions`와 필요한 경우 `session.update`의 speed에만 사용한다.
 */
data class ChatTurnAdaptationPolicy(
    // 이번 응답 길이. 긴 응답은 초저숙련 사용자의 다음 turn을 막을 수 있어 turn 단위로 낮출 수 있다.
    val responseLength: ResponseLengthPolicy,
    // 이번 응답의 정보 밀도. 실제 audio speed가 느려도 정보량이 많으면 이해가 어려워 별도 정책으로 둔다.
    val sentenceDensity: SentenceDensityPolicy,
    // 이번 응답에서 primaryLang을 얼마나 섞을지. 장기 profile보다 현재 발화의 막힘 신호를 우선한다.
    val primaryBridge: PrimaryBridgePolicy,
    // 이번 응답의 후속 여지 부담. 사용자가 단어 하나로도 이어갈 수 있게 낮출 수 있다.
    val questionLoad: QuestionLoadPolicy,
    // 이번 응답의 실제 음성 속도 정책. speed 값이 바뀔 때만 session.update로 전달한다.
    val speechSpeed: SpeechSpeedPolicy,
    // primaryLang 보조가 열린 이유. 같은 Active라도 profile 기본값인지 사용자 요청인지에 따라 override 필요성이 다르다.
    val primaryBridgeReason: PrimaryBridgeReason = PrimaryBridgeReason.ProfileDefault
)

/**
 * 이번 turn에서 primaryLang 보조가 필요한 이유.
 */
enum class PrimaryBridgeReason {
    // 세션 시작 profile이 정한 기본 보조 강도다. baseline과 같으면 override를 반복하지 않는다.
    ProfileDefault,
    // 사용자가 "한국어를 섞어줘"처럼 기준언어 보조를 직접 요청했다. baseline과 같아도 이번 응답에 짧게 재강조한다.
    ExplicitSupportRequest
}

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
 * Chat 전용 대화 가능 단계.
 *
 * 내부 판단용 이름이며 prompt나 사용자 노출 문구에 그대로 넣지 않는다.
 */
enum class ConversationAbilityBand {
    // 단어 조각이나 기준언어 혼합 발화에서 의도 복원이 먼저 필요한 단계.
    IntentOnly,
    // 짧은 구와 고정 표현은 가능하지만 문장 구성은 아직 불안정한 단계.
    PhraseEmerging,
    // 짧은 문장은 가능하지만 어순, 시제, 기본 문법이 자주 흔들리는 단계.
    SimpleSentence,
    // 짧은 일상 왕복 대화와 이유/선호 질문을 감당할 수 있는 단계.
    BasicConversation,
    // 이유, 감정, 상황 설명을 연결하며 표현 폭을 넓힐 수 있는 단계.
    ConnectedExpression,
    // 의미 전달은 안정적이고 뉘앙스와 원어민식 표현이 성장 지점인 단계.
    NuanceControl
}

/**
 * 불완전한 발화에서 AI가 의도를 얼마나 먼저 복원할지 정한다.
 */
enum class IntentSupportPolicy {
    // 단어 조각만 있어도 가능한 의도를 먼저 추론한다.
    InferActively,
    // 오해 가능성이 크면 짧게 의도를 확인한다.
    ConfirmBriefly,
    // 의미가 보이면 별도 확인 없이 대화를 이어간다.
    TrustMeaning,
    // 고급 사용자의 흐름을 AI가 앞서 해석하지 않고 따라간다.
    FollowUserLead
}

/**
 * Chat에서 primaryLang을 섞는 강도.
 */
enum class PrimaryBridgePolicy {
    // 이해가 끊기지 않도록 짧은 primaryLang 확인을 적극 허용한다.
    Active,
    // 막힌 순간에만 핵심 힌트를 짧게 제공한다.
    Brief,
    // 기본은 selectedLang이고 오해/단절 시에만 fallback으로 쓴다.
    FallbackOnly,
    // 사용자가 요청하지 않으면 primaryLang을 쓰지 않는다.
    None
}

/**
 * 사용자가 말하려던 뜻을 응답 안에서 다시 보여주는 방식.
 */
enum class RecastStylePolicy {
    // 단어 또는 아주 짧은 문장으로 의미를 한 번만 복원한다.
    TinyInline,
    // 쉬운 문장 하나로 자연스럽게 다시 표현한다.
    SimpleInline,
    // 실제 대화 문장 안에 자연스러운 표현을 섞는다.
    NaturalInline,
    // 고급 사용자에게만 뉘앙스 차이를 자연스럽게 반영한다.
    NuanceOnly
}

/**
 * 현재 능력보다 조금 높은 표현을 노출하는 폭.
 */
enum class ExpressionGrowthPolicy {
    // 이해 가능한 아주 작은 표현 하나만 보여준다.
    OneTinyPhrase,
    // 쉬운 일상 패턴 하나를 보여준다.
    OneSimplePattern,
    // 실제 자주 쓰는 일상 표현 하나를 자연스럽게 섞는다.
    OneEverydayExpression,
    // 원어민다운 선택지를 필요할 때만 미세하게 보여준다.
    OneNativeLikeChoice
}

/**
 * 다음 발화를 만들 때의 질문 부담.
 */
enum class QuestionLoadPolicy {
    // 한 단어나 선택지로 답할 수 있게 묻는다.
    ConcreteChoice,
    // 짧은 실제 follow-up 하나만 묻는다.
    OneConcreteFollowUp,
    // 너무 넓지 않은 짧은 open question을 묻는다.
    OpenShort,
    // 대화가 안정될 때만 뉘앙스나 이유를 묻는다.
    NuanceFollowUp
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
    // 짧은 답변에 필요한 경우 가벼운 후속 여지를 둔다.
    ShortTwoStep,
    // 자연스럽지만 장황하지 않은 답변을 제공한다.
    NaturalBrief,
    // 고급 사용자에게 필요한 설명과 예시를 더 유연하게 허용한다.
    Flexible
}

/**
 * AI 응답 한 번에 담을 의미/정보량.
 */
enum class SentenceDensityPolicy {
    // 한 응답에 하나의 의미만 담아 사용자가 바로 이해하고 답할 수 있게 한다.
    OneIdea,
    // 짧은 반응과 후속 여지처럼 두 단계까지 허용한다.
    SimpleTwoStep,
    // 일반적인 짧은 대화 밀도를 허용한다.
    NaturalBrief,
    // 고급 사용자가 감당할 수 있을 때만 조금 더 풍부한 설명을 허용한다.
    Flexible
}

/**
 * AI 음성 응답 속도 정책.
 */
enum class SpeechSpeedPolicy {
    // 첫 대화나 초저숙련 단계에서 듣기 쉬운 느린 속도.
    SlowBeginner,
    // 초급 사용자가 이해할 수 있는 속도.
    Guided,
    // 일반 학습 대화 속도.
    NormalLearning,
    // 능숙한 사용자를 위한 약간 빠른 속도.
    SlightlyFast,
    // 고급 사용자를 위한 상한 내 빠른 속도.
    Advanced
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
