package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.flashcard.FlashcardSchedule
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import javax.inject.Inject
import kotlin.math.max

/** SM-2 기반 복습 주기를 우리 서비스 정책에 맞게 계산합니다. */
class ReviewSchedulePolicy @Inject constructor() {

    /**
     * 현재 카드 상태와 평가 등급으로 다음 복습 시점을 계산합니다.
     */
    fun calculateNextSchedule(
        current: FlashcardSchedule,
        rating: ReviewRating,
        reviewedAt: Long
    ): ReviewScheduleResult {
        // 신규 카드도 첫 노출 기준이 흔들리지 않도록 1일을 기준 간격으로 잡는다.
        val baseInterval = if (current.interval <= 0) 1440 else current.interval
        val currentEF = current.easeFactor

        // 등급별로 간격과 easeFactor를 함께 갱신해 다음 복습 시점을 계산한다.
        val (nextInterval, nextEF) = when (rating) {
            ReviewRating.AGAIN -> {
                // 다시 봐야 하는 카드라서 간격을 0으로 두고, 세션 재등장은 ViewModel이 관리한다.
                0 to max(1.3, currentEF - 0.20)
            }
            ReviewRating.HARD -> {
                // 어렵게 맞힌 카드는 간격을 조금만 늘린다.
                val interval = max(1440.0, baseInterval * 1.2).toInt()
                interval to max(1.3, currentEF - 0.15)
            }
            ReviewRating.GOOD -> {
                // 무난하게 맞힌 카드는 표준 배수를 사용한다.
                val interval = max(1440.0, baseInterval * currentEF).toInt()
                interval to currentEF
            }
            ReviewRating.EASY -> {
                // 쉽게 맞힌 카드는 더 오래 뒤에 다시 본다.
                val interval = max(5760.0, baseInterval * currentEF * 1.3).toInt()
                interval to (currentEF + 0.15)
            }
        }

        // 분 단위 간격을 실제 복습 타임스탬프로 바꿔 저장 계약으로 넘긴다.
        val nextReviewAt = reviewedAt + (nextInterval.toLong() * 60 * 1000)

        return ReviewScheduleResult(
            interval = nextInterval,
            easeFactor = nextEF,
            nextReviewAt = nextReviewAt
        )
    }
}
