package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 앱 최초 실행 시 알림 권한을 이미 요청했는지 확인하는 UseCase.
 */
class HasRequestedLaunchNotificationPermissionUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke(): Boolean {
        return repository.hasRequestedLaunchNotificationPermission()
    }
}
