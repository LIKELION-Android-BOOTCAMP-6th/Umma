package com.app.umma.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.domain.usecase.notification.ObserveSrsNotificationSettingsUseCase
import com.app.umma.domain.usecase.notification.RefreshNotificationTimezoneUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationEnabledUseCase
import com.app.umma.domain.usecase.notification.SetSrsNotificationTimeUseCase
import com.app.umma.domain.usecase.notification.SyncCurrentNotificationDeviceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 마이페이지 알림 설정과 FCM 기기 동기화를 담당하는 ViewModel.
 */
@HiltViewModel
class MyPageViewModel @Inject constructor(
    observeSrsNotificationSettingsUseCase: ObserveSrsNotificationSettingsUseCase,
    private val setSrsNotificationEnabledUseCase: SetSrsNotificationEnabledUseCase,
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
                        settings = settings,
                        isSaving = false
                    )
                }
            }
        }
    }

    /**
     * 화면 진입 시 현재 타임존과 기기 등록 상태를 동기화한다.
     */
    fun onScreenStarted(
        permissionGranted: Boolean,
        timezone: String
    ) {
        viewModelScope.launch {
            refreshNotificationTimezoneUseCase(timezone)
            syncCurrentNotificationDeviceUseCase(
                permissionGranted = permissionGranted,
                timezone = timezone
            )
        }
    }

    /**
     * 알림 토글 변경 이벤트를 처리한다.
     */
    fun onNotificationToggleChanged(
        enabled: Boolean,
        permissionGranted: Boolean
    ) {
        if (enabled && !permissionGranted) {
            _uiState.update { it.copy(permissionRequired = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            setSrsNotificationEnabledUseCase(enabled)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = error.message ?: "알림 설정 저장에 실패했습니다."
                        )
                    }
                }
        }
    }

    /**
     * 권한 허용 후 알림 토글 ON을 완료한다.
     */
    fun onNotificationPermissionGranted(timezone: String) {
        _uiState.update { it.copy(permissionRequired = false) }
        onScreenStarted(permissionGranted = true, timezone = timezone)
        onNotificationToggleChanged(enabled = true, permissionGranted = true)
    }

    /**
     * 권한 거부 시 메시지를 노출하고 현재 기기 상태를 false로 동기화한다.
     */
    fun onNotificationPermissionDenied(timezone: String) {
        _uiState.update {
            it.copy(
                permissionRequired = false,
                message = "알림 권한이 없어 학습 알림을 켤 수 없습니다."
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
     * 시간 선택 결과를 저장한다.
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
                            message = error.message ?: "알림 시간 저장에 실패했습니다."
                        )
                    }
                }
        }
    }

    /**
     * 노출 메시지를 소비한다.
     */
    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }
}
