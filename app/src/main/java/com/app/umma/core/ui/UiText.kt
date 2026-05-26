package com.app.umma.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * UiText: ViewModel이나
 * 도메인 계층에서 Android Context 의존성 없이 문자열 리소스(strings.xml)나
 * 동적 문자열을 안전하게 처리하기 위해 사용하는 sealed class(interface) 패턴입니다.
 *
 * 활용 예시: ViewModel에서
 *
 * ```kotlin
 *
 * class MyViewModel : ViewModel() {
 *
 *     private val _errorMessage = MutableStateFlow<UiText?>(null)
 *     val errorMessage = _errorMessage.asStateFlow()
 *
 *     fun fetchData() {
 *         // 상황에 따른 처리
 *         if (error) {
 *             _errorMessage.value = UiText.StringResource(R.string.error_network)
 *         } else {
 *             _errorMessage.value = UiText.DynamicString("Something went wrong")
 *         }
 *     }
 * }
 *```
 * UI에서의 활용
 * ```kotlin
 * @Composable
 * fun MyScreen(viewModel: MyViewModel) {
 *     val errorText by viewModel.errorMessage.collectAsState()
 *
 *     errorText?.let {
 *         Text(text = it.asString()) // UiText를 실제 String으로 변환
 *     }
 * }
 *
 * ```
 * */
sealed interface UiText {
    fun asString(context: Context): String

    // 동적 문자열
    @JvmInline
    value class Dynamic(
        private val text: String,
    ) : UiText {
        fun asString() = text
        override fun asString(context: Context): String = text
    }

    // 리소스 문자열 -> res/values/strings.xml
    class Resource(
        @field:StringRes private val resId: Int,
        private vararg val args: Any
    ) : UiText {
        @Composable
        fun asString(): String = stringResource(resId, *args)

        override fun asString(context: Context): String = context.getString(resId, *args)

        override fun hashCode(): Int {
            var hashCode = resId.hashCode() * 31
            args.forEach { hashCode = hashCode * 31 + it.hashCode() }
            return hashCode
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Resource) return false
            return resId == other.resId && args.contentEquals(other.args)
        }

        companion object {
            val Empty = Dynamic("")
        }
    }

}

fun String.toUiText(): UiText = UiText.Dynamic(this)

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Dynamic -> asString()
    is UiText.Resource -> asString()
}