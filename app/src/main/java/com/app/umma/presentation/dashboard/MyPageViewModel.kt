package com.app.umma.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.usecase.notification.ObserveMarketingNotificationSettingsUseCase
import com.app.umma.domain.usecase.notification.ObserveSrsNotificationSettingsUseCase
import com.app.umma.domain.usecase.notification.RefreshNotificationTimezoneUseCase
import com.app.umma.domain.usecase.notification.SetMarketingNotificationEnabledUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationEnabledUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationTimeUseCase
import com.app.umma.domain.usecase.notification.SyncCurrentNotificationDeviceUseCase
import com.app.umma.domain.usecase.user.GetUserNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 마이페이지 알림 설정과 기기 권한 동기화를 담당하는 ViewModel.
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
    private val syncCurrentNotificationDeviceUseCase: SyncCurrentNotificationDeviceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageNotificationUiState())
    val uiState: StateFlow<MyPageNotificationUiState> = _uiState.asStateFlow()

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
    }

    /**
     * 화면 진입 시 현재 타임존과 권한 상태를 서버에 동기화한다.
     */
    fun onScreenStarted(
        permissionGranted: Boolean,
        timezone: String
    ) {
        viewModelScope.launch {
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
}
