package com.app.umma.domain.model.learningstate

import com.app.umma.domain.model.chat.ChatConversationEvidence

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
    val notes: String? = null,
    // 선택된 교정 후보들에서 모은 관찰 학습 신호.
    // 신호 계약 자체는 CorrectionLearningSignalModels.kt에 두어 update input 묶음과 책임을 분리한다.
    // LearningState 분석 정책은 이 신호를 metric evidence, active focus, score guard 입력으로 소비한다.
    val learningSignals: List<CorrectionLearningSignal> = emptyList()
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
 * Repository는 preparedState와 preparedSummary를 원자적으로 저장하는 역할만 맡는다.
 *
 * Summary 계산(delta/분 환산/correctionAvailable) 책임은 UseCase 계층에 있다.
 * Repository는 계산된 준비 값을 받아 저장만 수행하며, 직접 계산하지 않는다.
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
    // UseCase가 RecalculateFromInput 정책에 따라 계산해 둔 DashSummary 저장 후보.
    // null이면 Repository가 기존 Summary를 보존한다(PreserveExisting 또는 비정상 입력 안전 처리).
    val preparedDashSummary: DashSummary? = null,
    // UseCase가 RecalculateFromInput 정책에 따라 계산해 둔 SessionSummary 저장 후보.
    // null이면 Repository가 기존 Summary를 보존한다(PreserveExisting 또는 비정상 입력 안전 처리).
    val preparedSessionSummary: SessionSummary? = null,
    // 이번 분석에 사용된 사용자 발화.
    val recentUserTurns: List<ConversationTurn>,
    // 교정 결과가 있으면 함께 반영한다.
    val correctionResult: CorrectionResult?,
    // 교정 완료처럼 correctionAvailable 값을 명시적으로 덮어써야 하는 경우 사용한다.
    val correctionAvailableOverride: Boolean? = null,
    // 교정 완료 흐름에서 dash/session summary 의 recentTopic 을 함께 갱신할 때 사용한다.
    // null 이면 기존 값을 보존한다 — 부분 갱신 호출자가 주제를 지우지 않도록.
    val recentTopic: String? = null,
    // 플래시카드 복습 이벤트 묶음.
    val flashcardReviewEvents: List<FlashcardReviewEvent>,
    // 분석이 끝난 시각.
    val analyzedAt: Long,
    // LangState 저장과 함께 화면/세션 요약을 다시 계산할지 정한다.
    // Correction batch update는 기존처럼 summary를 함께 갱신하지만,
    // Chat evidence update는 LangState 근거만 누적해야 하므로 기존 summary를 보존한다.
    val summaryUpdatePolicy: LangStateSummaryUpdatePolicy = LangStateSummaryUpdatePolicy.RecalculateFromInput,
    // 강제로 재분석해야 하는지 여부.
    val forceReanalysis: Boolean = false
)

/**
 * LangState update가 Dashboard/Session Summary까지 함께 갱신할지에 대한 저장 정책.
 */
enum class LangStateSummaryUpdatePolicy {
    // 기존 Correction batch update 계약. 분석 입력으로 recentMinutes/delta/correctionAvailable을 다시 계산한다.
    RecalculateFromInput,
    // Chat evidence처럼 LangState 근거만 저장하고 기존 Summary 값은 그대로 둔다.
    PreserveExisting
}

/**
 * Language State 저장이 끝난 뒤 후속 Flow가 참조할 수 있는 완료 결과.
 *
 * StatisticsHistory 기록처럼 "저장된 LangState의 최종 값"이 필요한 흐름은
 * Repository 내부 상태를 다시 파고들지 않고 이 결과만 이어받는다.
 */
data class LearningStateUpdateResult(
    // 업데이트 대상 언어.
    val lang: LangCode,
    // 저장 또는 중복 처리 후 최종 기준이 되는 Language State.
    val savedState: LangState,
    // StatisticsHistory 중복 방지에 사용할 source event.
    val sourceEventId: String,
    // 실제로 새 값이 반영됐는지, 중복 이벤트라 skip 됐는지 구분한다.
    val applied: Boolean,
    // 결과가 확정된 시각.
    val updatedAt: Long
)

/**
 * SRS 복습 완료 후 Summary만 갱신할 때 사용하는 입력.
 *
 * Due 카드 수 계산은 Flashcard/SRS 책임이고, LearningState는 계산된 숫자를
 * 전역 요약 스냅샷에 local-first로 반영하는 경계만 담당한다.
 */
data class FlashcardSummaryUpdateInput(
    // 사용자 식별자. 현재 DataStore 구현은 uid별 파일 분리를 하지 않지만 원격 sync 계약을 위해 보존한다.
    val uid: String,
    // 갱신 대상 학습 언어.
    val lang: LangCode,
    // SRS가 계산한 오늘 복습 대상 카드 수.
    val dueFlashcards: Int,
    val notifiableDueFlashcards: Int = 0,
    // 저장된 전체 카드 수.
    val savedFlashcards: Int,
    // 갱신 이벤트 식별자. 같은 이벤트가 반복 반영되는 것을 막는 데 사용한다.
    val sourceEventId: String?,
    // 갱신 시각.
    val updatedAt: Long
)

/**
 * Chat turn 확정 후 correctionAvailable 신호만 올릴 때 사용하는 최소 입력.
 *
 * 이 입력은 full LangState 분석을 다시 돌리지 않고, 세션/대시보드 요약만
 * local-first로 갱신하기 위한 lightweight 계약이다.
 *
 * 왜 uid / sessionMemoryKey / sourceEventId가 따로 필요한가:
 *  - uid: 어떤 사용자 스냅샷을 갱신할지 식별한다.
 *  - sessionMemoryKey: 같은 대화 세션에서 나온 신호인지 경계한다.
 *  - sourceEventId: 같은 turn 재시도에서 중복 반영을 막는다.
 *
 * `correctionAvailable` 값 자체는 UseCase/caller가 결정한다. Repository는 이 값을 보고
 * "켜야 하는지/꺼야 하는지"를 다시 판단하지 않고 summary에 저장만 한다.
 */
data class CorrectionSignalUpdateInput(
    // 사용자 식별자.
    val uid: String,
    // 갱신 대상 학습 언어.
    val lang: LangCode,
    // 어떤 Session Memory 스코프에서 나온 신호인지 추적하기 위한 키.
    val sessionMemoryKey: String,
    // 같은 turn 재시도나 stale 호출을 구분하기 위한 이벤트 식별자.
    val sourceEventId: String,
    // 이 신호가 summary에 저장하려는 교정 가능 여부. 기본 Chat final turn 신호는 true다.
    val correctionAvailable: Boolean = true,
    // 필요 시 최근 대화 길이만 함께 덮어쓴다.
    val recentMinutes: Int? = null,
    // 필요 시 최근 주제도 함께 덮어쓴다.
    val recentTopic: String? = null,
    // 신호가 확정된 시각.
    val updatedAt: Long
)

/**
 * Chat 세션 후 Gemini가 만든 대화 능력 evidence를 LangState 근거로 반영하기 위한 입력.
 *
 * 이 입력은 CorrectionResult와 섞이지 않는다. Chat source는 점수 자체가 아니라
 * analysisMeta.metricEvidence에 source가 분리된 근거를 남기는 데만 사용한다.
 */
data class ChatSignalUpdateInput(
    // 사용자 식별자. sessionMemoryKey와 중복 방어 event id의 계정 경계를 만든다.
    val uid: String,
    // 갱신 대상 학습 언어.
    val lang: LangCode,
    // 어떤 Session Memory 스코프에서 나온 분석인지 추적하기 위한 키.
    val sessionMemoryKey: String,
    // Gemini 분석의 source가 된 Chat session id.
    val sourceSessionId: String,
    // Gemini가 산출한 구조화 evidence.
    val evidence: ChatConversationEvidence,
    // summary 부작용이 잘못 꺼지지 않도록 저장 확정된 USER turn만 전달한다.
    val recentUserTurns: List<ConversationTurn>,
    // 분석이 끝난 시각.
    val analyzedAt: Long,
    // 개발/재분석 상황에서 같은 session id를 다시 반영해야 할 때만 사용한다.
    val forceReanalysis: Boolean = false
)

/**
 * FlashcardSummary와 DashSummary가 함께 갱신된 결과.
 */
data class FlashcardSummaryUpdateResult(
    // 갱신 대상 언어.
    val lang: LangCode,
    // SRS 카드가 직접 참조하는 요약.
    val flashcardSummary: FlashcardSummary,
    // Dashboard 카드가 빠르게 읽는 요약.
    val dashSummary: DashSummary,
    // 실제 반영 여부.
    val applied: Boolean,
    // 중복 방지용 이벤트 식별자.
    val sourceEventId: String?,
    // 갱신 시각.
    val updatedAt: Long
)

/**
 * correctionAvailable signal 갱신이 끝난 뒤의 결과.
 *
 * Dashboard와 Correction이 서로 다른 summary를 읽더라도 같은 신호를 보게 되도록
 * SessionSummary / DashSummary를 함께 돌려준다.
 *
 * 이 결과는 "LS가 내부적으로 어떤 summary를 함께 맞췄는지"를 호출자에게 보여주는
 * 기록용 계약이다. 화면은 여기서 계산하지 않고, 자기 역할에 맞는 summary만 읽는다.
 */
data class CorrectionSignalUpdateResult(
    // 갱신 대상 언어.
    val lang: LangCode,
    // Correction 진입 판단용 세션 요약.
    val sessionSummary: SessionSummary,
    // Dashboard 표시용 대시보드 요약.
    val dashSummary: DashSummary,
    // 실제로 새 값이 반영됐는지, 중복 신호라 skip 됐는지 구분한다.
    val applied: Boolean,
    // 중복 방지용 이벤트 식별자.
    val sourceEventId: String,
    // 결과가 확정된 시각.
    val updatedAt: Long
)
