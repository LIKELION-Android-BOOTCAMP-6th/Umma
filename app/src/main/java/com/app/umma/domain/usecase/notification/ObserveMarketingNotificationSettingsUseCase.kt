package com.app.umma.domain.usecase.notification

import com.app.umma.domain.model.notification.MarketingNotificationSettings
import com.app.umma.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 마케팅 알림 설정 관찰 UseCase.
 */
class ObserveMarketingNotificationSettingsUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    operator fun invoke(): Flow<MarketingNotificationSettings> {
        return repository.observeMarketingNotificationSettings()
    }
}
