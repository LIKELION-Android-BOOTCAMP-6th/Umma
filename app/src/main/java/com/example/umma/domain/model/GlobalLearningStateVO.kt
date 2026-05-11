package com.example.umma.domain.model

/**
 * 앱 전역에서 현재 사용자 학습 상태를 한 번에 바라보는 스냅샷.
 *
 * 이 객체는 UI 상태가 아니라 Domain/App 상태이다.
 * 즉, 화면 전용 loading/error 텍스트를 담지 않고,
 * 현재 사용자와 선택 언어에 대한 "학습 컨텍스트"만 묶는다.
 */
data class GlobalLearningStateVO(
    val userLearningPreference: UserLearningPreferenceVO?,
    val languageStates: Map<LanguageCode, LanguageStateVO>,
    val dashboardSummaries: Map<LanguageCode, LanguageDashboardSummaryVO>,
    val sessionSummaries: Map<LanguageCode, SessionSummaryVO>,
    val flashcardSummaries: Map<LanguageCode, FlashcardSummaryVO>,
    val isPreloaded: Boolean = false,
    val schemaVersion: Int = SCHEMA_VERSION
) {
    companion object {
        const val SCHEMA_VERSION = 1

        /**
         * 아직 사용자 학습 상태가 로드되지 않은 앱 시작 시점의 기본 전역 상태.
         *
         * 로그인 여부와 관계없이 Store가 비어 있는 순간을 명시적으로 표현하기 위해 사용한다.
         */
        fun initial(): GlobalLearningStateVO {
            return GlobalLearningStateVO(
                userLearningPreference = null,
                languageStates = emptyMap(),
                dashboardSummaries = emptyMap(),
                sessionSummaries = emptyMap(),
                flashcardSummaries = emptyMap(),
                isPreloaded = false,
                schemaVersion = SCHEMA_VERSION
            )
        }
    }
}

/**
 * 현재 선택된 학습 언어를 읽기 쉽게 꺼내는 보조 계산값.
 *
 * selectedLearningLanguage는 Preference에 있기 때문에, 전역 상태는 여기서만
 * 안전하게 위임해서 읽는다.
 */
val GlobalLearningStateVO.selectedLanguage: LanguageCode?
    get() = userLearningPreference?.selectedLearningLanguage

/**
 * 현재 선택 언어의 Language State를 가져온다.
 *
 * 선택 언어가 없거나, 해당 언어의 상태가 아직 로드되지 않았으면 null을 반환한다.
 */
fun GlobalLearningStateVO.currentLanguageState(): LanguageStateVO? {
    val language = selectedLanguage ?: return null
    return languageStates[language]
}

/**
 * 현재 선택 언어의 Dashboard Summary를 가져온다.
 *
 * Dashboard는 이 값을 우선 사용해 빠르게 렌더링하고,
 * 필요한 경우 LS-005 단계의 동기화 정책에 따라 보완 데이터를 받아온다.
 */
fun GlobalLearningStateVO.currentDashboardSummary(): LanguageDashboardSummaryVO? {
    val language = selectedLanguage ?: return null
    return dashboardSummaries[language]
}

/**
 * 현재 선택 언어의 Session Summary를 가져온다.
 */
fun GlobalLearningStateVO.currentSessionSummary(): SessionSummaryVO? {
    val language = selectedLanguage ?: return null
    return sessionSummaries[language]
}

/**
 * 현재 선택 언어의 Flashcard Summary를 가져온다.
 */
fun GlobalLearningStateVO.currentFlashcardSummary(): FlashcardSummaryVO? {
    val language = selectedLanguage ?: return null
    return flashcardSummaries[language]
}
