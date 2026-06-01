package com.app.umma.presentation.dashboard

import com.app.umma.domain.model.notification.SrsNotificationSettings

/**
 * 마이페이지 알림 설정 UI 상태.
 */
data class MyPageNotificationUiState(
    val settings: SrsNotificationSettings = SrsNotificationSettings.initial(),
    val isSaving: Boolean = false, // 저장 중 여부
    val showTimePicker: Boolean = false,
    val permissionRequired: Boolean = false,
    val message: String? = null
)
