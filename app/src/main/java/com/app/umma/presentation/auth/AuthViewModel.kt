package com.app.umma.presentation.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.SessionRepository
import com.app.umma.domain.usecase.auth.CheckInitialSetupUseCase
import com.app.umma.domain.usecase.auth.DeleteAccountUseCase
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.auth.IsSessionStillActiveUseCase
import com.app.umma.domain.usecase.auth.LogoutUseCase
import com.app.umma.domain.usecase.auth.SignInWithGoogleUseCase
import com.app.umma.domain.usecase.notification.HasRequestedLaunchNotificationPermissionUseCase
import com.app.umma.domain.usecase.notification.MarkLaunchNotificationPermissionRequestedUseCase
import com.app.umma.domain.usecase.notification.SetMarketingNotificationEnabledUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationEnabledUseCase
import com.app.umma.domain.usecase.user.GetSystemLanguageUseCase
import com.app.umma.domain.usecase.user.InitializeUserDataUseCase
import com.app.umma.domain.usecase.user.ValidateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AuthViewModel @Inject constructor(
    /**
     * 로그인 실행용
     */
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    /**
     * 로그인 상태 확인용
     */
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
    /**
     * 첫 사용자 데이터 Firestore 저장용 */
    private val initializeUserDataUseCase: InitializeUserDataUseCase,
    private val checkInitialSetupUseCase: CheckInitialSetupUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val authRepository: AuthRepository,
    private val validateNicknameUseCase: ValidateNicknameUseCase,
    private val getSystemLanguageUseCase: GetSystemLanguageUseCase,
    private val sessionRepository: SessionRepository,
    private val isSessionStillActiveUseCase: IsSessionStillActiveUseCase,
    private val hasRequestedLaunchNotificationPermissionUseCase: HasRequestedLaunchNotificationPermissionUseCase,
    private val markLaunchNotificationPermissionRequestedUseCase: MarkLaunchNotificationPermissionRequestedUseCase,
    private val setSrsNotificationEnabledUseCase: SetSrsNotificationEnabledUseCase,
    private val setMarketingNotificationEnabledUseCase: SetMarketingNotificationEnabledUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(googleState = GoogleAuthState.FAILED))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeAuthStatus()
    }

    /**
     * 현재 로그인된 상태인지 감시
     */
    private fun observeAuthStatus() {
        viewModelScope.launch {
            getCurrentUserUidUseCase().collect { uid ->
                if (uid != null) {
                    _uiState.update {
                        it.copy(googleState = GoogleAuthState.SUCCESS)
                    }
                } else {
                    _uiState.update {
                        it.copy(googleState = GoogleAuthState.IDLE)
                    }
                }
            }
        }
    }

    /**
     * 구글 로그인 시도 함수
     * 로그인 시작 시 로딩 상태로 변경하여 중복 실행 방지 isLoading = true
     * useCase 실행 후 결과로 ui 상태 업데이트
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            /** 로그인 시작 로딩... */
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
            try {
                val result = signInWithGoogleUseCase(idToken)
                result.onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            googleState = GoogleAuthState.SUCCESS
                        )
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            googleState = GoogleAuthState.FAILED,
                            errorMessage = "google 로그인 시도 중 에러 발생"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "google 로그인 시도 중 에러 발생"
                    )
                }
                Log.e("UmmaDev", "signInWithGoogle - ", e)
            }
        }
    }

    fun updateErrorMessage(message: String?) {
        _uiState.update {
            it.copy(
                errorMessage = message,
            )
        }
    }

    fun updateNicknameErrorMessage(message: String?) {
        _uiState.update {
            it.copy(
                nicknameError = message,
            )
        }
    }

    fun updateLearningLanguageErrorMessage(message: String?) {
        _uiState.update {
            it.copy(
                learningLanguageError = message,
            )
        }
    }

    /**
     * Google 로그인 버튼 중복 클릭 방지를 위해 추가
     */
    fun updateLoading(isLoading: Boolean) {
        _uiState.update {
            it.copy(isLoading = isLoading)
        }
    }


    /**
     * 신규 가입자 대상 초기 설정 프로세스 시작
     * Dashboard 진입 시 1회 호출
     * 신규 사용자 여부 확인 후 초기 설정 다이얼로그 표시 여부 결정
     */
    fun startInitialSetupFlow() {
        viewModelScope.launch {
            // 이미 초기 설정 확인중 or 다이얼로그 열린 상태면 중복 실행 X
            if (_uiState.value.isInitialSetupChecking ||
                _uiState.value.initialSetupDialogStep != InitialSetupDialogStep.NONE
            ) {
                return@launch
            }
            _uiState.update { it.copy(isInitialSetupChecking = true) }

            // uid 없으면 비로그인 상태
            val uid = getCurrentUserUidUseCase.getCurrentUserUid()
            if (uid == null) {
                _uiState.update { it.copy(isInitialSetupChecking = false) }
                return@launch
            }

            // Firestore 에서 신규 사용자 여부 확인
            // 판단 기준: users/{uid} 문서 없음 또는 isSetupCompleted == false
            val isNewUser = checkInitialSetupUseCase(uid)

            _uiState.update {
                it.copy(
                    isInitialSetupChecking = false,
                    initialSetupDialogStep = if (isNewUser) {
                        InitialSetupDialogStep.NICKNAME
                    } else {
                        InitialSetupDialogStep.NONE
                    }
                )
            }
        }
    }

    /**
     * 닉네임 다이얼로그 확인 클릭 시 실행
     * 닉네임 검사, 저장, 다이얼로그 상태 변경
     */
    fun onNicknameConfirm(nickname: String) {
        // 닉네임 겅증 실패 시 안내 문구 표시
        if (!validateNicknameUseCase(nickname)) {
            _uiState.update { it.copy(nicknameError = "닉네임은 2자 이상 10자 이하로 입력해 주세요.") }
            return
        }
        _uiState.update {
            it.copy(
                nickname = nickname,
                initialSetupDialogStep = InitialSetupDialogStep.LANGUAGE,
                nicknameError = null
            )
        }
    }

    fun onLanguageSelectAndSave(
        selectedLearningLanguage: LangCode,
        notificationPermissionGranted: Boolean
    ) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val uid = getCurrentUserUidUseCase.getCurrentUserUid()
            val email = getCurrentUserUidUseCase.getCurrentUserEmail()
            if (uid.isNullOrBlank() || email.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        learningLanguageError = "관리자에게 문의 바랍니다."
                    )
                }
                return@launch
            }

            val result = initializeUserDataUseCase(
                uid = uid,
                email = email,
                nickname = _uiState.value.nickname,
                // 시스템 언어 필요시 아래 줄로 복구
                // primaryLang = getSystemLanguageUseCase(),
                primaryLang = LangCode.KO,
                selectedLang = selectedLearningLanguage,
                topics = emptyList()
            )
            result.onSuccess {
                // 신규 사용자 최초 설정: 알림 권한이 허용된 경우에만 학습/마케팅 알림을 기본 활성화한다.
                setSrsNotificationEnabledUseCase(notificationPermissionGranted)
                setMarketingNotificationEnabledUseCase(notificationPermissionGranted)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        initialSetupDialogStep = InitialSetupDialogStep.NONE,
                        learningLanguageError = null
                    )
                }
            }.onFailure { e ->
                Log.e("AuthViewModel", "onLanguageSelectAndSave 실패", e)  // ← 추가

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        learningLanguageError = "저장에 실패했습니다. 다시 시도해주세요"
                    )
                }
            }
        }
    }

    /**
     * 로컬 Firebase 세션 확인하여 로그인 여부 판단
     * 비로그인 -> OnBoarding 이동
     * 로그인 O -> Firebase 서버에 세션 유효성 확인
     *      - 있다 -> DashBoard 이동
     *      - 없다 -> 로그아웃 후 OnBoarding
     *      - 서버 오류 -> 에러메시지 + 재시도 버튼 표시
     */
    fun checkSession() {
        viewModelScope.launch {
            // 앱 최초 실행(설치 후 첫 실행) 시에만 알림 권한을 요청한다.
            if (!hasRequestedLaunchNotificationPermissionUseCase()) {
                _uiState.update { it.copy(shouldRequestNotificationPermission = true) }
            }

            // 강제 로그아웃 FCM을 처리한 적 있다면(백그라운드/종료 상태) 안내 메시지 표시 대상
            val hasPendingForceLogout = sessionRepository.consumePendingForceLogoutNotice()

            _uiState.update {
                it.copy(
                    isSessionChecking = true,
                    sessionError = null,
                    forceLogoutMessage = if (hasPendingForceLogout) {
                        FORCE_LOGOUT_MESSAGE
                    } else {
                        it.forceLogoutMessage
                    }
                )
            }
            // 스플래시 화면 0.1초 만에 사라져서 지연 추가
            delay(1000L)

            // 로컬 uid 확인
            val uid = getCurrentUserUidUseCase.getCurrentUserUid()
            // 비로그인 -> OnBoarding 이동
            if (uid == null) {
                _uiState.update {
                    it.copy(
                        isSessionChecking = false,
                        googleState = GoogleAuthState.IDLE
                    )
                }
                return@launch
            }

            // 로그인 O -> 재확인: Firebase 서버에서 세션 유효성
            val result = authRepository.hasValidSession()
            result.onSuccess { isValid ->
                if (!isValid) {
                    _uiState.update {
                        it.copy(
                            isSessionChecking = false,
                            googleState = GoogleAuthState.IDLE
                        )
                    }
                    return@onSuccess
                }

                // FCM 유실 등으로 force_logout을 못받은 경우를 대비한 fallback 검증
                val sessionStillActive = isSessionStillActiveUseCase(uid).getOrDefault(true)
                if (!sessionStillActive) {
                    logoutUseCase()
                    _uiState.update {
                        it.copy(
                            isSessionChecking = false,
                            googleState = GoogleAuthState.IDLE,
                            forceLogoutMessage = FORCE_LOGOUT_MESSAGE
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSessionChecking = false,
                            googleState = GoogleAuthState.SUCCESS
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isSessionChecking = false,
                        sessionError = "네트워크 오류가 발생했습니다 재시도 바랍니다"
                    )
                }
                Log.e("Auth", "checkSession 실패", e)
            }
        }
    }

    /**
     * 강제 로그아웃 안내 다이얼로그 "확인" 클릭 시 호출.
     * 다이얼로그를 닫고 정상 네비게이션 흐름을 재개한다.
     */
    fun consumeForceLogoutMessage() {
        _uiState.update { it.copy(forceLogoutMessage = null) }
    }

    /**
     * 앱 최초 실행 시 알림 권한 요청 결과를 처리한다.
     * 허용/거부 여부와 관계없이 다시 요청하지 않도록 기록만 한다.
     * (학습/마케팅 알림 기본값 적용은 신규 사용자 초기 설정 시
     * [onLanguageSelectAndSave] 에서 이뤄진다.)
     */
    fun onLaunchNotificationPermissionResult() {
        _uiState.update { it.copy(shouldRequestNotificationPermission = false) }
        viewModelScope.launch {
            markLaunchNotificationPermissionRequestedUseCase()
        }
    }

    /**
     * 세션 확인 실패 시 재시도 버튼에 넣을 함수
     * AppEntryScreen 에서 재시도 버튼 클릭 시 호출
     */
    fun retryCheckSession() {
        checkSession()
    }

    /**
     * 현재 로그인된 사용자의 로그아웃 처리
     * Firebase Authentication 세션 완전히 해제
     * 파이어베이스가 구글 서버에 저장된 유저의 로그인 토큰을 만료, 인증 세션 끊는 작업
     */
    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = logoutUseCase()
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        googleState = GoogleAuthState.IDLE,
                        errorMessage = null,
                        nickname = "",
                        initialSetupDialogStep = InitialSetupDialogStep.NONE,
                        isLogoutCompleted = true,
                        learningLanguageError = null
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "로그아웃에 실패했습니다."
                    )
                }
            }

        }
    }

    /**
     * 회원탈퇴용 Google 재인증을 시작하기 전 로딩을 켠다.
     */
    fun beginDeleteAccountReauthentication() {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }
    }

    /**
     * 회원탈퇴용 Google 재인증이 실패하거나 취소된 경우를 처리한다.
     */
    fun cancelDeleteAccountReauthentication(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message
            )
        }
    }

    /**
     * 회원탈퇴를 수행한다.
     */
    fun getCurrentUserEmail(): String? {
        return authRepository.getCurrentUserEmail()
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
            val result = deleteAccountUseCase()
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        googleState = GoogleAuthState.IDLE,
                        errorMessage = null,
                        nickname = "",
                        initialSetupDialogStep = InitialSetupDialogStep.NONE,
                        isDeleteAccountCompleted = true,
                        learningLanguageError = null
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "회원탈퇴에 실패했습니다. 다시 시도해주세요."
                    )
                }
            }
        }
    }

    private companion object {
        const val FORCE_LOGOUT_MESSAGE = "다른 기기에서 로그인되어 자동으로 로그아웃되었습니다."
    }
}
