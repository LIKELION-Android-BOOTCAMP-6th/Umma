package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class EnsureLearningStateLoadedUseCaseTest {

    @Test
    fun `returns LoadedFromLocal when local userPref already exists`() = runBlocking {
        // 로컬 캐시가 살아 있으면 remote restore 없이 즉시 Dashboard를 열 수 있어야 한다.
        val repo = RecordingLearningStateRepo(
            initialState = stateWithUserPref()
        )
        val useCase = EnsureLearningStateLoadedUseCase(repo)

        val result = useCase().getOrThrow()

        assertEquals(LearningStateLoadResult.LoadedFromLocal, result)
        assertEquals(1, repo.preloadCalls)
        assertEquals(0, repo.syncCalls)
    }

    @Test
    fun `returns RestoredFromRemote when local is empty but sync restores userPref`() = runBlocking {
        // 앱 재설치처럼 local cache가 비어도 remote restore로 기존 사용자를 다시 살려야 한다.
        val repo = RecordingLearningStateRepo(
            initialState = GlobalLangState.initial(),
            syncBehavior = SyncBehavior.RESTORE_USER_PREF
        )
        val useCase = EnsureLearningStateLoadedUseCase(repo)

        val result = useCase().getOrThrow()

        assertEquals(LearningStateLoadResult.RestoredFromRemote, result)
        assertEquals(1, repo.preloadCalls)
        assertEquals(1, repo.syncCalls)
    }

    @Test
    fun `returns MissingSetup when local and remote both have no userPref`() = runBlocking {
        // 신규 사용자나 초기 설정 미완료 사용자는 복구 성공이 아니라 MissingSetup 으로 구분해야 한다.
        val repo = RecordingLearningStateRepo(
            initialState = GlobalLangState.initial(),
            syncBehavior = SyncBehavior.NOOP
        )
        val useCase = EnsureLearningStateLoadedUseCase(repo)

        val result = useCase().getOrThrow()

        assertEquals(LearningStateLoadResult.MissingSetup, result)
        assertEquals(1, repo.preloadCalls)
        assertEquals(1, repo.syncCalls)
    }

    @Test
    fun `returns failure when sync fails`() = runBlocking {
        // sync 실패를 MissingSetup 으로 바꾸면 기존 사용자 오판이 생기므로 failure 로 남겨야 한다.
        val repo = RecordingLearningStateRepo(
            initialState = GlobalLangState.initial(),
            syncBehavior = SyncBehavior.FAILURE
        )
        val useCase = EnsureLearningStateLoadedUseCase(repo)

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(1, repo.preloadCalls)
        assertEquals(1, repo.syncCalls)
    }

    private fun stateWithUserPref(): GlobalLangState {
        val userPref = UserLangPref.initial(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )
        return GlobalLangState.initial().copy(
            userPref = userPref,
            langStates = mapOf(
                LangCode.EN to LangState.initial(LangCode.EN)
            ),
            dashSummaries = mapOf(
                LangCode.EN to DashSummary.initial(LangCode.EN)
            ),
            sessionSummaries = mapOf(
                LangCode.EN to SessionSummary.initial(LangCode.EN)
            ),
            flashcardSummaries = mapOf(
                LangCode.EN to FlashcardSummary.initial(LangCode.EN)
            )
        )
    }

    private class RecordingLearningStateRepo(
        initialState: GlobalLangState,
        private val syncBehavior: SyncBehavior = SyncBehavior.NOOP
    ) : LearningStateRepo {
        private val state = MutableStateFlow(initialState)
        var preloadCalls: Int = 0
        var syncCalls: Int = 0

        override fun observeLearningState(): Flow<GlobalLangState> = state

        override fun observeUserPref() = state.map { it.userPref }

        override fun observeLangState(lang: LangCode) = state.map { it.langStates[lang] }

        override fun observeDashSummary(lang: LangCode) = state.map { it.dashSummaries[lang] }

        override fun observeSessionSummary(lang: LangCode) = state.map { it.sessionSummaries[lang] }

        override fun observeFlashcardSummary(lang: LangCode) =
            state.map { it.flashcardSummaries[lang] }

        override suspend fun preload(): Result<Unit> {
            // preload는 local cache 복원 시도를 세는 용도다. state 자체는 이 테스트에서 직접 주입한다.
            preloadCalls += 1
            return Result.success(Unit)
        }

        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(
            input: LangStateUpdateInput
        ): Result<LearningStateUpdateResult> =
            Result.failure(UnsupportedOperationException("not used in this test"))

        override suspend fun updateFlashcardSummary(
            input: FlashcardSummaryUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult> =
            Result.failure(UnsupportedOperationException("not used in this test"))

        override suspend fun updateCorrectionSignal(
            input: CorrectionSignalUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult> =
            Result.failure(UnsupportedOperationException("not used in this test"))

        override suspend fun createInitial(
            userUid: String,
            userPref: UserLangPref,
            langState: LangState,
            dashSummary: DashSummary,
            sessionSummary: SessionSummary,
            flashcardSummary: FlashcardSummary
        ): Result<Unit> = Result.success(Unit)

        override suspend fun clear(): Result<Unit> = Result.success(Unit)

        override suspend fun sync(): Result<Unit> {
            // local이 비어 있을 때 remote restore가 실제로 일어나는지를 세기 위한 fake 동작이다.
            syncCalls += 1
            return when (syncBehavior) {
                SyncBehavior.RESTORE_USER_PREF -> {
                    state.value = state.value.copy(
                        userPref = UserLangPref.initial(
                            primaryLang = LangCode.KO,
                            selectedLang = LangCode.EN
                        )
                    )
                    Result.success(Unit)
                }

                SyncBehavior.NOOP -> Result.success(Unit)

                SyncBehavior.FAILURE -> Result.failure(IOException("simulated sync failure"))
            }
        }
    }

    private enum class SyncBehavior {
        RESTORE_USER_PREF,
        NOOP,
        FAILURE
    }
}
