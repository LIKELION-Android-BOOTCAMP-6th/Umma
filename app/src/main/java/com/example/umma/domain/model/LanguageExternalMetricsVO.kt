package com.example.umma.domain.model

/**
 * Statistics / Dashboard에서 사용자에게 노출할 요약 지표 묶음.
 *
 * internalMetrics를 직접 모두 보여주지 않고, 외부 노출에 적합한
 * 5개 요약 값만 유지한다.
 */
data class LanguageExternalMetricsVO(
    val vocabularyLevel: VocabularyLevel,
    val grammarAccuracy: Double,
    val expressionRange: Int,
    val fluencyScore: Double,
    val naturalnessScore: Double
) {
    companion object {
        /**
         * 아직 학습 데이터가 충분하지 않은 경우의 기본 외부 지표.
         */
        fun initial(): LanguageExternalMetricsVO {
            return LanguageExternalMetricsVO(
                vocabularyLevel = VocabularyLevel.A1,
                grammarAccuracy = 0.0,
                expressionRange = 0,
                fluencyScore = 0.0,
                naturalnessScore = 0.0
            )
        }
    }
}
