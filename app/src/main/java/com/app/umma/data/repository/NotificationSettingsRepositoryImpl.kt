package com.app.umma.data.repository

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.umma.data.model.notification.MarketingNotificationSettingsDto
import com.app.umma.data.model.notification.SrsNotificationSettingsDto
import com.app.umma.data.model.notification.toDomain
import com.app.umma.data.model.notification.toDto
import com.app.umma.domain.model.notification.MarketingNotificationSettings
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
 * Firestore 원본 + DataStore 캐시 기반 알림 설정 저장소.
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
                trySend(readCachedSrsSettings() ?: SrsNotificationSettings.initial())
                close()
                return@callbackFlow
            }

            val documentRef = notificationSettingsCollection(uid).document(SRS_REVIEW_DOCUMENT_ID)
            val registration = documentRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    launch {
                        trySend(readCachedSrsSettings() ?: SrsNotificationSettings.initial())
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
            cacheSrsSettings(settings)
            settings
        }
    }

    override fun observeMarketingNotificationSettings(): Flow<MarketingNotificationSettings> {
        return callbackFlow {
            val uid = authRepository.getCurrentUserUid()
            if (uid.isNullOrBlank()) {
                trySend(readCachedMarketingSettings() ?: MarketingNotificationSettings.initial())
                close()
                return@callbackFlow
            }

            val documentRef = notificationSettingsCollection(uid).document(MARKETING_DOCUMENT_ID)
            val registration = documentRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    launch {
                        trySend(
                            readCachedMarketingSettings() ?: MarketingNotificationSettings.initial()
                        )
                    }
                    return@addSnapshotListener
                }

                val settings = snapshot?.data
                    ?.toMarketingNotificationSettingsDto()
                    ?.toDomain()
                    ?: MarketingNotificationSettings.initial()
                trySend(settings)
            }

            awaitClose { registration.remove() }
        }.map { settings ->
            cacheMarketingSettings(settings)
            settings
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun setSrsNotificationEnabled(enabled: Boolean): Result<Unit> {
        return runCatching {
            val current = currentSrsSettings()
            val nextNotificationAt = if (enabled) {
                computeNextNotificationAt(
                    timezone = current.timezone,
                    preferredTimeMinutes = current.preferredNotificationTimeMinutes,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveSrsSettings(
                current.copy(
                    enabled = enabled,
                    nextNotificationAt = nextNotificationAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun setMarketingNotificationEnabled(enabled: Boolean): Result<Unit> {
        return runCatching {
            saveMarketingSettings(
                MarketingNotificationSettings(
                    enabled = enabled,
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

            val current = currentSrsSettings()
            val nextNotificationAt = if (current.enabled) {
                computeNextNotificationAt(
                    timezone = current.timezone,
                    preferredTimeMinutes = totalMinutes,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveSrsSettings(
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
            val current = currentSrsSettings()
            val nextNotificationAt = if (current.enabled) {
                computeNextNotificationAt(
                    timezone = timezone,
                    preferredTimeMinutes = current.preferredNotificationTimeMinutes,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveSrsSettings(
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

            notificationDevicesCollection(uid)
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

    override suspend fun syncNotificationPermissionState(
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid() ?: return@runCatching

            if (permissionGranted) {
                val token = FirebaseMessaging.getInstance().token.await()
                registerCurrentDevice(
                    token = token,
                    permissionGranted = true,
                    timezone = timezone
                ).getOrThrow()
                return@runCatching
            }

            unregisterCurrentDevice().getOrThrow()
            disableAllNotificationSettings(uid)
        }
    }

    override suspend fun unregisterCurrentDevice(): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid() ?: return@runCatching
            val deviceId = currentDeviceId()

            notificationDevicesCollection(uid)
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

    private suspend fun currentSrsSettings(): SrsNotificationSettings {
        readCachedSrsSettings()?.let { return it }

        val uid = authRepository.getCurrentUserUid()
        if (uid.isNullOrBlank()) return SrsNotificationSettings.initial()

        val snapshot = notificationSettingsCollection(uid)
            .document(SRS_REVIEW_DOCUMENT_ID)
            .get()
            .await()

        return snapshot.data
            ?.toSrsNotificationSettingsDto()
            ?.toDomain()
            ?: SrsNotificationSettings.initial()
    }

    private suspend fun saveSrsSettings(settings: SrsNotificationSettings) {
        val uid = authRepository.getCurrentUserUid()
            ?: throw IllegalStateException("signed-in user is required")

        notificationSettingsCollection(uid)
            .document(SRS_REVIEW_DOCUMENT_ID)
            .set(settings.toDto(), SetOptions.merge())
            .await()

        cacheSrsSettings(settings)
    }

    private suspend fun saveMarketingSettings(settings: MarketingNotificationSettings) {
        val uid = authRepository.getCurrentUserUid()
            ?: throw IllegalStateException("signed-in user is required")

        notificationSettingsCollection(uid)
            .document(MARKETING_DOCUMENT_ID)
            .set(settings.toDto(), SetOptions.merge())
            .await()

        cacheMarketingSettings(settings)
    }

    private suspend fun disableAllNotificationSettings(uid: String) {
        val now = System.currentTimeMillis()

        val srsSettings = currentSrsSettings().copy(
            enabled = false,
            nextNotificationAt = null,
            updatedAt = now
        )
        saveSrsSettings(srsSettings)

        val marketingSettings = MarketingNotificationSettings(
            enabled = false,
            updatedAt = now
        )
        notificationSettingsCollection(uid)
            .document(MARKETING_DOCUMENT_ID)
            .set(marketingSettings.toDto(), SetOptions.merge())
            .await()
        cacheMarketingSettings(marketingSettings)
    }

    private suspend fun readCachedSrsSettings(): SrsNotificationSettings? {
        val preferences = dataStore.data.first()
        val raw = preferences[SRS_SETTINGS_CACHE_KEY] ?: return null
        return json.decodeFromString<SrsNotificationSettingsDto>(raw).toDomain()
    }

    private suspend fun readCachedMarketingSettings(): MarketingNotificationSettings? {
        val preferences = dataStore.data.first()
        val raw = preferences[MARKETING_SETTINGS_CACHE_KEY] ?: return null
        return json.decodeFromString<MarketingNotificationSettingsDto>(raw).toDomain()
    }

    private suspend fun cacheSrsSettings(settings: SrsNotificationSettings) {
        dataStore.edit { preferences ->
            preferences[SRS_SETTINGS_CACHE_KEY] = json.encodeToString(settings.toDto())
        }
    }

    private suspend fun cacheMarketingSettings(settings: MarketingNotificationSettings) {
        dataStore.edit { preferences ->
            preferences[MARKETING_SETTINGS_CACHE_KEY] = json.encodeToString(settings.toDto())
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

    private fun notificationSettingsCollection(uid: String) = firestore
        .collection(USERS_COLLECTION)
        .document(uid)
        .collection(NOTIFICATION_SETTINGS_COLLECTION)

    private fun notificationDevicesCollection(uid: String) = firestore
        .collection(USERS_COLLECTION)
        .document(uid)
        .collection(NOTIFICATION_DEVICES_COLLECTION)

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

    private fun Map<String, Any?>.toMarketingNotificationSettingsDto(): MarketingNotificationSettingsDto {
        return MarketingNotificationSettingsDto(
            type = this["type"] as? String ?: MARKETING_DOCUMENT_ID,
            enabled = this["enabled"] as? Boolean ?: false,
            updatedAt = (this["updatedAt"] as? Number)?.toLong()
        )
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val NOTIFICATION_SETTINGS_COLLECTION = "notification_settings"
        const val NOTIFICATION_DEVICES_COLLECTION = "notification_devices"
        const val SRS_REVIEW_DOCUMENT_ID = "srs_review"
        const val MARKETING_DOCUMENT_ID = "marketing"
        const val ANDROID_PLATFORM = "android"
        const val DEFAULT_TIMEZONE = "Asia/Seoul"
        const val DEFAULT_NOTIFICATION_TIME_MINUTES = 18 * 60
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60
        const val NOTIFICATION_DEVICE_PREFERENCES = "notification_device_preferences"
        const val NOTIFICATION_INSTALLATION_ID_KEY = "notification_installation_id"
        val SRS_SETTINGS_CACHE_KEY = stringPreferencesKey("srs_notification_settings_cache")
        val MARKETING_SETTINGS_CACHE_KEY =
            stringPreferencesKey("marketing_notification_settings_cache")
    }
}
