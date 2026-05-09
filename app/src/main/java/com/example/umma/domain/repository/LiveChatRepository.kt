package com.example.umma.domain.repository

import com.example.umma.domain.model.AIEvent
import kotlinx.coroutines.flow.Flow

interface LiveChatRepository {
    suspend fun startSession(): Result<Unit>

    suspend fun sendAudioData(audio: ByteArray)

    suspend fun sendTextData(text: String)

    fun observeAIEvent(): Flow<AIEvent>

    suspend fun stopSession()
}