package com.app.umma.domain.usecase.onboarding

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import com.app.umma.domain.model.learningstate.onboardingStageFor
import com.app.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 온보딩 가이드 이벤트를 받아 현재 stage 를 전이시키고 저장한다.
 *
 * 퍼널: CONVERSATION → CORRECTION → STUDY → DONE (단방향)
 * 리셋: 교정 미산출 시 CORRECTION → CONVERSATION
 * 불변식: DONE 도달 후 모든 이벤트 no-op — 재진입에도 펄스 없음.
 *
 * 전이 판단은 순수 함수 [nextStage] 에 위임하므로 저장 없이 단위 테스트 가능.
 * 이미 같은 stage 면 저장을 생략해 불필요한 Firestore 쓰기를 막는다.
 *
 * SSOT: DASH-UX-001 이슈 #383 — 온보딩 전이표.
 */
class AdvanceOnboardingGuideUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    /**
     * @param event 발생한 온보딩 이벤트
     * @param lang  이벤트가 속한 학습 언어
     */
    suspend operator fun invoke(event: OnboardingGuideEvent, lang: LangCode): Result<Unit> {
        val userPref = repo.observeUserPref().first()
            ?: return Result.success(Unit) // 신규 setup 이전 — no-op
        val current = userPref.onboardingStageFor(lang)
        val next = nextStage(current, event) ?: return Result.success(Unit) // 전이 없음 — no-op
        return repo.setOnboardingGuideStage(lang, next)
    }

    companion object {
        /**
         * 현재 stage 와 이벤트로 다음 stage 를 결정하는 순수 함수.
         * 전이가 없으면 null 을 반환한다.
         *
         * | current      | event                 | next         |
         * |---|---|---|
         * | CONVERSATION | CorrectionAvailable   | CORRECTION   |
         * | CORRECTION   | CorrectionSaved       | STUDY        |
         * | CORRECTION   | CorrectionNoFlashcard | CONVERSATION |
         * | STUDY        | StudyInteracted       | DONE         |
         * | DONE         | *                     | null (no-op) |
         * | *            | mismatched event      | null (no-op) |
         */
        fun nextStage(current: OnboardingGuideStage, event: OnboardingGuideEvent): OnboardingGuideStage? {
            if (current == OnboardingGuideStage.DONE) return null
            return when (event) {
                OnboardingGuideEvent.CorrectionAvailable ->
                    if (current == OnboardingGuideStage.CONVERSATION) OnboardingGuideStage.CORRECTION else null

                OnboardingGuideEvent.CorrectionSaved ->
                    if (current == OnboardingGuideStage.CORRECTION) OnboardingGuideStage.STUDY else null

                OnboardingGuideEvent.CorrectionNoFlashcard ->
                    // 미산출 리셋은 CORRECTION 단계일 때만 CONVERSATION 으로 되돌린다.
                    if (current == OnboardingGuideStage.CORRECTION) OnboardingGuideStage.CONVERSATION else null

                OnboardingGuideEvent.StudyInteracted ->
                    if (current == OnboardingGuideStage.STUDY) OnboardingGuideStage.DONE else null
            }
        }
    }
}

/**
 * 온보딩 전이 이벤트.
 *
 * - [CorrectionAvailable]: 교정 가능한 turn 이 생긴 뒤 대시보드로 복귀.
 * - [CorrectionSaved]: 교정 완료(플래시카드 저장 성공).
 * - [CorrectionNoFlashcard]: 교정 미산출(선택 0개 / 카드 미산출 / 저장 실패+세션 없음).
 * - [StudyInteracted]: 학습 화면에서 4개 평가 버튼 중 하나 상호작용.
 */
enum class OnboardingGuideEvent {
    CorrectionAvailable,
    CorrectionSaved,
    CorrectionNoFlashcard,
    StudyInteracted,
}
