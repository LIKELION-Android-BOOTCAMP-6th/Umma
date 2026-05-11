package com.example.umma.domain.model.learningstate

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
 * 사용자 언어 상태의 저장 단위.
 */
data class LangState(
    // 현재 선택된 학습 언어.
    val lang: LangCode,
    // AI 적응과 분석용 세부 지표.
    val internal: InternalMetrics,
    // 화면과 대시보드용 요약 지표.
    val external: ExternalMetrics,
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
        const val SCHEMA = 1

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
                schema = SCHEMA,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastAnalyzedAt = null,
                lastAnalysisEventId = null
            )
        }
    }
}
