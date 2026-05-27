package com.app.umma.data.repository

import android.util.Log
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.realtime.SessionInterruptedReason
import com.app.umma.domain.repository.ChatRepository
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
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    constructor(
        firebaseAI: FirebaseAI,
        reconnectPolicy: ReconnectPolicy
    ) : this(firebaseAI) {
        this.reconnectPolicy = reconnectPolicy
    }

    /**
     * 자동 재연결 시도 정책입니다.
     */
    private var reconnectPolicy: ReconnectPolicy = ReconnectPolicy()

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
    private var receiveScope: CoroutineScope? = null

    /**
     * 자동 재연결 job 입니다.
     */
    private var reconnectJob: Job? = null

    /**
     * 자동 재연결 job 이 사용할 repository 생명주기 스코프입니다.
     */
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                stopInternal(clearAppSession = true)
                _events.emit(AIEvent.Initializing)

                val newSessionId = UUID.randomUUID().toString()

                connectLiveTransport(
                    langCode = langCode,
                    systemInstruction = systemInstruction,
                    sessionId = newSessionId,
                    resetTurnSequence = true
                )
                _events.emit(AIEvent.Initialized(newSessionId))
                Result.success(newSessionId)
            } catch (error: Exception) {
                stopInternal(clearAppSession = true)
                _events.emit(AIEvent.Error(error.message ?: "Connection Failed"))
                Result.failure(error)
            }
        }
    }

    override suspend fun reconnectSession(systemInstruction: String): Result<String> {
        return sessionMutex.withLock {
            val sessionId = activeSessionId
                ?: return Result.failure(IllegalStateException("Active session not found"))
            val langCode = currentLang
                ?: return Result.failure(IllegalStateException("Session language not found"))

            try {
                reconnectJob?.cancel()
                reconnectJob = null

                clearTranscriptBuffers()
                closeLiveTransport()
                _events.emit(AIEvent.StateChanged(AIState.RECONNECTING))

                connectLiveTransport(
                    langCode = langCode,
                    systemInstruction = systemInstruction,
                    sessionId = sessionId,
                    resetTurnSequence = false
                )

                _events.emit(AIEvent.Reconnected(sessionId))
                _events.emit(AIEvent.StateChanged(AIState.IDLE))
                Result.success(sessionId)
            } catch (error: Exception) {
                closeLiveTransport()
                _events.emit(
                    AIEvent.ReconnectFailed(
                        message = error.message ?: "재연결에 실패했습니다.",
                        recoverable = true
                    )
                )
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
                beginAutomaticReconnect(
                    reason = SessionInterruptedReason.SERVER_GO_AWAY,
                    message = "Live Session interrupted by server"
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
                /*Log.d("ChatRepository", "USER partial text=$text")
                Log.d("ChatRepository", "USER partial previousBuffer=$userTranscriptBuffer")*/
                userTranscriptBuffer += text
                /*Log.d("ChatRepository", "USER partial updatedBuffer=$userTranscriptBuffer")*/
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
                /*Log.d("ChatRepository", "AI partial incoming=$text")
                Log.d("ChatRepository", "AI partial previousBuffer=$aiTranscriptionBuffer")*/

                aiTranscriptionBuffer += text

                /*Log.d("ChatRepository", "AI partial updatedBuffer=$aiTranscriptionBuffer")*/
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
            /*Log.d(
                "ChatRepository",
                "turnComplete userBuffer=$userTranscriptBuffer aiBuffer=$aiTranscriptionBuffer"
            )*/
            emitFinalTranscript(
                sessionId = sessionId,
                text = userTranscriptBuffer,
                role = TurnSpeaker.USER,
                sessionLang = sessionLang
            )
            userTranscriptBuffer = ""
        }

        if (aiTranscriptionBuffer.isNotBlank()) {
            Log.d(
                "ChatRepository",
                "turnComplete userBuffer=$userTranscriptBuffer aiBuffer=$aiTranscriptionBuffer"
            )
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
     * Live transport 를 연결하고 receive collector 를 시작합니다.
     *
     * @param langCode 현재 대화 학습 언어
     * @param systemInstruction Live session 에 전달할 system prompt
     * @param sessionId 유지할 앱 레벨 세션 ID
     * @param resetTurnSequence 새 대화 시작 여부
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun connectLiveTransport(
        langCode: LangCode,
        systemInstruction: String,
        sessionId: String,
        resetTurnSequence: Boolean
    ) {
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

        session = newSession
        activeSessionId = sessionId
        currentSystemInstruction = systemInstruction
        currentLang = langCode
        if (resetTurnSequence) {
            turnSequence = 0L
        }

        startReceiveLoop(newSession)
    }

    /**
     * Live server message 수신 loop 를 시작합니다.
     *
     * @param liveSession 수신 대상 Live session
     */
    @OptIn(PublicPreviewAPI::class)
    private fun startReceiveLoop(liveSession: LiveSession) {
        receiveScope?.cancel()
        receiveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        receiveScope?.launch {
            try {
                liveSession.receive().collect { message ->
                    handleServerMessage(message)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                beginAutomaticReconnect(
                    reason = classifyReceiveException(error),
                    message = error.message ?: "Live stream interrupted"
                )
            }
        }
    }

    /**
     * 자동 재연결을 시작합니다.
     *
     * @param reason 중단 원인
     * @param message 사용자에게 전달할 메시지
     */
    private suspend fun beginAutomaticReconnect(
        reason: SessionInterruptedReason,
        message: String
    ) {
        if (reconnectJob?.isActive == true) return

        val sessionId = activeSessionId
        val langCode = currentLang
        val systemInstruction = currentSystemInstruction

        if (sessionId == null || langCode == null || systemInstruction == null) {
            _events.emit(
                AIEvent.ReconnectFailed(
                    message = "복구할 세션 정보가 없습니다.",
                    recoverable = false
                )
            )
            return
        }

        clearTranscriptBuffers()
        _events.emit(AIEvent.StateChanged(AIState.RECONNECTING))

        reconnectJob = repositoryScope.launch {
            var lastError: Throwable? = null

            for (attempt in 1..reconnectPolicy.maxAttempts) {
                _events.emit(
                    AIEvent.SessionInterrupted(
                        reason = reason,
                        attempt = attempt,
                        maxAttempts = reconnectPolicy.maxAttempts,
                        recoverable = true,
                        message = message
                    )
                )

                delay(reconnectPolicy.delayMillisFor(attempt))

                val result = sessionMutex.withLock {
                    runCatching {
                        closeLiveTransport()
                        connectLiveTransport(
                            langCode = langCode,
                            systemInstruction = systemInstruction,
                            sessionId = sessionId,
                            resetTurnSequence = false
                        )
                    }
                }

                if (result.isSuccess) {
                    _events.emit(AIEvent.Reconnected(sessionId))
                    _events.emit(AIEvent.StateChanged(AIState.IDLE))
                    reconnectJob = null
                    return@launch
                }

                lastError = result.exceptionOrNull()
                sessionMutex.withLock {
                    closeLiveTransport()
                }
            }

            _events.emit(
                AIEvent.ReconnectFailed(
                    message = lastError?.message ?: "자동 재연결에 실패했습니다.",
                    recoverable = true
                )
            )
            reconnectJob = null
        }
    }

    /**
     * receive exception 을 중단 원인으로 분류합니다.
     *
     * @param error 수신 중 발생한 예외
     * @return 세션 중단 원인
     */
    private fun classifyReceiveException(error: Exception): SessionInterruptedReason {
        return if (error is IOException) {
            SessionInterruptedReason.NETWORK_ERROR
        } else {
            SessionInterruptedReason.STREAM_ERROR
        }
    }

    /**
     * partial transcript buffer 를 정리합니다.
     */
    private fun clearTranscriptBuffers() {
        userTranscriptBuffer = ""
        aiTranscriptionBuffer = ""
    }

    /**
     * Live transport 만 정리합니다.
     */
    @OptIn(PublicPreviewAPI::class)
    private suspend fun closeLiveTransport() {
        receiveScope?.cancel()
        receiveScope = null

        session?.let { currentSession ->
            if (!currentSession.isClosed()) {
                currentSession.close()
            }
        }

        session = null
        clearTranscriptBuffers()
    }

    /**
     * 내부 세션 상태를 정리합니다.
     *
     * @param clearAppSession 앱 레벨 세션 정보까지 정리할지 여부
     */
    private suspend fun stopInternal(clearAppSession: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = null

        closeLiveTransport()

        if (!clearAppSession) return

        activeSessionId = null
        currentSystemInstruction = null
        currentLang = null
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
        stopInternal(clearAppSession = true)
    }
}

/**
 * RT-004 자동 재연결 정책입니다.
 */
data class ReconnectPolicy(
    val maxAttempts: Int = 3,
    val delaysMillis: List<Long> = listOf(500L, 1_500L, 3_000L)
) {
    /**
     * [attempt]에 해당하는 delay 를 반환합니다.
     */
    fun delayMillisFor(attempt: Int): Long {
        return delaysMillis.getOrElse((attempt - 1).coerceAtLeast(0)) {
            delaysMillis.lastOrNull() ?: 0L
        }
    }
}
