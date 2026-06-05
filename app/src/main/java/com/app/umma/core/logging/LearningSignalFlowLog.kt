package com.app.umma.core.logging

/**
 * LearningState signal pipeline 진단 로그.
 *
 * domain/usecase에서도 같은 태그를 써야 pipeline을 한 번에 추적할 수 있지만,
 * local JVM unit test에서 `android.util.Log`를 직접 호출하면 Android stub 예외가 발생한다.
 * 그래서 reflection으로 Android 런타임에서만 Logcat에 남기고, JVM test에서는 조용히 no-op 처리한다.
 *
 * 로그에는 사용자 원문, 교정문, 설명문을 넣지 않는다.
 * eventId, lang, count, drop reason처럼 pipeline 추적에 필요한 메타데이터만 남긴다.
 */
object LearningSignalFlowLog {
    private const val TAG = "LearningSignalFlow"

    fun d(message: String) {
        log(methodName = "d", message = message, throwable = null)
    }

    fun w(message: String, throwable: Throwable) {
        log(methodName = "w", message = message, throwable = throwable)
    }

    private fun log(
        methodName: String,
        message: String,
        throwable: Throwable?
    ) {
        runCatching {
            // android.util.Log를 import하지 않아 domain local unit test의 Stub! 실패를 피한다.
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
