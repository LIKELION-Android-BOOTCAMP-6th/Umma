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
 * 테스트 알림 전송 대상.
 */
enum class NotificationTestTarget(val type: String) {
    SRS("srs_review"),
    MARKETING("marketing")
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
    val showTestNotificationDialog: Boolean = false,
    val testNotificationTarget: NotificationTestTarget = NotificationTestTarget.SRS,
    val isSendingTestNotification: Boolean = false,
    val testNotificationMessage: String? = null,
    val message: String? = null
) {
    val permissionRequired: Boolean
        get() = permissionRequestTarget != null
}
