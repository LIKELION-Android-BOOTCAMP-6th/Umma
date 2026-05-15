package com.example.umma.domain.repository

import com.example.umma.domain.model.realtime.AIEvent
import com.example.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // 인자: systemInstruction 동적 주입 session에 따라, 반환값: 생성된 sessionId
    suspend fun startSession(langCode: LangCode, systemInstruction: String): Result<String>

    // 반환 SessionId(Nullable) 없을 시 새 세션 생성
    fun getActiveSessionId(): String?

    suspend fun sendAudioData(audio: ByteArray)

    suspend fun sendTextData(text: String)

    fun observeAIEvent(): Flow<AIEvent>

    suspend fun stopSession()
}