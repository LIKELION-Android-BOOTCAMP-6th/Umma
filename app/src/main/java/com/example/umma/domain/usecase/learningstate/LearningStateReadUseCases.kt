package com.example.umma.domain.usecase.learningstate

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 학습 상태를 읽는 UseCase 묶음.
 */
class ObserveLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    // 전역 스냅샷을 UI와 상위 상태에 그대로 노출한다.
    operator fun invoke(): Flow<GlobalLangState> = repo.observeLearningState()
}

class ObserveDashSummaryUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    // Dashboard는 현재 선택 언어의 요약만 구독하면 된다.
    operator fun invoke(lang: LangCode): Flow<DashSummary?> = repo.observeDashSummary(lang)
}

class PreloadLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    // 앱 시작 시 local cache를 먼저 채워 넣는다.
    suspend operator fun invoke(): Result<Unit> = repo.preload()
}
