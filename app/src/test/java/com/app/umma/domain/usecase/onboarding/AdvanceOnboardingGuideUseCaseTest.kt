package com.app.umma.domain.usecase.onboarding

import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AdvanceOnboardingGuideUseCase.nextStage] 순수 전이 함수 단위 테스트.
 *
 * 저장/repo 없이 전이 규칙만 검증한다.
 * DONE 영구 종료, 미산출 리셋, guard(잘못된 단계에서 no-op)를 모두 커버한다.
 */
class AdvanceOnboardingGuideUseCaseTest {

    private fun nextStage(current: OnboardingGuideStage, event: OnboardingGuideEvent) =
        AdvanceOnboardingGuideUseCase.nextStage(current, event)

    // ── 정상 퍼널 전이 ──────────────────────────────────────────────────────────────

    @Test
    fun `CONVERSATION + CorrectionAvailable → CORRECTION`() {
        assertEquals(
            OnboardingGuideStage.CORRECTION,
            nextStage(OnboardingGuideStage.CONVERSATION, OnboardingGuideEvent.CorrectionAvailable)
        )
    }

    @Test
    fun `CORRECTION + CorrectionSaved → STUDY`() {
        assertEquals(
            OnboardingGuideStage.STUDY,
            nextStage(OnboardingGuideStage.CORRECTION, OnboardingGuideEvent.CorrectionSaved)
        )
    }

    @Test
    fun `STUDY + StudyInteracted → DONE`() {
        assertEquals(
            OnboardingGuideStage.DONE,
            nextStage(OnboardingGuideStage.STUDY, OnboardingGuideEvent.StudyInteracted)
        )
    }

    // ── 미산출 리셋 분기 ──────────────────────────────────────────────────────────

    @Test
    fun `CORRECTION + CorrectionNoFlashcard → CONVERSATION (미산출 리셋)`() {
        assertEquals(
            OnboardingGuideStage.CONVERSATION,
            nextStage(OnboardingGuideStage.CORRECTION, OnboardingGuideEvent.CorrectionNoFlashcard)
        )
    }

    // ── DONE 영구 종료 (모든 이벤트 no-op) ───────────────────────────────────────

    @Test
    fun `DONE + CorrectionAvailable → null (영구 종료)`() {
        assertNull(nextStage(OnboardingGuideStage.DONE, OnboardingGuideEvent.CorrectionAvailable))
    }

    @Test
    fun `DONE + CorrectionSaved → null (영구 종료)`() {
        assertNull(nextStage(OnboardingGuideStage.DONE, OnboardingGuideEvent.CorrectionSaved))
    }

    @Test
    fun `DONE + CorrectionNoFlashcard → null (영구 종료)`() {
        assertNull(nextStage(OnboardingGuideStage.DONE, OnboardingGuideEvent.CorrectionNoFlashcard))
    }

    @Test
    fun `DONE + StudyInteracted → null (영구 종료)`() {
        assertNull(nextStage(OnboardingGuideStage.DONE, OnboardingGuideEvent.StudyInteracted))
    }

    // ── Guard — 잘못된 단계에서 이벤트 no-op ──────────────────────────────────────

    @Test
    fun `CONVERSATION + CorrectionSaved → null (guard)`() {
        assertNull(nextStage(OnboardingGuideStage.CONVERSATION, OnboardingGuideEvent.CorrectionSaved))
    }

    @Test
    fun `CONVERSATION + CorrectionNoFlashcard → null (guard)`() {
        assertNull(nextStage(OnboardingGuideStage.CONVERSATION, OnboardingGuideEvent.CorrectionNoFlashcard))
    }

    @Test
    fun `CONVERSATION + StudyInteracted → null (guard)`() {
        assertNull(nextStage(OnboardingGuideStage.CONVERSATION, OnboardingGuideEvent.StudyInteracted))
    }

    @Test
    fun `CORRECTION + CorrectionAvailable → null (guard, 중복 이벤트)`() {
        assertNull(nextStage(OnboardingGuideStage.CORRECTION, OnboardingGuideEvent.CorrectionAvailable))
    }

    @Test
    fun `CORRECTION + StudyInteracted → null (guard)`() {
        assertNull(nextStage(OnboardingGuideStage.CORRECTION, OnboardingGuideEvent.StudyInteracted))
    }

    @Test
    fun `STUDY + CorrectionAvailable → null (guard)`() {
        assertNull(nextStage(OnboardingGuideStage.STUDY, OnboardingGuideEvent.CorrectionAvailable))
    }

    @Test
    fun `STUDY + CorrectionNoFlashcard → null (guard, STUDY 단계는 리셋 없음)`() {
        // STUDY 단계에서 미산출 리셋은 발생하지 않는다 — 이미 CORRECTION 을 지난 단계.
        assertNull(nextStage(OnboardingGuideStage.STUDY, OnboardingGuideEvent.CorrectionNoFlashcard))
    }
}
