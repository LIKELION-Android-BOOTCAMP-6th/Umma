package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Dashboard 같은 진입점이 local cache와 remote restore를 어떻게 해석할지 나타내는 결과.
 *
 * UseCase는 여기까지의 복구 결과만 알려주고, 이후 background sync 여부나 UI 문구는
 * 호출부가 해석한다.
 */
sealed interface LearningStateLoadResult {
    // local cache만으로 바로 복구된 상태.
    data object LoadedFromLocal : LearningStateLoadResult

    // local cache가 비어 있어 sync 후 remote에서 다시 채운 상태.
    data object RestoredFromRemote : LearningStateLoadResult

    // local/remote 어디에도 userPref가 없어 초기 설정 흐름이 필요한 상태.
    data object MissingSetup : LearningStateLoadResult
}

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
 * Dashboard 진입 시 local-first 복구와 remote restore를 한 번에 정리하는 공통 진입 계약.
 *
 * 화면마다 preload/sync 조건을 따로 두지 않고, "local이 있으면 바로 쓰고 없으면 한 번 복구한다"
 * 는 최소 규칙만 domain에서 보장한다.
 */
class EnsureLearningStateLoadedUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(): Result<LearningStateLoadResult> = runCatching {
        // 1) local cache를 먼저 복원한다.
        //    실패해도 관측 가능한 local state가 없다면 다음 단계에서 remote restore를 시도한다.
        repo.preload().getOrThrow()

        // 2) local에 userPref가 있으면 이미 복구된 상태로 본다.
        val localUserPref = repo.observeUserPref().firstOrNull()
        if (localUserPref != null) {
            return@runCatching LearningStateLoadResult.LoadedFromLocal
        }

        // 3) local이 비어 있으면 existing user 여부를 판단하기 전에 remote restore를 한 번 시도한다.
        repo.sync().getOrThrow()

        // 4) sync 이후에도 userPref가 없으면 신규 사용자 또는 초기 설정 미완료로 해석한다.
        if (repo.observeUserPref().firstOrNull() != null) {
            LearningStateLoadResult.RestoredFromRemote
        } else {
            LearningStateLoadResult.MissingSetup
        }
    }
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
