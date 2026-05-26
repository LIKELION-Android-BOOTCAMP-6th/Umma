package com.app.umma.domain.model.learningstate

/**
 * Dashboard가 빠르게 읽는 언어별 요약 카드 데이터.
 */
data class DashSummary(
    // 현재 언어 문맥.
    val lang: LangCode,
    // 최근 대화 길이.
    val recentMinutes: Int,
    // 최근 대화 주제.
    val recentTopic: String?,
    // 교정 화면으로 갈 수 있는지.
    val correctionAvailable: Boolean,
    // 오늘 복습 대상 카드 수.
    val dueFlashcards: Int,
    // 저장된 카드 수.
    val savedFlashcards: Int,
    // 문법 성취 변화량.
    val grammarDelta: Int,
    // 유창성 성취 변화량.
    val fluencyDelta: Int,
    // 어휘 성취 변화량.
    val vocabDelta: Int,
    // 자연스러움 성취 변화량.
    val naturalnessDelta: Int,
    // 저장 구조 버전.
    val schema: Int = SCHEMA,
    // 마지막 갱신 시각.
    val updatedAt: Long? = null
) {
    companion object {
        const val SCHEMA = 1

        fun initial(lang: LangCode): DashSummary {
            // 대시보드 첫 진입용 빈 상태.
            return DashSummary(
                lang = lang,
                recentMinutes = 0,
                recentTopic = null,
                correctionAvailable = false,
                dueFlashcards = 0,
                savedFlashcards = 0,
                grammarDelta = 0,
                fluencyDelta = 0,
                vocabDelta = 0,
                naturalnessDelta = 0,
                schema = SCHEMA,
                updatedAt = null
            )
        }
    }
}

/**
 * Session Memory 전체 대신 전역 상태가 참조하는 요약 단위.
 */
data class SessionSummary(
    // 현재 언어 문맥.
    val lang: LangCode,
    // 교정 가능 여부.
    val correctionAvailable: Boolean,
    // 최근 대화 길이.
    val recentMinutes: Int,
    // 최근 대화 주제.
    val recentTopic: String?,
    // 마지막 갱신 시각.
    val updatedAt: Long? = null
) {
    companion object {
        fun initial(lang: LangCode): SessionSummary {
            // 세션 메모리가 아직 비어 있을 때의 기본 상태.
            return SessionSummary(
                lang = lang,
                correctionAvailable = false,
                recentMinutes = 0,
                recentTopic = null,
                updatedAt = null
            )
        }
    }
}

/**
 * Flashcard 전체 목록 대신 복습 상태만 보여주는 요약 단위.
 */
data class FlashcardSummary(
    // 현재 언어 문맥.
    val lang: LangCode,
    // 오늘 복습해야 할 카드 수.
    val dueFlashcards: Int,
    // 저장된 카드 수.
    val savedFlashcards: Int,
    // 마지막 갱신 시각.
    val updatedAt: Long? = null
) {
    companion object {
        fun initial(lang: LangCode): FlashcardSummary {
            // 플래시카드가 아직 없는 초기 상태.
            return FlashcardSummary(
                lang = lang,
                dueFlashcards = 0,
                savedFlashcards = 0,
                updatedAt = null
            )
        }
    }
}

/**
 * DashSummary 가 "실질적으로 비어있는지" 판정.
 *
 * Dashboard 의 Empty 분기 기준 (DASH-001):
 *  - summary == null: UserLangPref 자체가 없는 케이스 (Initial Setup 전)
 *  - summary != null 이지만 모든 필드가 초기값: Initial Setup 직후 onboarding 끝낸 신규 사용자
 *
 * 두 케이스 모두 사용자 관점에선 "아직 학습 데이터 없음" 이라 같이 Empty 분기로 보낸다.
 * delta 4 종(grammar/fluency/vocab/naturalness) 은 신규 사용자도 의미 있는 0 일 수 있어서
 * isEmpty 판정에서는 의도적으로 제외 — recentMinutes/topic/flashcards 가 진짜 활동 지표.
 */
val DashSummary.isEffectivelyEmpty: Boolean
    get() = recentMinutes == 0 &&
            recentTopic == null &&
            dueFlashcards == 0 &&
            savedFlashcards == 0
