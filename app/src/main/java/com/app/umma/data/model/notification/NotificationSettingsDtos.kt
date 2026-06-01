package com.app.umma.data.model.notification

import com.app.umma.domain.model.notification.SrsNotificationSettings
import kotlinx.serialization.Serializable

/**
 * SRS 학습 알림 설정 DTO.
 */
@Serializable
data class SrsNotificationSettingsDto(
    val type: String = "srs_review",
    val enabled: Boolean,
    val preferredNotificationTimeMinutes: Int,
    val timezone: String,
    val nextNotificationAt: Long? = null,
    val updatedAt: Long? = null
)

/**
 * Domain 설정 모델을 DTO로 변환한다.
 */
fun SrsNotificationSettings.toDto(): SrsNotificationSettingsDto {
    return SrsNotificationSettingsDto(
        enabled = enabled,
        preferredNotificationTimeMinutes = preferredNotificationTimeMinutes,
        timezone = timezone,
        nextNotificationAt = nextNotificationAt,
        updatedAt = updatedAt
    )
}

/**
 * DTO를 Domain 설정 모델로 변환한다.
 */
fun SrsNotificationSettingsDto.toDomain(): SrsNotificationSettings {
    return SrsNotificationSettings(
        enabled = enabled,
        preferredNotificationTimeMinutes = preferredNotificationTimeMinutes,
        timezone = timezone,
        nextNotificationAt = nextNotificationAt,
        updatedAt = updatedAt
    )
}
