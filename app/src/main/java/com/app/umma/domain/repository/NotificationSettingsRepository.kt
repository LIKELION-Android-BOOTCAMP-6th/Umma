package com.app.umma.domain.repository

import com.app.umma.domain.model.notification.SrsNotificationSettings
import kotlinx.coroutines.flow.Flow

/**
 * 학습 알림 설정과 현재 기기 푸시 등록을 다루는 저장소 계약.
 */
interface NotificationSettingsRepository {
    /**
     * 현재 사용자의 SRS 알림 설정을 관찰한다.
     */
    fun observeSrsNotificationSettings(): Flow<SrsNotificationSettings>

    /**
     * SRS 알림 사용 여부를 저장한다.
     */
    suspend fun setSrsNotificationEnabled(enabled: Boolean): Result<Unit>

    /**
     * SRS 알림 시각을 저장한다.
     */
    suspend fun setSrsNotificationTime(totalMinutes: Int): Result<Unit>

    /**
     * 현재 기기의 타임존을 반영한다.
     */
    suspend fun refreshTimezone(timezone: String): Result<Unit>

    /**
     * 현재 기기의 FCM 토큰과 권한 상태를 등록한다.
     */
    suspend fun registerCurrentDevice(
        token: String,
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit>

    /**
     * 현재 기기의 FCM 토큰을 직접 조회해 등록 상태를 동기화한다.
     */
    suspend fun syncCurrentDeviceRegistration(
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit>

    /**
     * 현재 기기를 발송 대상에서 제외한다.
     */
    suspend fun unregisterCurrentDevice(): Result<Unit>
}
