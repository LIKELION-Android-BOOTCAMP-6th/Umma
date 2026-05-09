package com.example.umma.data.repository

import com.example.umma.domain.model.AIEvent
import com.example.umma.domain.model.AIState
import com.example.umma.domain.repository.LiveChatRepository
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.java.LiveModelFutures
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerMessage
import com.google.firebase.ai.type.LiveServerSetupComplete
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Transcription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class LiveChatRepositoryImpl @OptIn(PublicPreviewAPI::class)
@Inject constructor(
    private val liveModel: LiveGenerativeModel
) : LiveChatRepository {

    // 대화 세션
    @OptIn(PublicPreviewAPI::class)
    private var session: LiveSession? = null
    // 코루틴 스코프
    private var scope: CoroutineScope? = null
    // 이벤트 플로우
    private val _events = MutableSharedFlow<AIEvent>()

    // 대화 세션 시작
    @OptIn(PublicPreviewAPI::class)
    override suspend fun startSession(): Result<Unit> {
        return try {
            val newSession = liveModel.connect()
            session = newSession

            scope?.cancel()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            scope?.launch {
                newSession.receive().collect { message ->
                    when (message) {
                        is LiveServerContent -> {
                            message.content?.parts?.forEach { part ->
                                when (part) {
                                    is InlineDataPart -> {
                                        _events.emit(AIEvent.AudioResponse(part.inlineData))
                                    }
                                    is TextPart -> {
                                        _events.emit(AIEvent.TextResponse(part.text))
                                    }
                                    else -> { }
                                }
                            }
                        }

                        is LiveServerSetupComplete -> {
                            _events.emit(AIEvent.StateChanged(AIState.IDLE))
                        }

                        is LiveServerGoAway -> {
                            stopSession()
                        }
                    }
                }

            }
            Result.success(Unit)
        } catch (e: Exception) {
            _events.emit(AIEvent.Error("Connection Failed: ${e.message}"))
            Result.failure(e)
        }
    }

    @OptIn(PublicPreviewAPI::class)
    override suspend fun sendAudioData(audio: ByteArray) {
        session?.sendAudioRealtime(
            InlineData(
                data = audio,
                mimeType = "audio/pcm;rate=16000"
            )
        )
    }

    @OptIn(PublicPreviewAPI::class)
    override suspend fun sendTextData(text: String) {
        session?.sendTextRealtime(text)
    }

    override fun observeAIEvent(): Flow<AIEvent> = _events.asSharedFlow()

    @OptIn(PublicPreviewAPI::class)
    override suspend fun stopSession() {
        scope?.cancel()
        scope = null
        session?.close()
    }
}