package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.LangCode


/**
 * 수동 재연결 결과 타입
 *
 * - Reconnected: 같은 앱 세션으로 재연결 성공
 * - RequireNewSession: 같은 앱 세션으로 복구 불가능 -> 새 세션 시작 필요
 * - Failed: 같은 앱 세션 복구는 가능하였지만 실제 재연결 실패
 * */
sealed interface RetryConnectionResult {
    data class Reconnected(val sessionId: String) : RetryConnectionResult
    data class RequireNewSession(
        val reason: NewSessionReason,
        val targetLang: LangCode? = null
    ) : RetryConnectionResult
    data class Failed(val message: String) : RetryConnectionResult
}

enum class NewSessionReason {
    LANG_CHANGED,
    NO_ACTIVE_SESSION,
    RESTORE_UNAVAILABLE
}
