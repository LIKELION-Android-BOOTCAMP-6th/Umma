package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * SRS 알림 시각 저장 UseCase.
 */
class SetSrsNotificationTimeUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(totalMinutes: Int): Result<Unit> {
        return repository.setSrsNotificationTime(totalMinutes)
    }
}