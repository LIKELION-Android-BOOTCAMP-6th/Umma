package com.example.umma.domain.model.learningstate

/**
 * 타입 A/B/C 분석이 공통으로 다루는 세션 발화 한 단위.
 *
 * LS-006에서는 실제 STT/턴 구조 전체를 강제하지 않고,
 * Language State 갱신에 필요한 최소 정보만 표현한다.
 */
data class ConversationTurn(
    // user / ai 중 누구의 발화인지.
    val speaker: TurnSpeaker,
    // 분석과 교정에 사용할 발화 텍스트.
    val text: String,
    // 발화 길이 계산 보조값.
    val tokenCount: Int? = null,
    // 발화가 실제로 이어진 시간.
    val durationMs: Long? = null,
    // STT confidence 같은 신뢰도 보조값.
    val confidence: Double? = null
)

/**
 * 분석 시점에 사용자가 남긴 발화 주체.
 */
enum class TurnSpeaker {
    USER,
    AI
}

/**
 * 교정 결과를 Language State 업데이트 입력으로 넘기기 위한 최소 표현.
 *
 * 이 모델은 화면용 교정 카드가 아니라, LS-006 업데이트 정책이 참조하는
 * 최소 입력 덩어리다. `CorrectionSuggestion`의 화면/저장 계약과는 역할이 다르다.
 */
data class CorrectionResult(
    // 교정된 최종 문장.
    val correctedText: String? = null,
    // 이번 분석에서 잡아낸 교정 수.
    val correctionCount: Int = 0,
    // 사람이 읽을 설명 메모.
    val notes: String? = null
)

/**
 * Flashcard 복습 이벤트를 idempotent 하게 반영하기 위한 최소 표현.
 */
data class FlashcardReviewEvent(
    // 중복 반영 방지를 위한 이벤트 식별자.
    val reviewEventId: String,
    // 어떤 카드에 대한 복습이었는지.
    val flashcardId: String,
    // 정답 여부.
    val isCorrect: Boolean,
    // 복습이 완료된 시각.
    val reviewedAt: Long,
    // 난이도 응답값.
    val difficulty: Int? = null
)

/**
 * Language State batch update에 필요한 입력 묶음.
 *
 * 실제 정책 계산은 도메인/유스케이스 계층에서 수행하고,
 * Repository는 preparedState를 원자적으로 저장하는 역할만 맡는다.
 */
data class LangStateUpdateInput(
    // 사용자 식별자.
    val uid: String,
    // 업데이트 대상 언어.
    val lang: LangCode,
    // 어떤 세션 메모리를 기준으로 계산했는지.
    val sessionMemoryKey: String,
    // 중복 분석 방지용 식별자.
    val analysisEventId: String?,
    // 계산 전 기준 상태.
    val currentState: LangState,
    // UseCase가 계산해 둔 최종 저장 후보.
    val preparedState: LangState? = null,
    // 이번 분석에 사용된 사용자 발화.
    val recentUserTurns: List<ConversationTurn>,
    // 교정 결과가 있으면 함께 반영한다.
    val correctionResult: CorrectionResult?,
    // 교정 완료처럼 correctionAvailable 값을 명시적으로 덮어써야 하는 경우 사용한다.
    val correctionAvailableOverride: Boolean? = null,
    // 플래시카드 복습 이벤트 묶음.
    val flashcardReviewEvents: List<FlashcardReviewEvent>,
    // 분석이 끝난 시각.
    val analyzedAt: Long,
    // 강제로 재분석해야 하는지 여부.
    val forceReanalysis: Boolean = false
)
