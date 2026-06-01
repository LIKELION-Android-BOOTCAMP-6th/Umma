package com.app.umma.domain.model.notification

/**
 * SRS 학습 알림 설정 모델.
 *
 * @property preferredNotificationTimeMinutes 사용자 로컬 기준 자정 이후 분 단위 시각
 * @property nextNotificationAt 서버 스케줄러가 다음 발송 시각을 판정하는 UTC epoch ms
 */
data class SrsNotificationSettings(
    val enabled: Boolean,
    val preferredNotificationTimeMinutes: Int,
    val timezone: String,
    val nextNotificationAt: Long?,
    val updatedAt: Long?
) {
    companion object {
        /**
         * 기본값 오후 6시 Asia/Seoul 타임존
         */
        fun initial(): SrsNotificationSettings = SrsNotificationSettings(
            enabled = false,
            preferredNotificationTimeMinutes = 18 * 60,
            timezone = "Asia/Seoul",
            nextNotificationAt = null,
            updatedAt = null
        )
    }
}
