package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.LearningStateRepo
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

/**
 * 현재 사용자의 언어 설정을 단건 조회하는 UseCase입니다.
 *
 * AI 콘텐츠 신고처럼 화면 상태에 언어 설정 snapshot을 함께 남겨야 하는 경계에서 사용합니다.
 * 호출부가 LearningStateRepo를 직접 알면 presentation이 저장소 구조에 묶이므로 UseCase로 감쌉니다.
 */
class GetUserLanguagePreferenceUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(): UserLangPref? {
        // observeUserPref는 앱 전역 cache를 보는 flow다.
        // 신고 접수는 현재 화면의 한 순간 snapshot이면 충분하므로 첫 값만 읽고 끝낸다.
        return repo.observeUserPref().firstOrNull()
    }
}
