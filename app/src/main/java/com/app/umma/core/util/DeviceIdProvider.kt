package com.app.umma.core.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기 단위로 고정된 식별자를 제공한다.
 * 알림 기기 등록과 세션 claim에서 동일한 deviceId를 공유한다.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getDeviceId(): String {
        val preferences = context.getSharedPreferences(
            NOTIFICATION_DEVICE_PREFERENCES,
            Context.MODE_PRIVATE
        )
        val existing = preferences.getString(NOTIFICATION_INSTALLATION_ID_KEY, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        preferences.edit()
            .putString(NOTIFICATION_INSTALLATION_ID_KEY, generated)
            .apply()
        return generated
    }

    private companion object {
        const val NOTIFICATION_DEVICE_PREFERENCES = "notification_device_preferences"
        const val NOTIFICATION_INSTALLATION_ID_KEY = "notification_installation_id"
    }
}
