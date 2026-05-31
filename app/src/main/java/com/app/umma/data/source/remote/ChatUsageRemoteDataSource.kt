package com.app.umma.data.source.remote

import com.app.umma.BuildConfig
import com.app.umma.domain.model.realtime.ChatTokenUsage
import com.app.umma.domain.model.realtime.ChatUsageSessionAggregate
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * AI Chat usage aggregate의 remote sync 경계입니다.
 *
 * 개별 Realtime usage 이벤트는 local DB에 원본으로 남기고, remote에는 세션 단위 합산값만 올립니다.
 * 실제 Firestore write와 월별 합산은 Cloud Function이 담당하고, 이 data source는 인증된 요청만 보냅니다.
 */
interface ChatUsageRemoteDataSource {
    suspend fun syncSessionAggregate(aggregate: ChatUsageSessionAggregate): Result<Unit>
}

@Singleton
class CloudFunctionChatUsageRemoteDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ChatUsageRemoteDataSource {

    // Usage sync는 세션 종료 시점의 짧은 HTTPS 요청이다.
    // Realtime transport의 WebSocket client와 책임이 달라 전용 client를 둔다.
    private val client = OkHttpClient()

    override suspend fun syncSessionAggregate(
        aggregate: ChatUsageSessionAggregate
    ): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("signed-in user is required"))

        // Android client가 넘긴 userId와 현재 FirebaseAuth uid가 다르면 원격 저장을 막는다.
        // 사용량은 과금/플랜 판단의 근거가 될 수 있으므로 사용자 경계를 보수적으로 지킨다.
        if (uid != aggregate.userId) {
            return Result.failure(
                IllegalStateException("usage userId does not match signed-in user")
            )
        }

        val syncUrl = BuildConfig.OPENAI_USAGE_SYNC_URL
        if (syncUrl.isBlank()) {
            return Result.failure(
                IllegalStateException("OPENAI_USAGE_SYNC_URL is required for chat usage sync")
            )
        }

        return runCatching {
            val idToken = firebaseAuth.currentUser
                ?.getIdToken(false)
                ?.await()
                ?.token
                ?: error("Firebase ID token is required")

            val requestBody = aggregate
                .toFunctionPayload()
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(syncUrl)
                // Cloud Function은 Authorization header의 Firebase ID token으로 사용자 경계를 검증한다.
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body.string()
                        error("chat usage sync failed: code=${response.code}, body=$errorBody")
                    }
                }
            }
        }
    }
}

/**
 * Domain aggregate를 Cloud Function 요청 payload로 변환합니다.
 *
 * Android는 Firestore 문서를 직접 쓰지 않고, 서버가 검증할 수 있는 primitive JSON만 보냅니다.
 * 월별 합산과 session 문서 저장 책임은 `submitChatUsageSession` Cloud Function에 있습니다.
 */
private fun ChatUsageSessionAggregate.toFunctionPayload() = buildJsonObject {
    put("id", id)
    put("userId", userId)
    put("sessionId", sessionId)
    put("language", language.code)
    put("model", model)
    put("startedAt", startedAt)
    put("endedAt", endedAt)
    put("recordCount", recordCount)
    put("responseCount", responseCount)
    put("transcriptionCount", transcriptionCount)
    put("responseUsage", responseUsage.toJsonObject())
    put("transcriptionUsage", transcriptionUsage.toJsonObject())
    if (pricingVersion == null) {
        put("pricingVersion", JsonNull)
    } else {
        put("pricingVersion", pricingVersion)
    }
}

/**
 * token breakdown을 JSON으로 보존합니다.
 *
 * Cloud Function은 null을 0으로 정규화한 뒤 월별 합산에 반영합니다.
 * local DB에는 여전히 null이 남아 "미제공"과 "0"을 구분할 수 있습니다.
 */
private fun ChatTokenUsage.toJsonObject() = buildJsonObject {
    putNullableLong("totalTokens", totalTokens)
    putNullableLong("inputTokens", inputTokens)
    putNullableLong("outputTokens", outputTokens)
    putNullableLong("inputTextTokens", inputTextTokens)
    putNullableLong("inputAudioTokens", inputAudioTokens)
    putNullableLong("inputCachedTokens", inputCachedTokens)
    putNullableLong("outputTextTokens", outputTextTokens)
    putNullableLong("outputAudioTokens", outputAudioTokens)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(
    key: String,
    value: Long?
) {
    if (value == null) {
        put(key, JsonNull)
    } else {
        put(key, value)
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
