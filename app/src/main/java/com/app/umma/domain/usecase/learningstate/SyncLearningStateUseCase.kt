package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.repository.LearningStateRepo
import javax.inject.Inject

/**
 * Firebase 에서 최신 LearningState 를 받아 local cache 를 갱신.
 *
 * SSOT: DASH-001_Dashboard_Entry.md (AC 1, 6, 10)
 * AC 1: Dashboard 진입 시 DashSummary fetch가 수행된다.
 * AC 6: Firebase background sync가 수행된다.
 * AC 10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다.
 *
 * - VM(ViewModel) 이 repo 를 직접 의존하지 않게 한 겹 감쌈.
 * - 결과는 Result<Unit> — 성공 여부만 알면 됨.
 *   실제 새 데이터는 observeLearningState() Flow 로 들어옴.
 */
class SyncLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(): Result<Unit> = repo.sync()
}