package com.app.umma.watchbridge

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.usecase.chat.RetryConnectionResult
import kotlinx.coroutines.flow.Flow

interface PhoneChatSessionRuntime {
    suspend fun startSession(): Result<String>
    suspend fun retryConnection(): RetryConnectionResult
    fun observeAIEvents(): Flow<AIEvent>
    suspend fun sendAudioData(audio: ByteArray)
    fun endUserTurn(durationMs: Long?)
    fun cancelPendingUserTurn()
    suspend fun stopSession(clearAppSession: Boolean)
    fun getActiveSessionId(): String?
    fun getCurrentSessionLang(): LangCode?
}
