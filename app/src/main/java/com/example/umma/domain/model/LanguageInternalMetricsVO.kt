package com.example.umma.domain.model

/**
 * AI 대화 적응과 교정 분석에 사용되는 Language State의 내부 지표 묶음.
 *
 * 이 모델은 사용자가 직접 보는 화면 상태가 아니라, Session Memory / Correction /
 * Statistics 로직이 공통으로 참조하는 도메인 데이터다.
 * LS-001의 MVP 범위에 맞춰 12개 지표만 고정한다.
 */
data class LanguageInternalMetricsVO(
    val grammarAccuracy: Double,
    val vocabularyAppropriateness: Double,
    val lexicalDiversity: Double,
    val vocabularyLevel: VocabularyLevel,
    val sentenceComplexity: Double,
    val speechRate: Double,
    val pauseFrequency: Double,
    val avgUtteranceLength: Double,
    val spokenNaturalness: Double,
    val naturalExpressionUsage: Double,
    val errorRecurrence: Double,
    val reviewRetention: Double
) {
    companion object {
        /**
         * 신규 사용자 또는 초기 설정 직후에 사용할 기본 내부 지표값.
         *
         * 대화 학습이 누적되기 전에는 모든 수치를 0으로 시작하고,
         * CEFR 레벨은 가장 보수적인 A1로 시작한다.
         */
        fun initial(): LanguageInternalMetricsVO {
            return LanguageInternalMetricsVO(
                grammarAccuracy = 0.0,
                vocabularyAppropriateness = 0.0,
                lexicalDiversity = 0.0,
                vocabularyLevel = VocabularyLevel.A1,
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
