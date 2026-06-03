package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 현재 기기의 알림 권한과 FCM 등록 상태를 서버에 동기화하는 UseCase.
 */
class SyncCurrentNotificationDeviceUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit> {
        return repository.syncNotificationPermissionState(
            permissionGranted = permissionGranted,
            timezone = timezone
        )
    }
}
