package com.app.umma.domain.usecase.notification

import com.app.umma.domain.model.notification.SrsNotificationSettings
import com.app.umma.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * SRS 알림 설정 관찰 UseCase.
 */
class ObserveSrsNotificationSettingsUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    operator fun invoke(): Flow<SrsNotificationSettings> = repository.observeSrsNotificationSettings()
}