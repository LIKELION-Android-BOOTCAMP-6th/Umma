package com.app.umma.data.source.remote

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * 직접 HTTPS로 호출하는 서버 endpoint에 붙일 Firebase App Check token을 제공합니다.
 *
 * Firestore처럼 Firebase SDK가 직접 처리하는 요청은 SDK가 App Check token을 자동으로 붙일 수 있지만,
 * OkHttp로 호출하는 Cloud Functions `onRequest` endpoint는 Android 코드가 header를 직접 추가해야 합니다.
 */
@Singleton
class FirebaseAppCheckTokenProvider @Inject constructor(
    private val firebaseAppCheck: FirebaseAppCheck
) {
    /**
     * App Check token을 가져오되, 현재 단계에서는 실패해도 호출 자체를 막지 않습니다.
     *
     * enforcement를 아직 켜지 않은 상태에서 token 문제로 Chat/usage 흐름을 중단하면
     * 개발·검증이 어려워지므로, 우선 monitoring 가능한 header를 붙이고 실패는 warning으로 남깁니다.
     */
    suspend fun fetchOptionalToken(requestName: String): String? {
        return runCatching {
            // forceRefresh=false는 SDK가 보유한 유효 token을 재사용하게 하여 매 요청 비용을 줄입니다.
            firebaseAppCheck.getAppCheckToken(false).await().token
        }.mapCatching { token ->
            // 빈 문자열은 서버가 검증할 수 없는 값이므로 header를 생략하는 null로 정규화합니다.
            token.takeIf { it.isNotBlank() }
        }.onFailure { error ->
            // enforcement 전에는 흐름을 깨지 않고, 어떤 endpoint에서 token 획득이 실패했는지만 남깁니다.
            Log.w(
                TAG,
                "App Check token unavailable for $requestName; sending request without App Check header.",
                error
            )
        }.getOrNull()
    }

    companion object {
        // Cloud Functions onRequest에서 Firebase App Check token을 검증할 때 사용하는 표준 header입니다.
        const val HEADER_NAME: String = "X-Firebase-AppCheck"

        private const val TAG = "FirebaseAppCheck"
    }
}
