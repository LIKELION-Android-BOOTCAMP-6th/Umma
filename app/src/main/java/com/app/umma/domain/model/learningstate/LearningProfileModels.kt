package com.app.umma.domain.model.learningstate

/**
 * 사용자의 앱 전역 학습 언어 컨텍스트.
 */
data class UserLangPref(
    // 사용자가 학습 기준으로 삼는 언어. UI 안내, 교정 설명, 번역/source 문장의 기준이 된다.
    val primaryLang: LangCode,
    // 현재 사용자가 학습하려는 언어. LangState, Chat, Correction, SRS 데이터 소속의 기준이다.
    val selectedLang: LangCode,
    // 사용자가 학습 대상으로 추가한 언어 목록. primaryLang도 학습 대상이면 별도 선택을 통해 포함될 수 있다.
    val learningLangs: List<LangCode>,
    // 저장 구조 버전.
    val schema: Int = SCHEMA,
    // 마지막 갱신 시각.
    val updatedAt: Long? = null
) {
    companion object {
        const val SCHEMA = 1

        fun initial(
            primaryLang: LangCode,
            selectedLang: LangCode
        ): UserLangPref {
            // 최초 설정은 학습 기준 언어(primary)와 현재 학습 대상 언어(selected)를 분리해 저장한다.
            return UserLangPref(
                primaryLang = primaryLang,
                selectedLang = selectedLang,
                learningLangs = listOf(selectedLang),
                schema = SCHEMA,
                updatedAt = null
            )
        }
    }
}

/**
 * 앱 전역에서 현재 사용자 학습 상태를 한 번에 바라보는 스냅샷.
 */
data class GlobalLangState(
    // 현재 사용자 설정.
    val userPref: UserLangPref?,
    // 언어별 장기 상태.
    val langStates: Map<LangCode, LangState>,
    // 언어별 대시보드 요약.
    val dashSummaries: Map<LangCode, DashSummary>,
    // 언어별 세션 요약.
    val sessionSummaries: Map<LangCode, SessionSummary>,
    // 언어별 카드 요약.
    val flashcardSummaries: Map<LangCode, FlashcardSummary>,
    // preload 여부.
    val isPreloaded: Boolean = false,
    // 저장 구조 버전.
    val schema: Int = SCHEMA
) {
    companion object {
        const val SCHEMA = 1

        fun initial(): GlobalLangState {
            // 앱 시작 직후 또는 로그아웃 직후의 비어 있는 상태.
            return GlobalLangState(
                userPref = null,
                langStates = emptyMap(),
                dashSummaries = emptyMap(),
                sessionSummaries = emptyMap(),
                flashcardSummaries = emptyMap(),
                isPreloaded = false,
                schema = SCHEMA
            )
        }
    }
}

val GlobalLangState.selectedLang: LangCode?
    // 현재 선택 언어가 없으면 null 로 본다.
    get() = userPref?.selectedLang

fun GlobalLangState.currentLangState(): LangState? {
    // selectedLang 기준으로 현재 언어 상태를 선택한다.
    val lang = selectedLang ?: return null
    return langStates[lang]
}

fun GlobalLangState.currentDashSummary(): DashSummary? {
    // Dashboard는 현재 선택 언어의 summary만 본다.
    val lang = selectedLang ?: return null
    return dashSummaries[lang]
}

fun GlobalLangState.currentSessionSummary(): SessionSummary? {
    // 교정과 대화 진입 판단에 쓸 세션 요약을 가져온다.
    val lang = selectedLang ?: return null
    return sessionSummaries[lang]
}

fun GlobalLangState.currentFlashcardSummary(): FlashcardSummary? {
    // 플래시카드 학습 카드 진입 시 쓴다.
    val lang = selectedLang ?: return null
    return flashcardSummaries[lang]
}
