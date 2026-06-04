package com.app.umma.data.model.notification

import com.app.umma.domain.model.notification.MarketingNotificationSettings
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
    val nextNotificationBucketAt: Long? = null,
    val updatedAt: Long? = null
)

/**
 * 마케팅 알림 설정 DTO.
 */
@Serializable
data class MarketingNotificationSettingsDto(
    val type: String = "marketing",
    val enabled: Boolean,
    val timezone: String,
    val nextNotificationBucketAt: Long? = null,
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
        nextNotificationBucketAt = nextNotificationBucketAt,
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
        nextNotificationBucketAt = nextNotificationBucketAt,
        updatedAt = updatedAt
    )
}

/**
 * Domain 설정 모델을 DTO로 변환한다.
 */
fun MarketingNotificationSettings.toDto(): MarketingNotificationSettingsDto {
    return MarketingNotificationSettingsDto(
        enabled = enabled,
        timezone = timezone,
        nextNotificationBucketAt = nextNotificationBucketAt,
        updatedAt = updatedAt
    )
}

/**
 * DTO를 Domain 설정 모델로 변환한다.
 */
fun MarketingNotificationSettingsDto.toDomain(): MarketingNotificationSettings {
    return MarketingNotificationSettings(
        enabled = enabled,
        timezone = timezone,
        nextNotificationBucketAt = nextNotificationBucketAt,
        updatedAt = updatedAt
    )
}
