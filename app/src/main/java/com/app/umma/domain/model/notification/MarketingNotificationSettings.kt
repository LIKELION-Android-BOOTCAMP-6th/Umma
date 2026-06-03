package com.app.umma.domain.model.notification

/**
 * 마케팅 알림 설정 모델.
 */
data class MarketingNotificationSettings(
    val enabled: Boolean,
    val updatedAt: Long?
) {
    companion object {
        /**
         * 기본값은 OFF 상태다.
         */
        fun initial(): MarketingNotificationSettings = MarketingNotificationSettings(
            enabled = false,
            updatedAt = null
        )
    }
}
