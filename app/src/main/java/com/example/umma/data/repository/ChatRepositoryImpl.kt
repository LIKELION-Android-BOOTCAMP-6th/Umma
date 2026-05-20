package com.example.umma.data.repository

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.model.realtime.AIEvent
import com.example.umma.domain.model.realtime.AIState
import com.example.umma.domain.repository.ChatRepository
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerMessage
import com.google.firebase.ai.type.LiveServerSetupComplete
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemini Live API 기반 실시간 음성 대화 Repository 구현체입니다.
 */
@OptIn(PublicPreviewAPI::class)
class ChatRepositoryImpl @Inject constructor(
    private val firebaseAI: FirebaseAI
) : ChatRepository {

    /**
     * 세션 동시 접근을 직렬화하기 위한 mutex 입니다.
     */
    private val sessionMutex = Mutex()

    /**
     * 현재 활성 LiveSession 입니다.
     */
    @OptIn(PublicPreviewAPI::class)
    private var session: LiveSession? = null

    /**
     * 현재 세션의 학습 언어입니다.
     */
    private var currentLang: LangCode? = null

    /**
     * 현재 활성 앱 세션 ID 입니다.
     */
    private var activeSessionId: String? = null

    /**
     * 실시간 서버 이벤트 수신용 스코프입니다.
     */
    private var scope: CoroutineScope? = null

    /**
     * 외부에 전파할 AI 이벤트 스트림입니다.
     */
    private val _events = MutableSharedFlow<AIEvent>(extraBufferCapacity = 64)

    /**
     * 현재 세션 system instruction 캐시입니다.
     */
    private var currentSystemInstruction: String? = null

    /**
     * 사용자 partial transcript 버퍼입니다.
     */
    private var userTranscriptBuffer: String = ""

    /**
     * AI partial transcript 버퍼입니다.
     */
    private var aiTranscriptionBuffer: String = ""

    /**
     * session-local turn 시퀀스입니다.
     */
    private var turnSequence: Long = 0L

    override suspend fun startSession(
        langCode: LangCode,
        systemInstruction: String
    ): Result<String> {
        return sessionMutex.withLock {
            if (
                session != null &&
                activeSessionId != null &&
                currentLang == langCode &&
                currentSystemInstruction == systemInstruction
            ) {
                return Result.success(activeSessionId!!)
            }

            try {
                stopInternal()
                _events.emit(AIEvent.Initializing)

                val liveModel = firebaseAI.liveModel(
                    modelName = "gemini-3.1-flash-live-preview",
                    systemInstruction = content { text(systemInstruction) },
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        inputAudioTranscription = AudioTranscriptionConfig()
                        outputAudioTranscription = AudioTranscriptionConfig()
                    }
                )

                val newSession = liveModel.connect()
                val newSessionId = UUID.randomUUID().toString()

                session = newSession
                activeSessionId = newSessionId
                currentSystemInstruction = systemInstruction
                currentLang = langCode
                turnSequence = 0L

                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                scope?.launch {
                    newSession.receive().collect { message ->
                        handleServerMessage(message)
                    }
                }

                _events.emit(AIEvent.Initialized(newSessionId))
                Result.success(newSessionId)
            } catch (error: Exception) {
                stopInternal()
                _events.emit(AIEvent.Error(error.message ?: "Connection Failed"))
                Result.failure(error)
            }
        }
    }

    /**
     * 서버 메시지를 도메인 이벤트로 변환합니다.
     *
     * @param message 수신한 서버 메시지
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun handleServerMessage(message: LiveServerMessage) {
        when (message) {
            is LiveServerContent -> handleServerContent(message)
            is LiveServerSetupComplete -> _events.emit(AIEvent.StateChanged(AIState.IDLE))
            is LiveServerGoAway -> {
                _events.emit(AIEvent.StateChanged(AIState.RECONNECTING))
                _events.emit(
                    AIEvent.SessionInterrupted(
                        message = "Live Session interrupted by server"
                    )
                )
            }
        }
    }

    /**
     * transcript, audio, turnComplete 를 포함한 content 메시지를 처리합니다.
     *
     * @param message 수신한 content 메시지
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun handleServerContent(message: LiveServerContent) {
        message.inputTranscription?.text
            ?.takeIf { it.isNotBlank() }
            ?.let { text ->
                userTranscriptBuffer = text
                _events.emit(
                    AIEvent.PartialTranscription(
                        text = text,
                        role = TurnSpeaker.USER
                    )
                )
                _events.emit(AIEvent.StateChanged(AIState.LISTENING))
            }

        message.outputTranscription?.text
            ?.takeIf { it.isNotBlank() }
            ?.let { text ->
                aiTranscriptionBuffer = text
                _events.emit(
                    AIEvent.PartialTranscription(
                        text = text,
                        role = TurnSpeaker.AI
                    )
                )
                _events.emit(AIEvent.StateChanged(AIState.SPEAKING))
            }

        message.content?.parts
            ?.filterIsInstance<InlineDataPart>()
            ?.forEach { part ->
                _events.emit(AIEvent.AudioResponse(part.inlineData))
            }

        if (message.turnComplete) {
            emitFinalTranscripts()
            _events.emit(AIEvent.StateChanged(AIState.IDLE))
        }

        if (message.interrupted) {
            aiTranscriptionBuffer = ""
            _events.emit(AIEvent.StateChanged(AIState.IDLE))
        }
    }

    /**
     * 버퍼에 쌓인 확정 transcript 를 final 이벤트로 발행합니다.
     */
    private suspend fun emitFinalTranscripts() {
        val sessionId = activeSessionId ?: return
        val sessionLang = currentLang ?: return

        if (userTranscriptBuffer.isNotBlank()) {
            emitFinalTranscript(
                sessionId = sessionId,
                text = userTranscriptBuffer,
                role = TurnSpeaker.USER,
                sessionLang = sessionLang
            )
            userTranscriptBuffer = ""
        }

        if (aiTranscriptionBuffer.isNotBlank()) {
            emitFinalTranscript(
                sessionId = sessionId,
                text = aiTranscriptionBuffer,
                role = TurnSpeaker.AI,
                sessionLang = sessionLang
            )
            aiTranscriptionBuffer = ""
        }
    }

    /**
     * 단일 확정 transcript 를 final 이벤트로 발행합니다.
     *
     * @param sessionId 현재 세션 ID
     * @param text 확정 transcript 본문
     * @param role 발화 주체
     */
    private suspend fun emitFinalTranscript(
        sessionId: String,
        text: String,
        role: TurnSpeaker,
        sessionLang: LangCode
    ) {
        if (text.isBlank()) return

        val turnId = nextTurnId(sessionId, role)
        val createdAt = System.currentTimeMillis()

        _events.emit(
            AIEvent.FinalTranscription(
                turnId = turnId,
                sessionId = sessionId,
                sessionLang = sessionLang,
                text = text,
                role = role,
                createdAt = createdAt,
                durationMs = null,
                tokenCount = null,
                confidence = null
            )
        )
    }

    /**
     * session-local 시퀀스 기반 turn ID 를 생성합니다.
     *
     * @param sessionId 현재 세션 ID
     * @param speaker 발화 주체
     * @return 확정 turn ID
     */
    private fun nextTurnId(sessionId: String, speaker: TurnSpeaker): String {
        turnSequence += 1L
        return "$sessionId-$turnSequence-${speaker.name}"
    }

    /**
     * 내부 세션 상태를 정리합니다.
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun stopInternal() {
        scope?.cancel()
        scope = null

        session?.let { currentSession ->
            if (!currentSession.isClosed()) {
                currentSession.close()
            }
        }

        session = null
        activeSessionId = null
        currentSystemInstruction = null
        currentLang = null
        userTranscriptBuffer = ""
        aiTranscriptionBuffer = ""
        turnSequence = 0L
    }

    override suspend fun sendAudioData(audio: ByteArray) {
        session?.sendAudioRealtime(
            InlineData(
                data = audio,
                mimeType = "audio/pcm;rate=16000"
            )
        )
    }

    override suspend fun sendTextData(text: String) {
        session?.sendTextRealtime(text)
    }

    override fun observeAIEvent(): Flow<AIEvent> = _events.asSharedFlow()

    override fun getActiveSessionId(): String? = activeSessionId

    override suspend fun stopSession() = sessionMutex.withLock {
        stopInternal()
    }
}