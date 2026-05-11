package com.example.umma.domain.model

/**
 * 사용자의 앱 전역 학습 언어 컨텍스트를 저장하는 모델.
 *
 * LS-003의 목적은 nativeLanguage / primaryLearningLanguage /
 * selectedLearningLanguage / learningLanguages 를 한 곳에서 일관되게 관리하는 것이다.
 *
 * - [nativeLanguage]: 사용자의 모국어
 * - [primaryLearningLanguage]: Initial Setup에서 처음 선택한 주 학습 언어
 * - [selectedLearningLanguage]: 현재 앱이 바라보는 학습 언어
 * - [learningLanguages]: 현재 학습 중인 언어 목록
 *
 * 이 모델은 Dashboard, AI Chat, Correction, Flashcard, Statistics가
 * 공통으로 참조하는 전역 언어 기준점이 된다.
 */
data class UserLearningPreferenceVO(
    val nativeLanguage: LanguageCode,
    val primaryLearningLanguage: LanguageCode,
    val selectedLearningLanguage: LanguageCode,
    val learningLanguages: List<LanguageCode>,
    val schemaVersion: Int = SCHEMA_VERSION,
    val updatedAt: Long? = null
) {
    companion object {
        const val SCHEMA_VERSION = 1

        /**
         * Initial Setup 직후 사용하는 기본 UserLearningPreference 생성기.
         *
         * 정책상 selectedLearningLanguage는 primaryLearningLanguage와 같아야 하고,
         * learningLanguages에는 최소한 primaryLearningLanguage가 포함되어야 한다.
         */
        fun createInitialUserLearningPreference(
            nativeLanguage: LanguageCode,
            primaryLearningLanguage: LanguageCode
        ): UserLearningPreferenceVO {
            return UserLearningPreferenceVO(
                nativeLanguage = nativeLanguage,
                primaryLearningLanguage = primaryLearningLanguage,
                selectedLearningLanguage = primaryLearningLanguage,
                learningLanguages = listOf(primaryLearningLanguage),
                schemaVersion = SCHEMA_VERSION,
                updatedAt = null
            )
        }
    }
}
