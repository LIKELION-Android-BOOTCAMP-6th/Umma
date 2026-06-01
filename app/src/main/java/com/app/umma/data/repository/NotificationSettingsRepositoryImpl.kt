package com.app.umma.data.repository

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.umma.data.model.notification.SrsNotificationSettingsDto
import com.app.umma.data.model.notification.toDomain
import com.app.umma.data.model.notification.toDto
import com.app.umma.domain.model.notification.SrsNotificationSettings
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.NotificationSettingsRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Firestore source of truth + DataStore cache based repository for SRS notification settings.
 */
@Singleton
class NotificationSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    @Named("notificationSettingsDataStore")
    private val dataStore: DataStore<Preferences>
) : NotificationSettingsRepository {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    override fun observeSrsNotificationSettings(): Flow<SrsNotificationSettings> {
        return callbackFlow {
            val uid = authRepository.getCurrentUserUid()
            if (uid.isNullOrBlank()) {
                trySend(readCachedSettings() ?: SrsNotificationSettings.initial())
                close()
                return@callbackFlow
            }

            val documentRef = firestore
                .collection(USERS_COLLECTION)
                .document(uid)
                .collection(NOTIFICATION_SETTINGS_COLLECTION)
                .document(SRS_REVIEW_DOCUMENT_ID)

            val registration = documentRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    launch {
                        trySend(readCachedSettings() ?: SrsNotificationSettings.initial())
                    }
                    return@addSnapshotListener
                }

                val settings = snapshot?.data
                    ?.toSrsNotificationSettingsDto()
                    ?.toDomain()
                    ?: SrsNotificationSettings.initial()
                trySend(settings)
            }

            awaitClose { registration.remove() }
        }.map { settings ->
            cacheSettings(settings)
            settings
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun setSrsNotificationEnabled(enabled: Boolean): Result<Unit> {
        return runCatching {
            val current = currentSettings()
            val nextNotificationAt = if (enabled) {
                computeNextNotificationAt(
                    timezone = current.timezone,
                    preferredTimeMinutes = current.preferredNotificationTimeMinutes,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveSettings(
                current.copy(
                    enabled = enabled,
                    nextNotificationAt = nextNotificationAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun setSrsNotificationTime(totalMinutes: Int): Result<Unit> {
        return runCatching {
            require(totalMinutes in 0 until MINUTES_PER_DAY) {
                "totalMinutes must be between 0 and 1439"
            }

            val current = currentSettings()
            val nextNotificationAt = if (current.enabled) {
                computeNextNotificationAt(
                    timezone = current.timezone,
                    preferredTimeMinutes = totalMinutes,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveSettings(
                current.copy(
                    preferredNotificationTimeMinutes = totalMinutes,
                    nextNotificationAt = nextNotificationAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun refreshTimezone(timezone: String): Result<Unit> {
        return runCatching {
            val current = currentSettings()
            val nextNotificationAt = if (current.enabled) {
                computeNextNotificationAt(
                    timezone = timezone,
                    preferredTimeMinutes = current.preferredNotificationTimeMinutes,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveSettings(
                current.copy(
                    timezone = timezone,
                    nextNotificationAt = nextNotificationAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun registerCurrentDevice(
        token: String,
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid()
                ?: throw IllegalStateException("signed-in user is required")

            val now = System.currentTimeMillis()
            val deviceId = currentDeviceId()

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(NOTIFICATION_DEVICES_COLLECTION)
                .document(deviceId)
                .set(
                    mapOf(
                        "deviceId" to deviceId,
                        "platform" to ANDROID_PLATFORM,
                        "fcmToken" to token,
                        "permissionGranted" to permissionGranted,
                        "timezone" to timezone,
                        "updatedAt" to now,
                        "lastTokenRefreshAt" to now
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    override suspend fun syncCurrentDeviceRegistration(
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid() ?: return@runCatching
            val token = FirebaseMessaging.getInstance().token.await()
            registerCurrentDevice(
                token = token,
                permissionGranted = permissionGranted,
                timezone = timezone
            ).getOrThrow()
        }
    }

    override suspend fun unregisterCurrentDevice(): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid() ?: return@runCatching
            val deviceId = currentDeviceId()

            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(NOTIFICATION_DEVICES_COLLECTION)
                .document(deviceId)
                .set(
                    mapOf(
                        "deviceId" to deviceId,
                        "platform" to ANDROID_PLATFORM,
                        "permissionGranted" to false,
                        "fcmToken" to "",
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    private suspend fun currentSettings(): SrsNotificationSettings {
        readCachedSettings()?.let { return it }

        val uid = authRepository.getCurrentUserUid()
        if (uid.isNullOrBlank()) return SrsNotificationSettings.initial()

        val snapshot = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(NOTIFICATION_SETTINGS_COLLECTION)
            .document(SRS_REVIEW_DOCUMENT_ID)
            .get()
            .await()

        return snapshot.data
            ?.toSrsNotificationSettingsDto()
            ?.toDomain()
            ?: SrsNotificationSettings.initial()
    }

    private suspend fun saveSettings(settings: SrsNotificationSettings) {
        val uid = authRepository.getCurrentUserUid()
            ?: throw IllegalStateException("signed-in user is required")

        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(NOTIFICATION_SETTINGS_COLLECTION)
            .document(SRS_REVIEW_DOCUMENT_ID)
            .set(settings.toDto(), SetOptions.merge())
            .await()

        cacheSettings(settings)
    }

    private suspend fun readCachedSettings(): SrsNotificationSettings? {
        val preferences = dataStore.data.first()
        val raw = preferences[SETTINGS_CACHE_KEY] ?: return null
        return json.decodeFromString<SrsNotificationSettingsDto>(raw).toDomain()
    }

    private suspend fun cacheSettings(settings: SrsNotificationSettings) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_CACHE_KEY] = json.encodeToString(settings.toDto())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun computeNextNotificationAt(
        timezone: String,
        preferredTimeMinutes: Int,
        now: Long
    ): Long {
        val zoneId = ZoneId.of(timezone)
        val current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
        val target = current
            .withHour(preferredTimeMinutes / MINUTES_PER_HOUR)
            .withMinute(preferredTimeMinutes % MINUTES_PER_HOUR)
            .withSecond(0)
            .withNano(0)

        val next = if (target.isAfter(current)) target else target.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    private fun currentDeviceId(): String {
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

    private fun Map<String, Any?>.toSrsNotificationSettingsDto(): SrsNotificationSettingsDto {
        return SrsNotificationSettingsDto(
            type = this["type"] as? String ?: SRS_REVIEW_DOCUMENT_ID,
            enabled = this["enabled"] as? Boolean ?: false,
            preferredNotificationTimeMinutes = (this["preferredNotificationTimeMinutes"] as? Number)?.toInt()
                ?: DEFAULT_NOTIFICATION_TIME_MINUTES,
            timezone = this["timezone"] as? String ?: DEFAULT_TIMEZONE,
            nextNotificationAt = (this["nextNotificationAt"] as? Number)?.toLong(),
            updatedAt = (this["updatedAt"] as? Number)?.toLong()
        )
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val NOTIFICATION_SETTINGS_COLLECTION = "notification_settings"
        const val NOTIFICATION_DEVICES_COLLECTION = "notification_devices"
        const val SRS_REVIEW_DOCUMENT_ID = "srs_review"
        const val ANDROID_PLATFORM = "android"
        const val DEFAULT_TIMEZONE = "Asia/Seoul"
        const val DEFAULT_NOTIFICATION_TIME_MINUTES = 19 * 60
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60
        const val NOTIFICATION_DEVICE_PREFERENCES = "notification_device_preferences"
        const val NOTIFICATION_INSTALLATION_ID_KEY = "notification_installation_id"
        val SETTINGS_CACHE_KEY = stringPreferencesKey("srs_notification_settings_cache")
    }
}
