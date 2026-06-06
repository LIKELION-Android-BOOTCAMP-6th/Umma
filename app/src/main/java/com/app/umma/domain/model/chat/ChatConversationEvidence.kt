package com.app.umma.domain.model.chat

import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.ProfileConfidence

/**
 * Chat 세션에서 관찰한 "대화 지속 능력" 근거.
 *
 * Gemini나 개발자가 band를 직접 저장하지 않고, 이 근거를 domain policy가 해석해
 * [ConversationAbilityBand]를 계산한다. 1차에서는 Chat 시작 prompt/profile에만 사용하고,
 * LangState/Correction/Statistics에는 전달하지 않는다.
 */
data class ChatConversationEvidence(
    // 이 evidence가 적용되는 학습 언어.
    val selectedLang: LangCode,
    // 사용자의 반응만으로 학습언어 대화가 유지됐는지에 대한 흐름 근거.
    val conversationSustainability: ConversationSustainabilityEvidence,
    // 기준언어 보조나 난이도 하향이 있어야 대화가 회복됐는지.
    val supportRequiredToContinue: SupportRequiredEvidence,
    // 사용자가 실제로 대화에 참여한 발화 단위.
    val userContributionLevel: UserContributionEvidence,
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
    // 오래된 evidence를 무조건 신뢰하지 않기 위한 만료 시각.
    val expiresAt: Long? = null
) {
    /**
     * 세션 시작에 반영 가능한 evidence인지 확인한다.
     *
     * Low confidence는 테스트/debug 근거로는 남길 수 있지만, 실제 prompt band를 바꾸기에는
     * 흔들림이 크므로 1차 적용에서 제외한다.
     */
    fun isUsable(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val isExpired = expiresAt?.let { it <= nowMillis } == true
        return !isExpired && confidence != ProfileConfidence.Low
    }
}

/**
 * 대화가 사용자의 반응으로 유지됐는지에 대한 근거.
 */
@Suppress("unused")
enum class ConversationSustainabilityEvidence {
    // 기준언어 보조나 난이도 하향 없이는 대화가 거의 이어지지 않았다.
    RequiresSupport,
    // 보조가 있으면 짧게 이어졌지만, 학습언어-only 흐름은 불안정했다.
    SupportedWithHints,
    // 쉬운 학습언어 흐름에서는 짧은 왕복 대화가 유지됐다.
    SustainedSimple,
    // 자연스러운 학습언어 흐름에서도 대화가 안정적으로 유지됐다.
    SustainedNatural
}

/**
 * 대화를 계속하기 위해 필요했던 보조 강도.
 */
@Suppress("unused")
enum class SupportRequiredEvidence {
    // 기준언어/난이도 하향이 반복적으로 필요했다.
    High,
    // 몇 차례 보조가 필요했지만 회복 가능했다.
    Moderate,
    // 가끔 짧은 힌트만 필요했다.
    Low,
    // 별도 보조 없이 이어졌다.
    None
}

/**
 * 사용자가 실제 대화에 기여한 발화 단위.
 */
enum class UserContributionEvidence {
    // 의미 있는 학습언어 반응이 거의 없거나 기준언어 반응 위주였다.
    Minimal,
    // 단어, 짧은 소리, 조각 표현 중심이었다.
    WordsOrFragments,
    // 짧은 구나 고정 표현으로 반응했다.
    ShortPhrases,
    // 짧고 단순한 자유 문장으로 반응했다.
    SimpleSentences,
    // 이유, 감정, 상황을 연결한 여러 turn을 만들었다.
    ConnectedTurns
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
