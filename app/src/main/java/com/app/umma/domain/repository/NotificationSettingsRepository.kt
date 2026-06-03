package com.app.umma.domain.repository

import com.app.umma.domain.model.notification.MarketingNotificationSettings
import com.app.umma.domain.model.notification.SrsNotificationSettings
import kotlinx.coroutines.flow.Flow

/**
 * 알림 설정과 현재 기기 등록 상태를 관리하는 저장소 계약.
 */
interface NotificationSettingsRepository {
    /**
     * 현재 사용자의 SRS 알림 설정을 관찰한다.
     */
    fun observeSrsNotificationSettings(): Flow<SrsNotificationSettings>

    /**
     * 현재 사용자의 마케팅 알림 설정을 관찰한다.
     */
    fun observeMarketingNotificationSettings(): Flow<MarketingNotificationSettings>

    /**
     * SRS 알림 사용 여부를 저장한다.
     */
    suspend fun setSrsNotificationEnabled(enabled: Boolean): Result<Unit>

    /**
     * 마케팅 알림 사용 여부를 저장한다.
     */
    suspend fun setMarketingNotificationEnabled(enabled: Boolean): Result<Unit>

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
     * 현재 기기의 알림 권한과 토큰 상태를 서버에 동기화한다.
     *
     * 권한이 없으면 학습/마케팅 설정을 모두 OFF로 맞춘다.
     */
    suspend fun syncNotificationPermissionState(
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit>

    /**
     * 현재 기기를 발송 대상에서 제외한다.
     */
    suspend fun unregisterCurrentDevice(): Result<Unit>
}
