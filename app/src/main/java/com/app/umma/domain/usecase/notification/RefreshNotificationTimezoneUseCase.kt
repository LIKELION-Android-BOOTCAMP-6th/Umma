package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 현재 기기 타임존 갱신 UseCase.
 */
class RefreshNotificationTimezoneUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(timezone: String): Result<Unit> {
        return repository.refreshTimezone(timezone)
    }
}