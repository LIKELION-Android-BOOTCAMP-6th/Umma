package com.app.umma.presentation.dashboard

import com.app.umma.domain.model.notification.MarketingNotificationSettings
import com.app.umma.domain.model.notification.SrsNotificationSettings

/**
 * 권한 요청 후 다시 켜야 하는 알림 종류.
 */
enum class NotificationPermissionRequestTarget {
    SRS,
    MARKETING
}

/**
 * 마이페이지 알림 설정 UI 상태.
 */
data class MyPageNotificationUiState(
    val srsSettings: SrsNotificationSettings = SrsNotificationSettings.initial(),
    val marketingSettings: MarketingNotificationSettings = MarketingNotificationSettings.initial(),
    val nickname: String = "",
    val isSaving: Boolean = false,
    val showTimePicker: Boolean = false,
    val permissionRequestTarget: NotificationPermissionRequestTarget? = null,
    val message: String? = null
) {
    val permissionRequired: Boolean
        get() = permissionRequestTarget != null
}
