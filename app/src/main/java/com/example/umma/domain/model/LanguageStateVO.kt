package com.example.umma.domain.model

/**
 * 사용자의 학습 언어별 장기 상태를 표현하는 상위 Language State 모델.
 *
 * 이 VO는 Firestore의 `users/{uid}/language_states/{language}` 문서와 1:1로
 * 대응하는 것을 목표로 하며, UI 상태가 아니라 도메인 저장 구조를 나타낸다.
 */
data class LanguageStateVO(
    val language: String,
    val internalMetrics: LanguageInternalMetricsVO,
    val externalMetrics: LanguageExternalMetricsVO,
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) {
    companion object {
        const val SCHEMA_VERSION = 1

        /**
         * Initial Setup 직후나 Language State 문서가 아직 없을 때 사용하는 기본 생성 경로.
         *
         * Dashboard와 AI Chat이 바로 읽을 수 있도록 internal / external metrics를
         * 모두 초기값으로 채운다.
         */
        fun initial(
            language: String,
            createdAt: Long? = null,
            updatedAt: Long? = createdAt
        ): LanguageStateVO {
            return LanguageStateVO(
                language = language,
                internalMetrics = LanguageInternalMetricsVO.initial(),
                externalMetrics = LanguageExternalMetricsVO.initial(),
                schemaVersion = SCHEMA_VERSION,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
