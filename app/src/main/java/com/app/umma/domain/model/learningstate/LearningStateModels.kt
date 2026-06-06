package com.app.umma.domain.model.learningstate

/**
 * 언어별 내부 학습 지표 묶음.
 */
data class InternalMetrics(
    // 교정 품질과 문장 정확도를 대표하는 내부 값.
    val grammarAccuracy: Double,
    // 어휘 선택이 얼마나 자연스러운지 나타낸다.
    val vocabularyAppropriateness: Double,
    // 중복 없이 다양한 표현을 쓰는 정도.
    val lexicalDiversity: Double,
    // CEFR 기반 어휘 등급.
    val vocabularyLevel: VocabLevel,
    // 긴 문장과 복합문을 다루는 능력.
    val sentenceComplexity: Double,
    // 발화 속도와 끊김 정도를 계산할 때 쓴다.
    val speechRate: Double,
    // 머뭇거림이 얼마나 자주 나오는지 나타낸다.
    val pauseFrequency: Double,
    // 한 번의 발화가 얼마나 길게 이어지는지 나타낸다.
    val avgUtteranceLength: Double,
    // 구어체스럽고 자연스러운지 보여준다.
    val spokenNaturalness: Double,
    // 원어민식 표현을 얼마나 잘 고르는지 나타낸다.
    val naturalExpressionUsage: Double,
    // 교정된 실수가 다시 반복되는 정도.
    val errorRecurrence: Double,
    // 플래시카드 복습 결과가 얼마나 유지되는지 보여준다.
    val reviewRetention: Double
) {
    companion object {
        fun initial(): InternalMetrics {
            // 신규 사용자나 초기 동기화 직후에는 모두 0에 가깝게 시작한다.
            return InternalMetrics(
                grammarAccuracy = 0.0,
                vocabularyAppropriateness = 0.0,
                lexicalDiversity = 0.0,
                vocabularyLevel = VocabLevel.A1,
                sentenceComplexity = 0.0,
                speechRate = 0.0,
                pauseFrequency = 0.0,
                avgUtteranceLength = 0.0,
                spokenNaturalness = 0.0,
                naturalExpressionUsage = 0.0,
                errorRecurrence = 0.0,
                reviewRetention = 0.0
            )
        }
    }
}

/**
 * 외부 화면과 통계용으로 노출하는 요약 지표.
 */
data class ExternalMetrics(
    // 사용자가 직접 보게 되는 CEFR 등급.
    val vocabularyLevel: VocabLevel,
    // 사용자 화면에 보여줄 문법 점수.
    val grammarAccuracy: Double,
    // 말의 폭을 숫자로 단순화한 값.
    val expressionRange: Int,
    // 유창성을 하나의 점수로 묶은 값.
    val fluencyScore: Double,
    // 자연스러움 정도를 사용자에게 보여줄 점수.
    val naturalnessScore: Double
) {
    companion object {
        fun initial(): ExternalMetrics {
            // 외부 통계도 첫 진입 시 빈 상태로 렌더링한다.
            return ExternalMetrics(
                vocabularyLevel = VocabLevel.A1,
                grammarAccuracy = 0.0,
                expressionRange = 0,
                fluencyScore = 0.0,
                naturalnessScore = 0.0
            )
        }
    }
}

/**
 * CEFR 기반 어휘 레벨.
 */
enum class VocabLevel {
    A1, A2, B1, B2, C1, C2
}

/**
 * 장기 언어능력 계산에 쓰는 metric 식별자.
 *
 * raw correction 문장이나 prompt text를 저장하지 않고, 어떤 장기 지표에 대한 근거인지만
 * compact하게 남기기 위해 `InternalMetrics` 필드와 1:1에 가깝게 맞춘다.
 */
enum class LearningMetricKey {
    // 문법 오류가 줄어드는지 보는 장기 정확도 지표.
    GrammarAccuracy,
    // 단어 선택이 문맥에 맞는지 보는 어휘 적절성 지표.
    VocabularyAppropriateness,
    // 같은 단어만 반복하지 않고 다양한 단어를 쓰는지 보는 지표.
    LexicalDiversity,
    // CEFR 기반 어휘 난이도 단계.
    VocabularyLevel,
    // 단문을 넘어 복합 구조를 다루는지 보는 문장 구조 지표.
    SentenceComplexity,
    // 말이 너무 느리거나 빠르지 않은지 보는 발화 속도 지표.
    SpeechRate,
    // 머뭇거림이 잦은지 보는 fluency 보조 지표.
    PauseFrequency,
    // 한 번에 이어 말하는 길이가 늘어나는지 보는 fluency 보조 지표.
    AvgUtteranceLength,
    // 문장이 구어체로 자연스러운지 보는 지표.
    SpokenNaturalness,
    // 원어민이 자주 쓰는 자연스러운 표현을 활용하는지 보는 지표.
    NaturalExpressionUsage,
    // 같은 오류가 교정 이후에도 반복되는지 보는 학습 지표.
    ErrorRecurrence,
    // SRS 복습에서 학습한 표현이 유지되는지 보는 기억 유지 지표.
    ReviewRetention
}

/**
 * 관측 근거가 metric을 어느 방향으로 움직일지 나타내는 값.
 *
 * CEFR level처럼 급격히 움직이면 안 되는 값은 이 방향과 누적 횟수를 함께 봐야 한다.
 */
enum class EvidenceDirection {
    // 관측 근거가 해당 metric을 올리는 방향임을 뜻한다.
    Up,
    // 관측 근거가 해당 metric을 낮추는 방향임을 뜻한다.
    Down,
    // 변화 근거는 있지만 방향성이 거의 없거나 유지에 가깝다는 뜻이다.
    Stable,
    // 최근 관측 방향이 서로 충돌해 score/level 이동을 보수적으로 해야 한다는 뜻이다.
    Mixed
}

/**
 * metric evidence가 어디에서 온 신호인지 나타낸다.
 *
 * source를 남겨야 이후 policy가 AI signal과 코드 계산값의 weight를 다르게 줄 수 있다.
 */
enum class LearningSignalSource {
    // 사용자 발화 길이, token, pause처럼 앱이 직접 계산한 turn 기반 신호.
    UserTurn,
    // Correction AI가 교정 과정에서 구조화해 넘긴 관찰 신호.
    CorrectionSignal,
    // Chat 세션 종료 후 Gemini가 분석한 대화 지속 능력 신호.
    ChatSession,
    // Flashcard/SRS 복습 결과에서 온 신호.
    ReviewEvent,
    // 사전/패턴/규칙 기반 분석에서 온 신호.
    TypeBRule,
    // AI 분석이 직접 판단한 고비용 분석 신호.
    TypeCAi
}

/**
 * 장기 metric을 움직일 수 있는 반복 관측 근거.
 *
 * `confidence`는 이미 보정된 값만 보관한다. DTO 복원 단계에서 0.0..1.0 밖의 값은
 * domain으로 올리지 않아 single bad payload가 장기 profile을 오염시키지 않게 한다.
 */
data class MetricEvidence(
    // 이 metric에 대해 유효한 관측이 몇 번 누적됐는지.
    val observedCount: Int,
    // evidence 자체의 신뢰도. raw AI confidence가 아니라 보정된 confidence다.
    val confidence: Double,
    // 이 근거가 어떤 입력 경로에서 왔는지.
    val sourceTypes: Set<LearningSignalSource>,
    // 이번 누적 방향.
    val direction: EvidenceDirection,
    // 같은 방향 관측이 얼마나 이어졌는지.
    val directionCount: Int,
    // 마지막으로 이 근거가 관측된 시각.
    val lastObservedAt: Long? = null
)

/**
 * Chat/Correction이 당장 도와줄 수 있는 반복 약점 유형.
 *
 * 구체 원문을 저장하지 않고 유형만 저장해 privacy와 prompt bloat를 막는다.
 */
enum class LearningFocusType {
    // 영어 관사처럼 명사 앞 한정 표현이 반복해서 문제 되는 경우.
    Article,
    // 시제 선택이나 동사 형태가 반복해서 문제 되는 경우.
    Tense,
    // 전치사 선택이 반복해서 문제 되는 경우.
    Preposition,
    // 단어 순서나 문장 배열이 반복해서 문제 되는 경우.
    WordOrder,
    // 완전한 문장으로 끝나지 않는 fragment가 반복되는 경우.
    SentenceFragment,
    // 뜻은 통하지만 더 적절한 단어 선택이 필요한 경우.
    VocabularyChoice,
    // 같은 쉬운 동사만 반복해 표현 폭이 제한되는 경우.
    LimitedVerbRange,
    // 단어 조합이나 collocation이 어색하게 반복되는 경우.
    UnnaturalCollocation,
    // 상황보다 지나치게 격식적인 표현을 반복하는 경우.
    TooFormal,
    // 의미 전달에 필요한 주어/목적어/상황 정보가 빠지는 경우.
    MissingContext
}

/**
 * 반복 관측된 학습 초점.
 *
 * 실제 prompt에는 이 목록 전체가 아니라 profile 단계에서 상위 1~2개만 요약해 전달한다.
 */
data class LearningFocus(
    // 반복 관측된 약점 유형.
    val type: LearningFocusType,
    // 같은 focus가 관측된 횟수.
    val observedCount: Int,
    // focus를 다음 대화/교정에 반영할 신뢰도.
    val confidence: Double,
    // 처음 관측된 시각.
    val firstObservedAt: Long,
    // 마지막 관측된 시각.
    val lastObservedAt: Long
)

/**
 * LangState schema v2에서 추가되는 분석 메타데이터.
 *
 * 점수 자체와 별개로 "왜 이 점수를 움직일 수 있는지"에 대한 최소 근거만 저장한다.
 * raw correction history, prompt text, difficultyDelta는 이 모델에 저장하지 않는다.
 */
data class LangStateAnalysisMeta(
    // metric별 누적 근거. unknown metric key는 DTO 복원 단계에서 제거된다.
    val metricEvidence: Map<LearningMetricKey, MetricEvidence>,
    // 다음 Chat/Correction에서 도울 수 있는 active focus 후보.
    val activeFocus: List<LearningFocus>,
    // 마지막 learning signal 관측 시각.
    val lastSignalAt: Long? = null,
    // Chat 세션 분석은 correction 분석 사이에 끼어들 수 있어 source별 중복 방어 id를 별도로 둔다.
    val lastChatAnalysisEventId: String? = null
) {
    companion object {
        fun initial(): LangStateAnalysisMeta {
            // schema v1 데이터나 신규 사용자 모두 빈 근거 상태에서 시작한다.
            return LangStateAnalysisMeta(
                metricEvidence = emptyMap(),
                activeFocus = emptyList(),
                lastSignalAt = null,
                lastChatAnalysisEventId = null
            )
        }
    }
}

/**
 * 사용자 언어 상태의 저장 단위.
 */
data class LangState(
    // 현재 선택된 학습 언어.
    val lang: LangCode,
    // AI 적응과 분석용 세부 지표.
    val internal: InternalMetrics,
    // 화면과 대시보드용 요약 지표.
    val external: ExternalMetrics,
    // 장기 score 급변을 막기 위한 evidence/focus 메타데이터.
    val analysisMeta: LangStateAnalysisMeta = LangStateAnalysisMeta.initial(),
    // 저장 구조 버전.
    val schema: Int = SCHEMA,
    // 최초 생성 시각.
    val createdAt: Long? = null,
    // 마지막으로 반영된 시각.
    val updatedAt: Long? = null,
    // 마지막 분석 시각.
    val lastAnalyzedAt: Long? = null,
    // 마지막 분석 이벤트 식별자.
    val lastAnalysisEventId: String? = null
) {
    companion object {
        const val SCHEMA = 2

        fun initial(
            lang: LangCode,
            createdAt: Long? = null,
            updatedAt: Long? = createdAt
        ): LangState {
            // LS-001 초기 생성 경로에서 사용하는 기본 상태.
            return LangState(
                lang = lang,
                internal = InternalMetrics.initial(),
                external = ExternalMetrics.initial(),
                analysisMeta = LangStateAnalysisMeta.initial(),
                schema = SCHEMA,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastAnalyzedAt = null,
                lastAnalysisEventId = null
            )
        }
    }
}
