package com.app.umma.presentation.auth

import android.util.Log
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.SessionRepository
import com.app.umma.domain.usecase.auth.CheckInitialSetupUseCase
import com.app.umma.domain.usecase.auth.DeleteAccountUseCase
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.auth.IsSessionStillActiveUseCase
import com.app.umma.domain.usecase.auth.LogoutUseCase
import com.app.umma.domain.usecase.notification.HasRequestedLaunchNotificationPermissionUseCase
import com.app.umma.domain.usecase.notification.MarkLaunchNotificationPermissionRequestedUseCase
import com.app.umma.domain.usecase.notification.SetMarketingNotificationEnabledUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationEnabledUseCase
import com.app.umma.domain.usecase.user.ValidateNicknameUseCase
import com.app.umma.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AuthViewModelSessionRecoveryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `setup incomplete signed-in user is not force logged out by stale active session check`() = runTest {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        val uid = "uid-setup-incomplete"
        val authRepository = mockk<AuthRepository>()
        val getCurrentUserUidUseCase = mockk<GetCurrentUserUidUseCase>()
        val checkInitialSetupUseCase = mockk<CheckInitialSetupUseCase>()
        val sessionRepository = mockk<SessionRepository>()
        val isSessionStillActiveUseCase = mockk<IsSessionStillActiveUseCase>()
        val logoutUseCase = mockk<LogoutUseCase>()

        // FirebaseAuth local state survived process recreation, but setup has not been completed yet.
        every { getCurrentUserUidUseCase() } returns MutableStateFlow(uid)
        every { getCurrentUserUidUseCase.getCurrentUserUid() } returns uid
        every { getCurrentUserUidUseCase.getCurrentUserEmail() } returns "tester@example.com"
        coEvery { authRepository.hasValidSession() } returns Result.success(true)
        // users/{uid} is missing or isSetupCompleted=false, so Dashboard must reopen initial setup.
        coEvery { checkInitialSetupUseCase(uid) } returns true
        // This stale/mismatched active session result caused the private-track regression.
        coEvery { isSessionStillActiveUseCase(uid) } returns Result.success(false)
        every { sessionRepository.consumePendingForceLogoutNotice() } returns false
        coEvery { logoutUseCase() } returns Result.success(Unit)

        val viewModel = createViewModel(
            authRepository = authRepository,
            getCurrentUserUidUseCase = getCurrentUserUidUseCase,
            checkInitialSetupUseCase = checkInitialSetupUseCase,
            sessionRepository = sessionRepository,
            isSessionStillActiveUseCase = isSessionStillActiveUseCase,
            logoutUseCase = logoutUseCase,
        )

        viewModel.checkSession()
        advanceTimeBy(1_000L)
        advanceUntilIdle()

        assertEquals(GoogleAuthState.SUCCESS, viewModel.uiState.value.googleState)
        assertNull(viewModel.uiState.value.forceLogoutMessage)
        coVerify(exactly = 0) { isSessionStillActiveUseCase(uid) }
        coVerify(exactly = 0) { logoutUseCase() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `setup completed signed-in user is force logged out when active session is stale`() = runTest {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        val uid = "uid-setup-complete"
        val authRepository = mockk<AuthRepository>()
        val getCurrentUserUidUseCase = mockk<GetCurrentUserUidUseCase>()
        val checkInitialSetupUseCase = mockk<CheckInitialSetupUseCase>()
        val sessionRepository = mockk<SessionRepository>()
        val isSessionStillActiveUseCase = mockk<IsSessionStillActiveUseCase>()
        val logoutUseCase = mockk<LogoutUseCase>()

        every { getCurrentUserUidUseCase() } returns MutableStateFlow(uid)
        every { getCurrentUserUidUseCase.getCurrentUserUid() } returns uid
        every { getCurrentUserUidUseCase.getCurrentUserEmail() } returns "tester@example.com"
        coEvery { authRepository.hasValidSession() } returns Result.success(true)
        coEvery { checkInitialSetupUseCase(uid) } returns false
        coEvery { isSessionStillActiveUseCase(uid) } returns Result.success(false)
        every { sessionRepository.consumePendingForceLogoutNotice() } returns false
        coEvery { logoutUseCase() } returns Result.success(Unit)

        val viewModel = createViewModel(
            authRepository = authRepository,
            getCurrentUserUidUseCase = getCurrentUserUidUseCase,
            checkInitialSetupUseCase = checkInitialSetupUseCase,
            sessionRepository = sessionRepository,
            isSessionStillActiveUseCase = isSessionStillActiveUseCase,
            logoutUseCase = logoutUseCase,
        )

        viewModel.checkSession()
        advanceTimeBy(1_000L)
        advanceUntilIdle()

        assertEquals(GoogleAuthState.IDLE, viewModel.uiState.value.googleState)
        assertEquals(
            "다른 기기에서 로그인되어 자동으로 로그아웃되었습니다.",
            viewModel.uiState.value.forceLogoutMessage
        )
        coVerify(exactly = 1) { isSessionStillActiveUseCase(uid) }
        coVerify(exactly = 1) { logoutUseCase() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `dashboard entry reopens initial setup dialog when setup is incomplete`() = runTest {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        val uid = "uid-setup-incomplete"
        val authRepository = mockk<AuthRepository>()
        val getCurrentUserUidUseCase = mockk<GetCurrentUserUidUseCase>()
        val checkInitialSetupUseCase = mockk<CheckInitialSetupUseCase>()
        val sessionRepository = mockk<SessionRepository>()
        val isSessionStillActiveUseCase = mockk<IsSessionStillActiveUseCase>()
        val logoutUseCase = mockk<LogoutUseCase>()

        // Dashboard 진입 시점에는 FirebaseAuth uid가 살아 있으므로 설정 확인을 다시 수행해야 한다.
        every { getCurrentUserUidUseCase() } returns MutableStateFlow(uid)
        every { getCurrentUserUidUseCase.getCurrentUserUid() } returns uid
        every { getCurrentUserUidUseCase.getCurrentUserEmail() } returns "tester@example.com"
        every { sessionRepository.consumePendingForceLogoutNotice() } returns false
        coEvery { authRepository.hasValidSession() } returns Result.success(true)
        // activeSession만 저장된 부분 생성 문서도 설정 미완료로 판정되어 다이얼로그가 재개되어야 한다.
        coEvery { checkInitialSetupUseCase(uid) } returns true

        val viewModel = createViewModel(
            authRepository = authRepository,
            getCurrentUserUidUseCase = getCurrentUserUidUseCase,
            checkInitialSetupUseCase = checkInitialSetupUseCase,
            sessionRepository = sessionRepository,
            isSessionStillActiveUseCase = isSessionStillActiveUseCase,
            logoutUseCase = logoutUseCase,
        )

        viewModel.startInitialSetupFlow()
        advanceUntilIdle()

        assertEquals(InitialSetupDialogStep.NICKNAME, viewModel.uiState.value.initialSetupDialogStep)
    }

    private fun createViewModel(
        authRepository: AuthRepository,
        getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
        checkInitialSetupUseCase: CheckInitialSetupUseCase,
        sessionRepository: SessionRepository,
        isSessionStillActiveUseCase: IsSessionStillActiveUseCase,
        logoutUseCase: LogoutUseCase,
    ): AuthViewModel {
        val notificationPermission = mockk<HasRequestedLaunchNotificationPermissionUseCase>()
        coEvery { notificationPermission() } returns true

        return AuthViewModel(
            signInWithGoogleUseCase = mockk(relaxed = true),
            getCurrentUserUidUseCase = getCurrentUserUidUseCase,
            initializeUserDataUseCase = mockk(relaxed = true),
            checkInitialSetupUseCase = checkInitialSetupUseCase,
            logoutUseCase = logoutUseCase,
            deleteAccountUseCase = mockk<DeleteAccountUseCase>(relaxed = true),
            authRepository = authRepository,
            validateNicknameUseCase = ValidateNicknameUseCase(),
            sessionRepository = sessionRepository,
            isSessionStillActiveUseCase = isSessionStillActiveUseCase,
            hasRequestedLaunchNotificationPermissionUseCase = notificationPermission,
            markLaunchNotificationPermissionRequestedUseCase = mockk<MarkLaunchNotificationPermissionRequestedUseCase>(relaxed = true),
            setSrsNotificationEnabledUseCase = mockk<SetSrsNotificationEnabledUseCase>(relaxed = true),
            setMarketingNotificationEnabledUseCase = mockk<SetMarketingNotificationEnabledUseCase>(relaxed = true),
        )
    }
}
