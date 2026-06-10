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
        // 1440분 = 1일
        // interval이 1일(1440분) 이상이면 졸업한 복습 카드, 미만이면 학습 중인 카드
        val graduated = current.interval >= 1440
        val currentEF = current.easeFactor

        // 등급별로 간격과 easeFactor를 함께 갱신해 다음 복습 시점을 계산한다.
        val (nextInterval, nextEF) = when (rating) {
            ReviewRating.AGAIN -> {
                // 재학습(Again): 1분 뒤 다시 복습 대상이 되도록 짧은 간격, 복습 카드는 EF도 깎는다.
                if (graduated) 1 to max(1.3, currentEF - 0.20)
                else 1 to currentEF
            }

            ReviewRating.HARD -> {
                // 어려움(Hard): 복습 카드는 간격 1.2배(최소 1일) + EF 깎기, 학습 카드는 10분 뒤
                if (graduated) max(1440.0, current.interval * 1.2).toInt() to
                        max(1.3, currentEF - 0.15)
                else 10 to currentEF
            }

            ReviewRating.GOOD -> {
                // 괜찮음(Good): 복습 카드는 간격 × EF, 학습 카드는 바로 졸업(1일)
                if (graduated) (current.interval * currentEF).toInt() to currentEF
                else 1440 to currentEF
            }

            ReviewRating.EASY -> {
                // 쉽게 맞힌 카드는 더 오래 뒤에 다시 본다.
                // 쉬움(Easy): 복습 카드는 간격 × EF × 1.3 + EF 올리기, 학습 카드는 바로 졸업(4일)
                if (graduated) (current.interval * currentEF * 1.3).toInt() to (currentEF + 0.15)
                else 5760 to currentEF
            }
        }

        // 분 단위 간격을 실제 복습 타임스탬프로 바꿔 저장 계약으로 넘긴다.
        // 다음 복습 시각 = 평가한 시각 + (간격(분) * 60초 * 1000밀리초)
        val nextReviewAt = reviewedAt + (nextInterval.toLong() * 60 * 1000)

        return ReviewScheduleResult(
            interval = nextInterval,
            easeFactor = nextEF,
            nextReviewAt = nextReviewAt
        )
    }
}
