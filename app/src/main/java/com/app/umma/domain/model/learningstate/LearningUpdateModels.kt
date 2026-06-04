package com.app.umma.domain.model.learningstate

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
    // Correction AI가 교정 과정에서 관찰한 학습 신호.
    // 기본값을 비워 기존 correction 완료 경로가 signal 없이도 그대로 동작하게 한다.
    val learningSignals: List<CorrectionLearningSignal> = emptyList()
)

/**
 * Correction이 LearningState에 넘기는 관찰 신호.
 *
 * 이 모델은 최종 점수나 레벨이 아니라, LearningState가 장기 evidence/focus를
 * 계산하기 위한 입력이다. 그래서 grammarAccuracy 같은 최종 metric은 여기서 받지 않는다.
 */
data class CorrectionLearningSignal(
    // 교정 후보와 signal을 다시 연결하기 위한 식별자.
    val candidateId: String,
    // SessionMemory 원본 turn id. 없을 수 있으므로 sourceTurnIndex fallback을 함께 둔다.
    val sourceTurnId: String?,
    // sourceTurnId가 없을 때 원본 발화 순서를 추적하는 fallback.
    val sourceTurnIndex: Int,
    // 사용자가 실제로 말한 문장.
    val sourceText: String,
    // 교정 결과 문장.
    val correctedText: String,
    // 언어 공통 오류 범주. 장기 metric과 focus의 1차 근거가 된다.
    val issueCategories: List<CorrectionIssueCategory>,
    // 언어별 세부 학습 포인트. active focus를 더 정확히 잡기 위한 보조 신호다.
    val languageFeatures: List<LanguageFeatureSignal>,
    // 교정이 어떤 방향으로 개선됐는지 나타내는 신호.
    val improvementTypes: List<CorrectionImprovementType>,
    // 전체 문장보다 작은 변경 조각. 과도한 rewrite guard와 focus 추적에 사용한다.
    val editSpans: List<CorrectionEditSpan>,
    // 교정 결과 문장의 말투.
    val register: SpokenRegister,
    // 오류/개선의 교육적 심각도.
    val severity: CorrectionSeverity,
    // 교정이 사용자 원래 의미를 유지했는지 여부.
    val meaningPreserved: Boolean,
    // AI 판단 신뢰도. null이면 policy가 보수적인 기본값으로 취급한다.
    val confidence: Double?
)

/**
 * 특정 언어에 종속되지 않는 큰 오류 범주.
 */
enum class CorrectionIssueCategory {
    GrammarForm,
    WordOrder,
    SentenceCompleteness,
    VocabularyChoice,
    Collocation,
    Register,
    MissingContext,
    MeaningMismatch
}

/**
 * 언어별 세부 학습 feature.
 *
 * featureKey는 `EN.Article`, `JA.Particle`처럼 namespace를 포함해야 한다.
 */
data class LanguageFeatureSignal(
    // 어떤 학습 언어의 feature인지.
    val lang: LangCode,
    // 언어별 feature 이름.
    val featureKey: String
)

/**
 * 교정이 어떤 교육적 방향으로 개선됐는지 나타낸다.
 */
enum class CorrectionImprovementType {
    GrammarFixed,
    StructureExpanded,
    MoreNaturalVerb,
    BetterCollocation,
    SpokenExpressionAdded,
    ShortenedForClarity,
    MadeMoreCasual,
    MadeMorePolite
}

/**
 * source/corrected에서 실제로 달라진 작은 조각.
 */
data class CorrectionEditSpan(
    // 사용자가 말한 원문 fragment.
    val sourceFragment: String,
    // 교정된 fragment.
    val correctedFragment: String,
    // 이 fragment가 대표하는 오류 범주.
    val issueCategory: CorrectionIssueCategory,
    // 언어별 feature가 명확할 때만 채운다.
    val languageFeatureKey: String?,
    // 이 fragment가 어떤 방식으로 개선됐는지.
    val improvementType: CorrectionImprovementType
)

/**
 * 교정 결과 문장의 말투.
 */
enum class SpokenRegister {
    Simple,
    EverydaySpoken,
    NativeLikeCasual,
    Formal
}

/**
 * 교정 신호의 심각도.
 */
enum class CorrectionSeverity {
    BlockingMeaning,
    MajorPattern,
    MinorForm,
    NaturalnessOnly
}

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
    // 교정 완료 흐름에서 dash/session summary 의 recentTopic 을 함께 갱신할 때 사용한다.
    // null 이면 Repository 가 이전 값을 보존한다 — 부분 갱신 호출자가 주제를 지우지 않도록.
    val recentTopic: String? = null,
    // 플래시카드 복습 이벤트 묶음.
    val flashcardReviewEvents: List<FlashcardReviewEvent>,
    // 분석이 끝난 시각.
    val analyzedAt: Long,
    // 강제로 재분석해야 하는지 여부.
    val forceReanalysis: Boolean = false
)

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
