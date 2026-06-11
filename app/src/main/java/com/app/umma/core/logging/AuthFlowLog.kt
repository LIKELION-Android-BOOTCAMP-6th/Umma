package com.app.umma.core.logging

/**
 * 회원가입/탈퇴 흐름 진단 로그.
 *
 * domain/usecase 에서 android.util.Log 를 직접 import 하면 JVM unit test 에서
 * Android stub 예외가 발생한다. reflection 으로 Android 런타임에서만 Logcat 에 남기고
 * JVM test 에서는 조용히 no-op 처리한다(LearningSignalFlowLog 와 동일 패턴).
 */
object AuthFlowLog {
    private const val TAG = "AuthFlow"

    fun w(message: String, throwable: Throwable) {
        log(methodName = "w", message = message, throwable = throwable)
    }

    private fun log(
        methodName: String,
        message: String,
        throwable: Throwable?
    ) {
        runCatching {
            // android.util.Log 를 import 하지 않아 domain local unit test 의 Stub! 실패를 피한다.
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
