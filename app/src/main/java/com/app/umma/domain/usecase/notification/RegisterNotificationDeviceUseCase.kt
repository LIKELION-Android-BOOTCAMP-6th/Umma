package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 현재 기기 FCM 토큰 등록 UseCase.
 */
class RegisterNotificationDeviceUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(
        token: String,
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit> {
        return repository.registerCurrentDevice(
            token = token,
            permissionGranted = permissionGranted,
            timezone = timezone
        )
    }
}