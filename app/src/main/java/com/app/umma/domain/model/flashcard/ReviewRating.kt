package com.app.umma.domain.model.flashcard

/** SM-2 기반 4단계 복습 평가 등급입니다. */
enum class ReviewRating(val value: Int) {
    // 전혀 기억나지 않음.
    AGAIN(0),

    // 매우 어렵게 기억해냄.
    HARD(1),

    // 적절한 노력으로 기억해냄.
    GOOD(2),

    // 매우 쉽게 기억해냄.
    EASY(3)

    ;

    companion object {
        fun fromName(name: String?): ReviewRating? {
            return entries.firstOrNull { it.name == name }
        }
    }
}
