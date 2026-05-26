package com.app.umma.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.app.umma.data.source.remote.LearningStateRemote
import com.app.umma.data.source.remote.LearningStateRemoteDataSource
import com.app.umma.data.source.remote.LearningStateRemoteUpdate
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class LearningStateRepoImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sync writes pending local snapshot to remote and clears pending marker`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        repo.sync().getOrThrow()

        // 첫 sync는 createInitial에서 남긴 pending key를 Firestore write-back으로 밀어낸다.
        assertEquals(1, remoteDataSource.syncCalls)
        assertEquals("en", remoteDataSource.lastUpdate?.langStates?.single()?.language)
        assertEquals("en", remoteDataSource.lastUpdate?.dashSummaries?.single()?.language)

        repo.sync().getOrThrow()

        // pending marker가 해제됐기 때문에 다음 sync는 remote fetch 경로로 들어간다.
        assertEquals(1, remoteDataSource.syncCalls)
        assertEquals(1, remoteDataSource.fetchCalls)
    }

    @Test
    fun `sync keeps pending marker when remote write fails`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource(failSync = true)
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        val failed = repo.sync()

        // write-back 실패는 local snapshot을 깨지 않고 pending marker만 유지해야 한다.
        assertTrue(failed.isFailure)
        assertEquals(1, remoteDataSource.syncCalls)

        remoteDataSource.failSync = false
        repo.sync().getOrThrow()

        // 실패한 write-back은 pending marker를 지우지 않으므로 다음 sync에서 다시 시도된다.
        assertEquals(2, remoteDataSource.syncCalls)
    }

    @Test
    fun `sync does not overwrite local language change that happens during remote fetch`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        repo.sync().getOrThrow()

        remoteDataSource.onFetch = {
            // Dashboard 진입 sync가 fetch 중일 때 사용자가 selector에서 다른 언어를 고르는 상황을 재현한다.
            repo.changeSelectedLang(LangCode.JA).getOrThrow()
        }

        repo.sync().getOrThrow()

        val selectedLang = repo.observeLearningState().first().userPref?.selectedLang

        assertEquals(LangCode.JA, selectedLang)
        assertEquals("ja", remoteDataSource.lastUpdate?.userPref?.selectedLearningLanguage)
    }

    @Test
    fun `updateCorrectionSignal updates session and dashboard summaries and dedupes duplicate event`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        repo.sync().getOrThrow()

        val input = CorrectionSignalUpdateInput(
            uid = USER_UID,
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            sourceEventId = "turn-1",
            recentMinutes = 9,
            recentTopic = "Travel",
            updatedAt = 2_000L
        )

        val first = repo.updateCorrectionSignal(input).getOrThrow()
        val state = repo.observeLearningState().first()

        // correction signal 은 LangState 를 건드리지 않고 session/dash summary 만 함께 바꿔야 한다.
        // 이 경계가 흔들리면 Chat turn 신호가 분석 상태를 건드리는 부작용이 생긴다.
        assertTrue(first.applied)
        assertTrue(state.sessionSummaries[LangCode.EN]?.correctionAvailable == true)
        assertTrue(state.dashSummaries[LangCode.EN]?.correctionAvailable == true)
        assertEquals(9, state.sessionSummaries[LangCode.EN]?.recentMinutes)
        assertEquals("Travel", state.dashSummaries[LangCode.EN]?.recentTopic)

        repo.sync().getOrThrow()

        // pending marker 가 남아 있던 session/dash summary 가 remote write-back payload 에도 들어가야 한다.
        // local-first 상태가 remote 복구 후에도 동일하게 재현되는지 확인하는 부분이다.
        assertEquals(2, remoteDataSource.syncCalls)
        assertEquals("en", remoteDataSource.lastUpdate?.sessionSummaries?.single()?.language)
        assertEquals("en", remoteDataSource.lastUpdate?.dashSummaries?.single()?.language)

        val duplicate = repo.updateCorrectionSignal(input).getOrThrow()
        assertFalse(duplicate.applied)
    }

    @Test
    fun `updateCorrectionSignal does not re-enable correction after same event was completed`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        val input = CorrectionSignalUpdateInput(
            uid = USER_UID,
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            sourceEventId = "turn-1",
            recentMinutes = 9,
            recentTopic = "Travel",
            updatedAt = 2_000L
        )

        repo.updateCorrectionSignal(input).getOrThrow()
        // Correction 완료 파이프라인이 같은 summary를 false로 내린 상태를 만든다.
        // 이후 같은 turn retry가 오면 repo의 idempotent 기준이 event id뿐인지 검증할 수 있다.
        repo.updateLanguageState(
            LangStateUpdateInput(
                uid = USER_UID,
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "analysis-after-correction",
                currentState = LangState.initial(LangCode.EN, createdAt = 1_000L),
                preparedState = LangState.initial(LangCode.EN, createdAt = 1_000L).copy(
                    updatedAt = 3_000L,
                    lastAnalysisEventId = "analysis-after-correction"
                ),
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "I need book ticket.",
                        tokenCount = 5,
                        durationMs = 2_000L
                    )
                ),
                correctionResult = null,
                correctionAvailableOverride = false,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        ).getOrThrow()

        val retry = repo.updateCorrectionSignal(input.copy(updatedAt = 4_000L)).getOrThrow()
        val state = repo.observeLearningState().first()

        // 같은 sourceEventId 재도착은 현재 correctionAvailable 값과 무관하게 stale retry로 본다.
        // 이 회귀는 조건에 correctionAvailable을 포함했을 때 다시 true로 올라가던 문제를 막는다.
        assertFalse(retry.applied)
        assertFalse(state.sessionSummaries[LangCode.EN]?.correctionAvailable == true)
        assertFalse(state.dashSummaries[LangCode.EN]?.correctionAvailable == true)
    }

    @Test
    fun `updateCorrectionSignal fails when input language does not match selected language`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()

        val result = repo.updateCorrectionSignal(
            CorrectionSignalUpdateInput(
                uid = USER_UID,
                lang = LangCode.JA,
                sessionMemoryKey = "session-ja",
                sourceEventId = "turn-ja-1",
                updatedAt = 2_000L
            )
        )

        // Chat 쪽에서 stale language 신호를 보내면 현재 선택 언어의 summary를 오염시키지 않아야 한다.
        assertTrue(result.isFailure)
        assertEquals(
            "correction signal lang must match selected learning language",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `updateCorrectionSignal fails when current summaries are missing`() = runBlocking {
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        repo.createInitial(
            userUid = USER_UID,
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = LangCode.EN),
            langState = LangState.initial(LangCode.EN, createdAt = 1_000L),
            dashSummary = DashSummary.initial(LangCode.EN),
            sessionSummary = SessionSummary.initial(LangCode.EN),
            flashcardSummary = FlashcardSummary.initial(LangCode.EN)
        ).getOrThrow()
        repo.sync().getOrThrow()

        remoteDataSource.lastUpdate = LearningStateRemoteUpdate(
            userPref = remoteDataSource.lastUpdate?.userPref,
            langStates = remoteDataSource.lastUpdate?.langStates.orEmpty(),
            dashSummaries = emptyList(),
            sessionSummaries = emptyList(),
            flashcardSummaries = remoteDataSource.lastUpdate?.flashcardSummaries.orEmpty()
        )
        repo.sync().getOrThrow()

        val result = repo.updateCorrectionSignal(
            CorrectionSignalUpdateInput(
                uid = USER_UID,
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                sourceEventId = "turn-1",
                updatedAt = 2_000L
            )
        )

        // LS-009는 summary 초기화 흐름이 아니라 기존 summary에 신호를 전파하는 경로다.
        // summary가 빠진 상태를 조용히 복구하면 Initial Setup/Sync 결손이 숨겨진다.
        assertTrue(result.isFailure)
        assertEquals("session summary is missing for lang=en", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sync returns success when remote has no user document (new user before setup)`() = runBlocking {
        // createInitial() 없이 sync() 호출 — users/{uid} 문서 미생성 상태 시뮬레이션.
        // RecordingLearningStateRemoteDataSource.fetch() 는 lastUpdate==null → userPref=null 반환.
        val remoteDataSource = RecordingLearningStateRemoteDataSource()
        val repo = createRepository(remoteDataSource)

        val result = repo.sync()

        // repo 레이어는 userPref=null 인 empty remote 도 성공으로 처리한다.
        // Snackbar 억제 책임은 DashboardViewModel.setupConfirmed 에 있다.
        assertTrue(result.isSuccess)
        assertEquals(0, remoteDataSource.syncCalls)  // pending keys 없음 → write-back 없음
        assertEquals(1, remoteDataSource.fetchCalls) // fetch 경로로 진입
    }

    private fun createRepository(
        remoteDataSource: RecordingLearningStateRemoteDataSource
    ): LearningStateRepoImpl {
        // DataStore는 실제 파일을 쓰되, 각 테스트마다 독립된 임시 파일을 사용한다.
        val dataStore = PreferenceDataStoreFactory.create {
            temporaryFolder.newFile("learning_state_${System.nanoTime()}.preferences_pb")
        }
        return LearningStateRepoImpl(
            dataStore = dataStore,
            remoteDataSource = remoteDataSource,
            authRepository = FakeAuthRepository()
        )
    }

    private class RecordingLearningStateRemoteDataSource(
        var failSync: Boolean = false
    ) : LearningStateRemoteDataSource {
        var fetchCalls: Int = 0
        var syncCalls: Int = 0
        var lastUpdate: LearningStateRemoteUpdate? = null
        var onFetch: (suspend () -> Unit)? = null

        override suspend fun fetch(userUid: String): LearningStateRemote {
            fetchCalls += 1
            onFetch?.invoke()
            onFetch = null
            // write-back이 끝난 뒤에는 마지막으로 반영된 snapshot을 다시 읽을 수 있어야 한다.
            val update = lastUpdate
            return LearningStateRemote(
                userPref = update?.userPref,
                langStates = update?.langStates.orEmpty(),
                dashSummaries = update?.dashSummaries.orEmpty(),
                sessionSummaries = update?.sessionSummaries.orEmpty(),
                flashcardSummaries = update?.flashcardSummaries.orEmpty()
            )
        }

        override suspend fun sync(
            userUid: String,
            update: LearningStateRemoteUpdate
        ): Result<Unit> {
            syncCalls += 1
            if (failSync) {
                return Result.failure(IOException("simulated remote sync failure"))
            }
            lastUpdate = update
            return Result.success(Unit)
        }
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUserUid: Flow<String?> = flowOf(USER_UID)

        override suspend fun signInWithGoogle(idToken: String): Result<String> =
            Result.success(USER_UID)

        override fun getCurrentUserUid(): String = USER_UID

        override fun getCurrentUserEmail(): String = "test@example.com"

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun hasValidSession(): Result<Boolean> = Result.success(true)
    }

    private companion object {
        const val USER_UID = "uid-1"
    }
}
