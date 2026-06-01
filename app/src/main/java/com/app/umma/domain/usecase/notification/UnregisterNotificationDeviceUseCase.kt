package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 현재 기기 발송 대상 해제 UseCase.
 */
class UnregisterNotificationDeviceUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.unregisterCurrentDevice()
}