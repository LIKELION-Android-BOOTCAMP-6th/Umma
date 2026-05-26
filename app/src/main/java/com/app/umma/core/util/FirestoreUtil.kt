package com.app.umma.core.util

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

suspend fun <T> safeFirestoreCall(
    timeoutMillis: Long = 10_000L,
    block: suspend () -> T
): Result<T> {
    return try {
        val result = withTimeout(timeoutMillis) {
            block()
        }
        Result.success(result)
    } catch (e: TimeoutCancellationException) {
        Result.failure(Exception("네트워크 요청 시간이 초과되었습니다.", e))
    } catch (e: Exception) {
        Result.failure(e)
    }
}