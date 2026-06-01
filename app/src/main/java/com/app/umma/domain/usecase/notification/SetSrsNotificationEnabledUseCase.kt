package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * SRS 알림 ON/OFF 저장 UseCase.
 */
class SetSrsNotificationEnabledUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        return repository.setSrsNotificationEnabled(enabled)
    }
}
