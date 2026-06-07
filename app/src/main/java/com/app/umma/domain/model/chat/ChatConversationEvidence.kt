package com.app.umma.domain.model.chat

import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.ProfileConfidence

/**
 * Chat 세션에서 관찰한 "대화 지속 능력" 근거.
 *
 * Gemini나 개발자가 추천 band를 직접 적용하지 않고, 이 근거를 LangState의
 * chatEvidenceSummary로 저장한 뒤 domain policy가 [ConversationAbilityBand]를 계산한다.
 * Firestore snapshot은 debug/review용 보조 자료로 유지한다.
 */
data class ChatConversationEvidence(
    // 이 evidence가 적용되는 학습 언어.
    val selectedLang: LangCode,
    // 사용자가 AI의 학습언어 발화를 어느 수준까지 이해하고 반응했는지.
    val targetLanguageComprehension: TargetLanguageComprehensionEvidence,
    // 사용자가 학습언어로 직접 만든 의미 단위. 기준언어 발화는 이 값에 더하지 않는다.
    val targetLanguageProduction: TargetLanguageProductionEvidence,
    // 기준언어가 없으면 학습언어 대화가 끊기는 정도.
    val supportLanguageDependence: LanguageDependenceEvidence,
    // AI가 힌트, 선택지, 쉬운 재구성으로 얼마나 많이 리드해야 했는지.
    val aiScaffoldingDependence: LanguageDependenceEvidence,
    // 사용자의 반응만으로 학습언어 대화가 유지됐는지에 대한 흐름 근거.
    val conversationSustainability: ConversationSustainabilityEvidence,
    // 한두 turn이 아니라 세션 전체에서 같은 능력 단서가 유지됐는지.
    val consistency: ConversationConsistencyEvidence,
    // AI 응답 난이도가 사용자가 감당 가능한 수준이었는지.
    val responseDifficultyFit: ResponseDifficultyFitEvidence,
    // 이 evidence를 band 계산에 반영할 수 있는 신뢰도.
    val confidence: ProfileConfidence,
    // 수동 판단, 신고 세션 분석, Gemini 분석 등 evidence의 출처.
    val source: ChatConversationEvidenceSource,
    // evidence 근거가 된 대화 세션. 수동 입력이면 null일 수 있다.
    val sourceSessionId: String? = null,
    // review/debug용 요약. prompt에는 넣지 않는다.
    val reasonSummary: String? = null,
    // Gemini/개발자가 남긴 참고 band 후보. 앱은 이 값을 그대로 적용하지 않는다.
    val debugRecommendedBand: ConversationAbilityBand? = null,
    // 마지막 갱신 시각.
    val updatedAt: Long? = null,
    // 오래된 snapshot을 review 도구에서 구분하기 위한 만료 시각. 공식 Chat band source에는 직접 쓰지 않는다.
    val expiresAt: Long? = null
)

/**
 * 사용자가 AI의 학습언어 발화를 이해하고 적절히 반응한 수준.
 */
@Suppress("unused")
enum class TargetLanguageComprehensionEvidence {
    // 학습언어 이해 근거가 거의 없다.
    None,
    // 단어 또는 아주 짧은 표현에는 반응했다.
    WordLevel,
    // 쉬운 한 문장 수준은 이해하고 반응했다.
    SimpleSentence,
    // 자연스러운 학습언어 흐름을 이해하고 이어 갔다.
    NaturalFlow
}

/**
 * 사용자가 학습언어로 직접 생산한 의미 단위.
 */
@Suppress("unused")
enum class TargetLanguageProductionEvidence {
    // 의미 있는 학습언어 생산이 거의 없다.
    None,
    // 단어, 짧은 소리, 조각 표현 중심이다.
    WordsOrFragments,
    // 짧은 구나 고정 표현 중심이다.
    ShortPhrases,
    // 짧고 단순한 자유 문장을 만들었다.
    SimpleSentences,
    // 이유, 감정, 상황을 연결한 여러 turn을 학습언어로 만들었다.
    ConnectedTurns
}

/**
 * 기준언어 또는 AI scaffold에 대한 의존도.
 */
@Suppress("unused")
enum class LanguageDependenceEvidence {
    // 없으면 대화가 거의 이어지지 않는다.
    High,
    // 몇 차례 필요했고 없으면 흐름이 불안정하다.
    Medium,
    // 가끔 짧은 도움만 필요했다.
    Low,
    // 별도 의존 없이 이어졌다.
    None
}

/**
 * 대화가 사용자의 반응으로 유지됐는지에 대한 근거.
 */
@Suppress("unused")
enum class ConversationSustainabilityEvidence {
    // 기준언어 보조나 난이도 하향 없이는 대화가 거의 이어지지 않았다.
    RequiresSupport,
    // 보조가 있으면 짧게 이어졌지만, 학습언어-only 흐름은 불안정했다.
    SupportedShort,
    // 쉬운 학습언어 흐름에서는 짧은 왕복 대화가 유지됐다.
    SustainedSimple,
    // 자연스러운 학습언어 흐름에서도 대화가 안정적으로 유지됐다.
    SustainedNatural
}

/**
 * 세션 전체에서 능력 단서가 얼마나 일관됐는지.
 */
@Suppress("unused")
enum class ConversationConsistencyEvidence {
    // 학습언어 근거가 너무 적거나 한두 turn에만 있다.
    Low,
    // 일부 turn은 가능했지만 세션 전체에서는 흔들렸다.
    Mixed,
    // 세션 전반에서 비슷한 수준이 안정적으로 반복됐다.
    Stable
}

/**
 * AI 응답 난이도가 사용자에게 맞았는지에 대한 근거.
 */
@Suppress("unused")
enum class ResponseDifficultyFitEvidence {
    // AI 응답이 어려워 대화가 끊기거나 기준언어 보조가 필요했다.
    TooHard,
    // 약간 어렵지만 짧은 보조로 따라올 수 있었다.
    SlightlyHard,
    // 사용자가 감당 가능한 난이도였다.
    Fits,
    // 너무 쉬워 대화 확장을 막는 근거가 보였다.
    TooEasy,
    // 판단 근거가 부족하다.
    Unknown
}

/**
 * Chat conversation evidence의 생성 출처.
 */
@Suppress("unused")
enum class ChatConversationEvidenceSource {
    // 개발자가 Firestore에서 직접 입력하거나 수정한 값.
    ManualReview,
    // 신고된 세션을 사람이 검토해 반영한 값.
    ReportedSessionReview,
    // 세션 종료 후 Gemini 분석으로 생성한 값.
    GeminiConversationAnalysis
}
