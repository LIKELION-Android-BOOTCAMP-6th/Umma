package com.example.umma.domain.usecase.learningstate

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class ObserveLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
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

/**
 * 현재 선택된 학습 언어를 단건 조회하는 UseCase 입니다.
 */
class GetSelectedLearningLanguageUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(): LangCode? {
        return repo.observeUserPref().firstOrNull()?.selectedLang
    }
}