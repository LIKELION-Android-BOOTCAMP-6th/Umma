package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 마케팅 알림 ON/OFF 저장 UseCase.
 */
class SetMarketingNotificationEnabledUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        return repository.setMarketingNotificationEnabled(enabled)
    }
}
