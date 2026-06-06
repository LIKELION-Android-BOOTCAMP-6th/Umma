package com.app.umma.core.logging

/**
 * AI Chat prompt/ability 진단 로그.
 *
 * domain/usecase에서도 `AiChatPromptTrace` 태그로 같은 흐름을 볼 수 있게 reflection으로 Android Log를 호출한다.
 * JVM unit test에서는 Android stub 예외가 나지 않도록 no-op 처리한다.
 */
object ChatPromptTraceLog {
    private const val TAG = "AiChatPromptTrace"

    fun d(message: String) {
        log(methodName = "d", message = message, throwable = null)
    }

    fun i(message: String) {
        log(methodName = "i", message = message, throwable = null)
    }

    fun w(message: String, throwable: Throwable? = null) {
        log(methodName = "w", message = message, throwable = throwable)
    }

    private fun log(
        methodName: String,
        message: String,
        throwable: Throwable?
    ) {
        runCatching {
            // android.util.Log를 직접 import하지 않아 domain local unit test의 Stub! 실패를 피한다.
            val logClass = Class.forName("android.util.Log")
            if (throwable == null) {
                logClass
                    .getMethod(methodName, String::class.java, String::class.java)
                    .invoke(null, TAG, message)
            } else {
                logClass
                    .getMethod(methodName, String::class.java, String::class.java, Throwable::class.java)
                    .invoke(null, TAG, message, throwable)
            }
        }
    }
}
