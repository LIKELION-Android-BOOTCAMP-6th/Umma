package com.app.umma.data.repository

import android.content.Context
import com.app.umma.core.util.DeviceIdProvider
import com.app.umma.domain.repository.SessionRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val firebaseFunctions: FirebaseFunctions,
    private val deviceIdProvider: DeviceIdProvider
) : SessionRepository {

    private val preferences by lazy {
        context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
    }

    override fun getLocalSessionId(): String? {
        return preferences.getString(KEY_ACTIVE_SESSION_ID, null)
    }

    override fun saveLocalSessionId(sessionId: String) {
        preferences.edit()
            .putString(KEY_ACTIVE_SESSION_ID, sessionId)
            .apply()
    }

    override fun clearLocalSessionId() {
        preferences.edit()
            .remove(KEY_ACTIVE_SESSION_ID)
            .apply()
    }

    override suspend fun claimSession(): Result<Unit> {
        return try {
            val data = mapOf("deviceId" to deviceIdProvider.getDeviceId())
            val result = firebaseFunctions
                .getHttpsCallable("claimLoginSession")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.data as? Map<String, Any?>
            val sessionId = response?.get("sessionId") as? String
            if (!sessionId.isNullOrBlank()) {
                saveLocalSessionId(sessionId)
            }

            // 새 로그인이 정상적으로 claim되었으므로, 과거에 밀려났던 잔여 안내 플래그는 더 이상 의미가 없다.
            consumePendingForceLogoutNotice()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isCurrentSessionActive(uid: String): Result<Boolean> {
        return try {
            val localSessionId = getLocalSessionId()
            if (localSessionId == null) {
                // 직전 로그인에서 claim이 실패해 로컬이 비어있는 상태 → 오판 금지, 이번에 다시 claim 시도.
                claimSession()
                Result.success(true)
            } else {
                val snapshot = firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .get()
                    .await()

                val activeSessionId = snapshot.get("$ACTIVE_SESSION_FIELD.$SESSION_ID_FIELD") as? String
                // activeSession이 아직 없는 계정(레거시)은 정책 도입 이전 사용자이므로 유효한 것으로 간주한다.
                Result.success(activeSessionId == null || activeSessionId == localSessionId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun markPendingForceLogoutNotice() {
        preferences.edit()
            .putLong(KEY_PENDING_NOTICE_AT, System.currentTimeMillis())
            .apply()
    }

    override fun consumePendingForceLogoutNotice(): Boolean {
        val markedAt = preferences.getLong(KEY_PENDING_NOTICE_AT, 0L)
        if (markedAt == 0L) return false

        // 소비 즉시 흔적 삭제 — 계정 단위로 영속되는 "밀려남" 기록을 남기지 않는다.
        preferences.edit()
            .remove(KEY_PENDING_NOTICE_AT)
            .apply()
        return System.currentTimeMillis() - markedAt <= PENDING_NOTICE_TTL_MS
    }

    private companion object {
        const val SESSION_PREFERENCES = "auth_session_preferences"
        const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        const val KEY_PENDING_NOTICE_AT = "pending_force_logout_notice_at"
        const val PENDING_NOTICE_TTL_MS = 5 * 60 * 1000L
        const val USERS_COLLECTION = "users"
        const val ACTIVE_SESSION_FIELD = "activeSession"
        const val SESSION_ID_FIELD = "sessionId"
    }
}
