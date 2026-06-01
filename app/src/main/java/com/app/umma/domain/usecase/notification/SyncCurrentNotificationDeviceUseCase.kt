package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 현재 디바이스 토큰과 알림 권한 설정 상태를 서버와 동기화하는 UseCase.
 */
class SyncCurrentNotificationDeviceUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit> {
        return repository.syncCurrentDeviceRegistration(
            permissionGranted = permissionGranted,
            timezone = timezone
        )
    }
}
