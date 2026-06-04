package com.app.umma.domain.model.learningstate

/**
 * 대시보드에서 빠르게 보여주는 언어별 요약 상태다.
 */
data class DashSummary(
    val lang: LangCode,
    val recentMinutes: Int,
    val recentTopic: String?,
    val correctionAvailable: Boolean,
    val dueFlashcards: Int,
    val notifiableDueFlashcards: Int = 0,
    val savedFlashcards: Int,
    val grammarDelta: Int,
    val fluencyDelta: Int,
    val vocabDelta: Int,
    val naturalnessDelta: Int,
    val schema: Int = SCHEMA,
    val updatedAt: Long? = null,
) {
    companion object {
        const val SCHEMA = 1

        fun initial(lang: LangCode): DashSummary {
            return DashSummary(
                lang = lang,
                recentMinutes = 0,
                recentTopic = null,
                correctionAvailable = false,
                dueFlashcards = 0,
                notifiableDueFlashcards = 0,
                savedFlashcards = 0,
                grammarDelta = 0,
                fluencyDelta = 0,
                vocabDelta = 0,
                naturalnessDelta = 0,
                schema = SCHEMA,
                updatedAt = null,
            )
        }
    }
}

/**
 * 세션 요약 상태다.
 */
data class SessionSummary(
    val lang: LangCode,
    val correctionAvailable: Boolean,
    val recentMinutes: Int,
    val recentTopic: String?,
    val updatedAt: Long? = null,
) {
    companion object {
        fun initial(lang: LangCode): SessionSummary {
            return SessionSummary(
                lang = lang,
                correctionAvailable = false,
                recentMinutes = 0,
                recentTopic = null,
                updatedAt = null,
            )
        }
    }
}

/**
 * 플래시카드 요약 상태다.
 */
data class FlashcardSummary(
    val lang: LangCode,
    val dueFlashcards: Int,
    val notifiableDueFlashcards: Int = 0,
    val savedFlashcards: Int,
    val updatedAt: Long? = null,
) {
    companion object {
        fun initial(lang: LangCode): FlashcardSummary {
            return FlashcardSummary(
                lang = lang,
                dueFlashcards = 0,
                notifiableDueFlashcards = 0,
                savedFlashcards = 0,
                updatedAt = null,
            )
        }
    }
}

/**
 * 대시보드 요약이 실질적으로 비어 있는지 판단한다.
 */
val DashSummary.isEffectivelyEmpty: Boolean
    get() = recentMinutes == 0 &&
        recentTopic == null &&
        dueFlashcards == 0 &&
        savedFlashcards == 0
