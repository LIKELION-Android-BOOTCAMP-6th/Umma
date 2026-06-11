package com.app.umma.domain.usecase.notification

import com.app.umma.domain.repository.NotificationSettingsRepository
import javax.inject.Inject

/**
 * 앱 최초 실행 시 알림 권한을 요청했음을 기록하는 UseCase.
 */
class MarkLaunchNotificationPermissionRequestedUseCase @Inject constructor(
    private val repository: NotificationSettingsRepository
) {
    suspend operator fun invoke() {
        repository.markLaunchNotificationPermissionRequested()
    }
}
