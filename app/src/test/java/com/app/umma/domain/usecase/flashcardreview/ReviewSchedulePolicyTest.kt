package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.flashcard.FlashcardSchedule
import com.app.umma.domain.model.flashcard.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSchedulePolicyTest {

    private val policy = ReviewSchedulePolicy()

    @Test
    fun `again resets interval and clamps ease factor`() {
        // Again 은 재진입 시 바로 복습 대상이 되도록 interval 은 0, easeFactor 는 하한선에 고정된다.
        // 이 케이스가 깨지면 재학습(Again) 스케줄 정책이 흔들린다.
        val result = policy.calculateNextSchedule(
            current = FlashcardSchedule(interval = 1440, easeFactor = 1.2, nextReviewAt = 0L),
            rating = ReviewRating.AGAIN,
            reviewedAt = 1_000L
        )

        assertEquals(0, result.interval)
        assertEquals(1.3, result.easeFactor, 0.0)
        assertEquals(1_000L, result.nextReviewAt)
    }

    @Test
    fun `new card good starts from one day base interval`() {
        // 신규 카드도 계산 기준이 흔들리지 않도록 1일(1440분)부터 시작하는지 확인한다.
        // 초기 카드가 0분으로 남으면 after-review 계산이 불안정해진다.
        val result = policy.calculateNextSchedule(
            current = FlashcardSchedule(interval = 0, easeFactor = 2.5, nextReviewAt = 0L),
            rating = ReviewRating.GOOD,
            reviewedAt = 2_000L
        )

        assertEquals(1_440, result.interval)
        assertEquals(2.5, result.easeFactor, 0.0)
        assertEquals(2_000L + 1_440L * 60L * 1_000L, result.nextReviewAt)
    }

    @Test
    fun `hard good easy produce stable minute based schedules`() {
        // 등급별 배수와 분->밀리초 변환이 정책대로 유지되는지 한 번에 확인한다.
        // 한 군데만 어긋나도 다음 카드 노출 타이밍이 전부 밀린다.
        val current = FlashcardSchedule(interval = 1_440, easeFactor = 2.5, nextReviewAt = 0L)
        val reviewedAt = 5_000L

        val hard = policy.calculateNextSchedule(current, ReviewRating.HARD, reviewedAt)
        val good = policy.calculateNextSchedule(current, ReviewRating.GOOD, reviewedAt)
        val easy = policy.calculateNextSchedule(current, ReviewRating.EASY, reviewedAt)

        assertEquals(1_728, hard.interval)
        assertEquals(2.35, hard.easeFactor, 0.0)
        assertEquals(reviewedAt + 1_728L * 60L * 1_000L, hard.nextReviewAt)

        assertEquals(3_600, good.interval)
        assertEquals(2.5, good.easeFactor, 0.0)
        assertEquals(reviewedAt + 3_600L * 60L * 1_000L, good.nextReviewAt)

        assertEquals(5_760, easy.interval)
        assertEquals(2.65, easy.easeFactor, 0.0)
        assertEquals(reviewedAt + 5_760L * 60L * 1_000L, easy.nextReviewAt)
    }
}
