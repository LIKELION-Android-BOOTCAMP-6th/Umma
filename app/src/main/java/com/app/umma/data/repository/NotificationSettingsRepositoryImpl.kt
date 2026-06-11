package com.app.umma.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.umma.core.util.DeviceIdProvider
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
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class NotificationSettingsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val deviceIdProvider: DeviceIdProvider,
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

            val registration = notificationSettingsCollection(uid)
                .document(SRS_REVIEW_DOCUMENT_ID)
                .addSnapshotListener { snapshot, error ->
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

            val registration = notificationSettingsCollection(uid)
                .document(MARKETING_DOCUMENT_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        launch {
                            trySend(readCachedMarketingSettings() ?: MarketingNotificationSettings.initial())
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
            val nextBucketAt = if (enabled) {
                computeNextSrsNotificationBucketAt(
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
                    nextNotificationAt = nextBucketAt,
                    nextNotificationBucketAt = nextBucketAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun setMarketingNotificationEnabled(enabled: Boolean): Result<Unit> {
        return runCatching {
            val current = currentMarketingSettings()
            val nextBucketAt = if (enabled) {
                computeNextMarketingNotificationBucketAt(
                    timezone = current.timezone,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveMarketingSettings(
                current.copy(
                    enabled = enabled,
                    nextNotificationBucketAt = nextBucketAt,
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

            val normalizedMinutes = normalizePreferredNotificationTimeMinutes(totalMinutes)
            val current = currentSrsSettings()
            val nextBucketAt = if (current.enabled) {
                computeNextSrsNotificationBucketAt(
                    timezone = current.timezone,
                    preferredTimeMinutes = normalizedMinutes,
                    now = System.currentTimeMillis()
                )
            } else {
                null
            }

            saveSrsSettings(
                current.copy(
                    preferredNotificationTimeMinutes = normalizedMinutes,
                    nextNotificationAt = nextBucketAt,
                    nextNotificationBucketAt = nextBucketAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun refreshTimezone(timezone: String): Result<Unit> {
        return runCatching {
            val now = System.currentTimeMillis()

            val currentSrs = currentSrsSettings()
            val nextSrsBucketAt = if (currentSrs.enabled) {
                computeNextSrsNotificationBucketAt(
                    timezone = timezone,
                    preferredTimeMinutes = currentSrs.preferredNotificationTimeMinutes,
                    now = now
                )
            } else {
                null
            }
            saveSrsSettings(
                currentSrs.copy(
                    timezone = timezone,
                    nextNotificationAt = nextSrsBucketAt,
                    nextNotificationBucketAt = nextSrsBucketAt,
                    updatedAt = now
                )
            )

            val currentMarketing = currentMarketingSettings()
            val nextMarketingBucketAt = if (currentMarketing.enabled) {
                computeNextMarketingNotificationBucketAt(
                    timezone = timezone,
                    now = now
                )
            } else {
                null
            }
            saveMarketingSettings(
                currentMarketing.copy(
                    timezone = timezone,
                    nextNotificationBucketAt = nextMarketingBucketAt,
                    updatedAt = now
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
            val deviceId = deviceIdProvider.getDeviceId()

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

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun syncNotificationPermissionState(
        permissionGranted: Boolean,
        timezone: String
    ): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid() ?: return@runCatching

            // 강제 로그아웃 알림은 권한과 무관하게 수신해야 하므로, 권한이 꺼져 있어도
            // FCM 토큰은 항상 등록 상태로 유지한다.
            val token = FirebaseMessaging.getInstance().token.await()
            registerCurrentDevice(
                token = token,
                permissionGranted = permissionGranted,
                timezone = timezone
            ).getOrThrow()

            if (permissionGranted) {
                refreshTimezone(timezone).getOrThrow()
            } else {
                disableAllNotificationSettings(uid, timezone)
            }
        }
    }

    override suspend fun unregisterCurrentDevice(): Result<Unit> {
        return runCatching {
            val uid = authRepository.getCurrentUserUid() ?: return@runCatching
            val deviceId = deviceIdProvider.getDeviceId()

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

    private suspend fun currentMarketingSettings(): MarketingNotificationSettings {
        readCachedMarketingSettings()?.let { return it }

        val uid = authRepository.getCurrentUserUid()
        if (uid.isNullOrBlank()) return MarketingNotificationSettings.initial()

        val snapshot = notificationSettingsCollection(uid)
            .document(MARKETING_DOCUMENT_ID)
            .get()
            .await()

        return snapshot.data
            ?.toMarketingNotificationSettingsDto()
            ?.toDomain()
            ?: MarketingNotificationSettings.initial()
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

    private suspend fun disableAllNotificationSettings(uid: String, timezone: String) {
        val now = System.currentTimeMillis()

        saveSrsSettings(
            currentSrsSettings().copy(
                enabled = false,
                timezone = timezone,
                nextNotificationAt = null,
                nextNotificationBucketAt = null,
                updatedAt = now
            )
        )

        saveMarketingSettings(
            currentMarketingSettings().copy(
                enabled = false,
                timezone = timezone,
                nextNotificationBucketAt = null,
                updatedAt = now
            )
        )
    }

    override suspend fun hasRequestedLaunchNotificationPermission(): Boolean {
        val preferences = dataStore.data.first()
        return preferences[LAUNCH_NOTIFICATION_PERMISSION_REQUESTED_KEY] ?: false
    }

    override suspend fun markLaunchNotificationPermissionRequested() {
        dataStore.edit { preferences ->
            preferences[LAUNCH_NOTIFICATION_PERMISSION_REQUESTED_KEY] = true
        }
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
    private fun computeNextSrsNotificationBucketAt(
        timezone: String,
        preferredTimeMinutes: Int,
        now: Long
    ): Long {
        val normalizedMinutes = normalizePreferredNotificationTimeMinutes(preferredTimeMinutes)
        val zoneId = ZoneId.of(timezone)
        val current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
        val target = current
            .withHour(normalizedMinutes / MINUTES_PER_HOUR)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        val next = if (target.isAfter(current)) target else target.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun computeNextMarketingNotificationBucketAt(
        timezone: String,
        now: Long
    ): Long {
        val zoneId = ZoneId.of(timezone)
        val current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
        val morningTarget = current.withHour(MARKETING_MORNING_HOUR).withMinute(0).withSecond(0).withNano(0)
        val noonTarget = current.withHour(MARKETING_NOON_HOUR).withMinute(0).withSecond(0).withNano(0)

        val next = when {
            morningTarget.isAfter(current) -> morningTarget
            noonTarget.isAfter(current) -> noonTarget
            else -> current.plusDays(1).withHour(MARKETING_MORNING_HOUR).withMinute(0).withSecond(0).withNano(0)
        }
        return next.toInstant().toEpochMilli()
    }

    private fun normalizePreferredNotificationTimeMinutes(totalMinutes: Int): Int {
        val clamped = totalMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        return (clamped / MINUTES_PER_HOUR) * MINUTES_PER_HOUR
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
        val preferredMinutes = normalizePreferredNotificationTimeMinutes(
            (this["preferredNotificationTimeMinutes"] as? Number)?.toInt()
                ?: DEFAULT_NOTIFICATION_TIME_MINUTES
        )
        return SrsNotificationSettingsDto(
            type = this["type"] as? String ?: SRS_REVIEW_DOCUMENT_ID,
            enabled = this["enabled"] as? Boolean ?: false,
            preferredNotificationTimeMinutes = preferredMinutes,
            timezone = this["timezone"] as? String ?: DEFAULT_TIMEZONE,
            nextNotificationAt = (this["nextNotificationAt"] as? Number)?.toLong(),
            nextNotificationBucketAt = (this["nextNotificationBucketAt"] as? Number)?.toLong(),
            updatedAt = (this["updatedAt"] as? Number)?.toLong()
        )
    }

    private fun Map<String, Any?>.toMarketingNotificationSettingsDto(): MarketingNotificationSettingsDto {
        return MarketingNotificationSettingsDto(
            type = this["type"] as? String ?: MARKETING_DOCUMENT_ID,
            enabled = this["enabled"] as? Boolean ?: false,
            timezone = this["timezone"] as? String ?: DEFAULT_TIMEZONE,
            nextNotificationBucketAt = (this["nextNotificationBucketAt"] as? Number)?.toLong(),
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
        const val MARKETING_MORNING_HOUR = 7
        const val MARKETING_NOON_HOUR = 12

        val SRS_SETTINGS_CACHE_KEY = stringPreferencesKey("srs_notification_settings_cache")
        val MARKETING_SETTINGS_CACHE_KEY =
            stringPreferencesKey("marketing_notification_settings_cache")
        val LAUNCH_NOTIFICATION_PERMISSION_REQUESTED_KEY =
            booleanPreferencesKey("launch_notification_permission_requested")
    }
}
