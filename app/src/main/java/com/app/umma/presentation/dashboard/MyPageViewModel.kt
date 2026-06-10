package com.app.umma.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.usecase.learningstate.ChangePrimaryLangUseCase
import com.app.umma.domain.usecase.learningstate.SyncLearningStateUseCase
import com.app.umma.domain.usecase.notification.ObserveMarketingNotificationSettingsUseCase
import com.app.umma.domain.usecase.notification.ObserveSrsNotificationSettingsUseCase
import com.app.umma.domain.usecase.notification.RefreshNotificationTimezoneUseCase
import com.app.umma.domain.usecase.notification.SetMarketingNotificationEnabledUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationEnabledUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationTimeUseCase
import com.app.umma.domain.usecase.notification.SyncCurrentNotificationDeviceUseCase
import com.app.umma.domain.usecase.user.GetUserNicknameUseCase
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * 마이페이지의 알림 설정, 기기 권한 동기화, 주언어(primaryLang) 변경을 담당하는 ViewModel.
 */
@HiltViewModel
class MyPageViewModel @Inject constructor(
    observeSrsNotificationSettingsUseCase: ObserveSrsNotificationSettingsUseCase,
    observeMarketingNotificationSettingsUseCase: ObserveMarketingNotificationSettingsUseCase,
    private val authRepository: AuthRepository,
    private val getUserNicknameUseCase: GetUserNicknameUseCase,
    private val setSrsNotificationEnabledUseCase: SetSrsNotificationEnabledUseCase,
    private val setMarketingNotificationEnabledUseCase: SetMarketingNotificationEnabledUseCase,
    private val setSrsNotificationTimeUseCase: SetSrsNotificationTimeUseCase,
    private val refreshNotificationTimezoneUseCase: RefreshNotificationTimezoneUseCase,
    private val syncCurrentNotificationDeviceUseCase: SyncCurrentNotificationDeviceUseCase,
    private val firebaseFunctions: FirebaseFunctions,
    private val learningStateRepo: LearningStateRepo,
    private val changePrimaryLangUseCase: ChangePrimaryLangUseCase,
    private val syncLearningStateUseCase: SyncLearningStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageNotificationUiState())
    val uiState: StateFlow<MyPageNotificationUiState> = _uiState.asStateFlow()

    private val _profileState = MutableStateFlow(MyPageProfileUiState())
    val profileState: StateFlow<MyPageProfileUiState> = _profileState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSrsNotificationSettingsUseCase().collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        srsSettings = settings,
                        isSaving = false
                    )
                }
            }
        }

        viewModelScope.launch {
            observeMarketingNotificationSettingsUseCase().collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        marketingSettings = settings,
                        isSaving = false
                    )
                }
            }
        }
        viewModelScope.launch {
            learningStateRepo.observeUserPref().collect { pref ->
                pref?.primaryLang?.let { lang ->
                    _profileState.update { it.copy(primaryLang = lang) }
                }
            }
        }
    }

    /**
     * 화면 진입 시 현재 타임존과 권한 상태를 서버에 동기화한다.
     */
    fun onScreenStarted(
        permissionGranted: Boolean,
        timezone: String
    ) {
        viewModelScope.launch {
            // 학습상태를 미리 로드해 observeUserPref가 실제 primaryLang을 emit하도록 한다.
            // 이 호출이 없으면 다이얼로그 초기 선택값이 실제 저장값과 다르게 표시될 수 있다.
            learningStateRepo.preload()

            val uid = authRepository.getCurrentUserUid()
            if (!uid.isNullOrBlank()) {
                val nickname = getUserNicknameUseCase(uid).orEmpty()
                _uiState.update { it.copy(nickname = nickname) }
            }
            refreshNotificationTimezoneUseCase(timezone)
            syncCurrentNotificationDeviceUseCase(
                permissionGranted = permissionGranted,
                timezone = timezone
            )
        }
    }

    /**
     * 학습 알림 토글 이벤트를 처리한다.
     */
    fun onSrsNotificationToggleChanged(
        enabled: Boolean,
        permissionGranted: Boolean
    ) {
        if (enabled && !permissionGranted) {
            _uiState.update {
                it.copy(permissionRequestTarget = NotificationPermissionRequestTarget.SRS)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            setSrsNotificationEnabledUseCase(enabled)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = error.message ?: "학습 알림 설정 저장에 실패했습니다."
                        )
                    }
                }
        }
    }

    /**
     * 마케팅 알림 토글 이벤트를 처리한다.
     */
    fun onMarketingNotificationToggleChanged(
        enabled: Boolean,
        permissionGranted: Boolean
    ) {
        if (enabled && !permissionGranted) {
            _uiState.update {
                it.copy(permissionRequestTarget = NotificationPermissionRequestTarget.MARKETING)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            setMarketingNotificationEnabledUseCase(enabled)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = error.message ?: "마케팅 알림 설정 저장에 실패했습니다."
                        )
                    }
                }
        }
    }

    /**
     * 권한 요청이 승인되면 해당 토글을 다시 ON 한다.
     */
    fun onNotificationPermissionGranted(timezone: String) {
        val target = _uiState.value.permissionRequestTarget ?: return
        _uiState.update { it.copy(permissionRequestTarget = null) }
        onScreenStarted(permissionGranted = true, timezone = timezone)

        when (target) {
            NotificationPermissionRequestTarget.SRS -> {
                onSrsNotificationToggleChanged(enabled = true, permissionGranted = true)
            }

            NotificationPermissionRequestTarget.MARKETING -> {
                onMarketingNotificationToggleChanged(enabled = true, permissionGranted = true)
            }
        }
    }

    /**
     * 권한 요청이 거부되면 서버와 UI를 OFF 상태로 유지한다.
     */
    fun onNotificationPermissionDenied(timezone: String) {
        _uiState.update {
            it.copy(
                permissionRequestTarget = null,
                message = "알림 권한이 없어 알림 설정이 꺼졌습니다."
            )
        }
        onScreenStarted(permissionGranted = false, timezone = timezone)
    }

    /**
     * 시간 선택 다이얼로그를 연다.
     */
    fun onTimeSettingClicked() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    /**
     * 시간 선택 다이얼로그를 닫는다.
     */
    fun onTimePickerDismissed() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    /**
     * 선택된 학습 알림 시간을 저장한다.
     */
    fun onTimeSelected(totalMinutes: Int) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    showTimePicker = false,
                    message = null
                )
            }
            setSrsNotificationTimeUseCase(totalMinutes)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = error.message ?: "학습 알림 시간 저장에 실패했습니다."
                        )
                    }
                }
        }
    }

    /**
     * 사용자 메시지를 소비한다.
     */
    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }

    fun onTestNotificationMessageConsumed() {
        _uiState.update { it.copy(testNotificationMessage = null) }
    }

    /**
     * 테스트 알림 전송 다이얼로그를 연다.
     */
    fun onTestNotificationActionClicked() {
        _uiState.update {
            it.copy(
                showTestNotificationDialog = true,
                testNotificationMessage = null
            )
        }
    }

    /**
     * 테스트 알림 전송 다이얼로그를 닫는다.
     */
    fun onTestNotificationDialogDismissed() {
        if (_uiState.value.isSendingTestNotification) return
        _uiState.update { it.copy(showTestNotificationDialog = false) }
    }

    /**
     * 테스트 알림 종류를 선택한다.
     */
    fun onTestNotificationTargetSelected(target: NotificationTestTarget) {
        _uiState.update { it.copy(testNotificationTarget = target) }
    }

    /**
     * 선택된 테스트 알림을 callable function으로 전송한다.
     */
    fun onTestNotificationConfirmed() {
        val current = _uiState.value
        if (current.isSendingTestNotification) return

        val uid = authRepository.getCurrentUserUid()
        if (uid.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    showTestNotificationDialog = false,
                    testNotificationMessage = "로그인한 상태에서만 테스트 알림을 보낼 수 있습니다."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                showTestNotificationDialog = false,
                isSendingTestNotification = true,
                testNotificationMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                firebaseFunctions
                    .getHttpsCallable(SEND_TEST_NOTIFICATION_FUNCTION_NAME)
                    .call(
                        mapOf(
                            TEST_NOTIFICATION_TYPE_KEY to current.testNotificationTarget.type
                        )
                    )
                    .await()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSendingTestNotification = false,
                        testNotificationMessage = when (current.testNotificationTarget) {
                            NotificationTestTarget.SRS -> "학습 테스트 알림을 보냈습니다."
                            NotificationTestTarget.MARKETING -> "마케팅 테스트 알림을 보냈습니다."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSendingTestNotification = false,
                        testNotificationMessage = error.message
                            ?: "테스트 알림 전송에 실패했습니다."
                    )
                }
            }
        }
    }

    /**
     * 주언어 primaryLang 변경: 로컬에 저장한 뒤 서버에 동기화한다.
     */
    fun onPrimaryLanguageChanged(lang: LangCode) {
        viewModelScope.launch {
            _profileState.update { it.copy(isSaving = true, message = null) }
            changePrimaryLangUseCase(lang)
                .onSuccess {
                    syncLearningStateUseCase()
                    _profileState.update { it.copy(isSaving = false) }
                }
                .onFailure { error ->
                    _profileState.update {
                        it.copy(
                            isSaving = false,
                            message = error.message ?: "주언어 설정 저장에 실패했습니다."
                        )
                    }
                }
        }
    }

    /**
     * 프로필 메시지(토스트)를 소비한다.
     */
    fun onProfileMessageConsumed() {
        _profileState.update { it.copy(message = null) }
    }

    private companion object {
        const val SEND_TEST_NOTIFICATION_FUNCTION_NAME = "sendTestNotification"
        const val TEST_NOTIFICATION_TYPE_KEY = "type"
    }
}
