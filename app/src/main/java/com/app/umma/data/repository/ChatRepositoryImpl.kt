package com.app.umma.data.repository

import android.util.Base64
import android.util.Log
import com.app.umma.BuildConfig
import com.app.umma.devtools.chatpromptreview.ChatPromptReviewEvent
import com.app.umma.devtools.chatpromptreview.ChatPromptReviewEventType
import com.app.umma.devtools.chatpromptreview.ChatPromptReviewRepository
import com.app.umma.devtools.chatpromptreview.ChatPromptReviewSessionReporter
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.realtime.ChatTokenUsage
import com.app.umma.domain.model.realtime.ChatUsageKind
import com.app.umma.domain.model.realtime.SessionInterruptedReason
import com.app.umma.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OpenAI Realtime 기반 AI Chat transport repository 구현체입니다.
 *
 * 같은 [ChatRepository] 계약을 구현해 presentation/domain 레이어가 provider 를 직접 알지 않도록
 * 하면서, AI Chat realtime transport 를 OpenAI Realtime 단일 경로로 연결합니다.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val chatPromptReviewRepository: ChatPromptReviewRepository
) : ChatRepository, ChatPromptReviewSessionReporter {

    // OkHttp WebSocket 과 Cloud Function token endpoint 호출을 함께 처리하는 client 입니다.
    // OpenAI API key 는 Android 에 없고, 이 client 는 short-lived client secret 만 받아 사용합니다.
    private val client = OkHttpClient()
    // WebSocket callback 은 ViewModel scope 와 무관하게 도착하므로 repository 생명주기용 IO scope 에서 처리합니다.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // start/reconnect/stop 이 동시에 호출되면 app session 상태가 꼬일 수 있어 transport 변경은 직렬화합니다.
    private val sessionMutex = Mutex()
    // Presentation layer 는 provider 를 모른 채 AIEvent 만 구독합니다.
    // extraBufferCapacity 는 WebSocket callback 에서 이벤트를 빠르게 emit 할 때 backpressure 를 줄이기 위한 값입니다.
    private val events = MutableSharedFlow<AIEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    // OpenAI Realtime 은 이벤트별 JSON shape 이 다르므로 unknown field 를 무시하며 필요한 값만 읽습니다.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    /**
     * 자동 재연결 시도 정책입니다.
     *
     * 기존 Gemini Live 구현의 RT-004 재연결 정책을 provider 독립 계약으로 유지합니다.
     */
    private var reconnectPolicy: ReconnectPolicy = ReconnectPolicy()

    private var webSocket: WebSocket? = null
    // 앱이 대화 흐름을 추적하는 세션 ID 입니다. OpenAI server session ID 와 동일한 의미가 아닙니다.
    private var activeSessionId: String? = null
    // 재연결 시 최신 prompt 를 다시 session.update 로 보낼 수 있도록 현재 언어와 prompt 를 캐시합니다.
    private var currentLang: LangCode? = null
    private var currentSystemInstruction: String? = null
    private var currentSystemInstructionDebugTrace: String? = null
    private var currentTranscriptionPrompt: String? = null
    // 자동/수동 재연결 때도 학습자 수준에 맞춘 음성 속도를 동일하게 복원하기 위해 함께 캐시한다.
    private var currentOutputAudioSpeed: Double? = null
    // 실제 이탈/명시적 reconnect 로 닫은 socket 을 인스턴스 단위로 추적해 불필요한 자동 재연결을 막는다.
    // OkHttp callback 과 coroutine cleanup 이 서로 다른 thread 에서 접근하므로 synchronized set 으로 둔다.
    private val requestedCloseSockets: MutableSet<WebSocket> =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))
    // 자동 재연결 job 입니다. 같은 세션에서 중복 reconnect loop 가 생기지 않도록 단일 job 으로 관리합니다.
    private var reconnectJob: Job? = null
    // session.updated 를 받기 전에는 수동 turn 설정이 반영됐다고 볼 수 없으므로 READY 처리를 막는다.
    private var sessionReadySignal: CompletableDeferred<Unit>? = null
    // OkHttp onMessage callback 은 연속으로 들어오지만, callback 마다 launch 하면 처리 완료 순서가 뒤집힐 수 있다.
    // response.done 이후 늦게 처리된 audio delta 가 SPEAKING 을 다시 emit 하지 않도록 서버 이벤트는 단일 queue 로 직렬 처리한다.
    private var serverEventChannel: Channel<ServerRealtimeEvent>? = null
    private var serverEventJob: Job? = null

    // 기존 AIEvent.FinalTranscription 계약은 turnId 를 요구하므로 OpenAI 구현도 세션 내부 순번을 만든다.
    private var turnSequence: Long = 0L
    // ViewModel 이 user turn 종료 시 전달하는 duration 을 final USER transcript metadata 로 보관한다.
    private var pendingUserTurnDurationMs: Long? = null
    // OpenAI input_audio_buffer 에 실제 오디오가 들어갔는지 추적해 빈 commit 을 방지한다.
    private var hasBufferedAudioForTurn: Boolean = false
    // 새 사용자 turn 의 첫 audio chunk 에서만 clear 를 보내 이전 turn buffer 혼입을 막는다.
    private var hasClearedInputForTurn: Boolean = false
    // 같은 사용자 turn 에서 commit/response 생성이 중복 실행되지 않도록 막는 플래그다.
    private var commitInFlight: Boolean = false
    // 사용자 자막 선표시를 검증하기 위해 response.create 를 user transcription completed 이후로 지연한다.
    private var responsePendingUntilUserTranscript: Boolean = false
    // Phone 화면처럼 turn hint를 만들 수 있는 호출자가 명시적으로 켜는 1회성 대기 플래그다.
    // 이 플래그가 없으면 USER final 직후 기존처럼 바로 response.create를 보낸다.
    private var shouldWaitForNextResponseInstructions: Boolean = false
    // hint 대기 플래그가 켜진 경우에만 사용하는 fallback job이다.
    // hint가 오지 않아도 자동 응답을 보내 사용자 대화 흐름이 멈추지 않게 한다.
    private var responseCreateFallbackJob: Job? = null

    // AI transcript 는 delta 로 들어오므로 done 이벤트 전까지 누적해 final transcript 로 내보낸다.
    private var aiTranscriptBuffer: String = ""
    // OpenAI는 오디오 재생기를 직접 알지 않으므로, 수신한 PCM byte 수로 AI 응답 길이를 계산한다.
    private var aiAudioByteCount: Long = 0L
    // response lifecycle 은 late audio delta 방어의 기준이다.
    // provider response id 가 없는 이벤트도 있어, id guard 와 fallback guard 를 함께 둔다.
    private var activeResponseId: String? = null
    private val completedResponseIds = mutableListOf<String>()
    private var responseDoneUntilNextCreate: Boolean = false
    // response.create 기준 첫 audio/transcript delta 지연시간을 측정하기 위한 타임스탬프들이다.
    private var responseCreatedAtMs: Long? = null
    private var firstAudioReceivedAtMs: Long? = null
    private var userTurnCommittedAtMs: Long? = null
    private var userTranscriptCompletedAtMs: Long? = null
    private var firstAiTranscriptDeltaAtMs: Long? = null

    override suspend fun startSession(
        langCode: LangCode,
        systemInstruction: String,
        transcriptionPrompt: String?,
        outputAudioSpeed: Double,
        systemInstructionDebugTrace: String?
    ): Result<String> = sessionMutex.withLock {
        // 화면 회전이나 LaunchedEffect 재실행으로 같은 조건의 startSession 이 다시 들어오면
        // 기존 transport 를 그대로 재사용한다. 이 guard 가 없으면 subtitle/저장 흐름이 중복될 수 있다.
        if (
            webSocket != null &&
            activeSessionId != null &&
            currentLang == langCode &&
            currentSystemInstruction == systemInstruction &&
            currentTranscriptionPrompt == transcriptionPrompt &&
            currentOutputAudioSpeed == outputAudioSpeed
        ) {
            return Result.success(activeSessionId!!)
        }

        runCatching {
            stopInternal(clearAppSession = true)
            validateOpenAIConfig()
            events.emit(AIEvent.Initializing)

            // 기존 Gemini 구현과 동일하게 앱 내부 sessionId 는 repository 가 생성한다.
            // 이 ID 가 SessionMemory turnId prefix 와 correction handoff source 를 안정적으로 묶는다.
            val newSessionId = UUID.randomUUID().toString()
            connectRealtimeTransport(
                langCode = langCode,
                systemInstruction = systemInstruction,
                transcriptionPrompt = transcriptionPrompt,
                outputAudioSpeed = outputAudioSpeed,
                systemInstructionDebugTrace = systemInstructionDebugTrace,
                sessionId = newSessionId,
                resetTurnSequence = true
            )

            events.emit(AIEvent.Initialized(newSessionId))
            events.emit(AIEvent.StateChanged(AIState.IDLE))
            newSessionId
        }.onFailure {
            // 시작 실패 화면 처리는 StartSessionUseCase 결과를 받은 ViewModel 이 담당한다.
            // 여기서 AIEvent.Error 까지 emit 하면 동일 실패가 이벤트 경로와 Result 경로로 중복 반영될 수 있다.
            stopInternal(clearAppSession = true)
        }
    }

    override suspend fun reconnectSession(
        systemInstruction: String,
        transcriptionPrompt: String?,
        outputAudioSpeed: Double,
        systemInstructionDebugTrace: String?
    ): Result<String> = sessionMutex.withLock {
        // 수동 재시도는 "새 대화 시작"이 아니라 "현재 앱 세션의 transport 복구"입니다.
        // 따라서 activeSessionId 와 currentLang 이 없으면 복구할 기준이 없어 실패로 반환합니다.
        val sessionId = activeSessionId
            ?: return Result.failure(IllegalStateException("Active session not found"))
        val langCode = currentLang
            ?: return Result.failure(IllegalStateException("Session language not found"))

        runCatching {
            reconnectJob?.cancel()
            reconnectJob = null

            closeRealtimeTransport()
            events.emit(AIEvent.StateChanged(AIState.RECONNECTING))

            // 기존 앱 세션 ID를 유지한 채 WebSocket transport 만 새로 연다.
            // SessionMemory 와 correction handoff 는 이 app sessionId 를 기준으로 이어진다.
            connectRealtimeTransport(
                langCode = langCode,
                systemInstruction = systemInstruction,
                transcriptionPrompt = transcriptionPrompt,
                outputAudioSpeed = outputAudioSpeed,
                systemInstructionDebugTrace = systemInstructionDebugTrace,
                sessionId = sessionId,
                resetTurnSequence = false
            )

            events.emit(AIEvent.Reconnected(sessionId))
            events.emit(AIEvent.StateChanged(AIState.IDLE))
            sessionId
        }.onFailure { error ->
            closeRealtimeTransport()
            events.emit(
                AIEvent.ReconnectFailed(
                    message = error.message ?: "재연결에 실패했습니다.",
                    recoverable = true
                )
            )
        }
    }

    override fun getActiveSessionId(): String? = activeSessionId

    override fun getCurrentSessionLang(): LangCode? = currentLang

    override suspend fun sendAudioData(audio: ByteArray) {
        val socket = webSocket ?: return
        if (audio.isEmpty()) return

        // 수동 PTT에서는 새 사용자 turn 시작 시 이전 buffer를 먼저 비워야 이전 오디오가 섞이지 않는다.
        if (!hasClearedInputForTurn) {
            socket.send(buildInputAudioClearEvent())
            hasClearedInputForTurn = true
        }

        // Android recorder 는 16kHz PCM 을 만들기 때문에 OpenAI Realtime 입력 포맷에 맞춰 24kHz 로 변환한다.
        val openAiPcm24k = upsamplePcm16Mono16kTo24k(audio)
        val encodedAudio = Base64.encodeToString(openAiPcm24k, Base64.NO_WRAP)
        socket.send(buildInputAudioAppendEvent(encodedAudio))
        hasBufferedAudioForTurn = true
        events.emit(AIEvent.StateChanged(AIState.LISTENING))
    }

    private fun updatePendingUserTurnDuration(durationMs: Long?) {
        pendingUserTurnDurationMs = durationMs

        if (durationMs == null) {
            // stop/cleanup 경로에서 null 이 들어오면 현재 turn commit 대기도 함께 취소한다.
            commitInFlight = false
            responsePendingUntilUserTranscript = false
            shouldWaitForNextResponseInstructions = false
            responseCreateFallbackJob?.cancel()
            responseCreateFallbackJob = null
            return
        }
    }

    override fun cancelPendingUserTurn() {
        // cleanup 경로에서는 "turn 종료"가 아니라 "아직 확정되지 않은 turn 폐기"가 목적이다.
        // OpenAI input buffer 와 commit 대기 상태를 함께 비워 다음 user turn 에 이전 상태가 섞이지 않게 한다.
        webSocket?.send(buildInputAudioClearEvent())
        updatePendingUserTurnDuration(null)
        resetTurnTransportState()
    }

    override fun prepareNextResponseInstructions() {
        // Phone Chat 화면에서만 다음 USER final 이후 turn hint를 만들 수 있다.
        // Watch/기타 경로는 이 메서드를 호출하지 않으므로 기존 즉시 response.create 흐름을 유지한다.
        shouldWaitForNextResponseInstructions = true
    }

    override fun createResponse(instructions: String?) {
        if (!responsePendingUntilUserTranscript) {
            // 늦게 도착한 hint가 다음 turn으로 섞이면 더 위험하므로 pending 응답이 없으면 폐기한다.
            logTurnHintTrace(status = "ignored", reason = "no_pending_response")
            recordPromptReviewTurnHint(
                instructions = instructions,
                status = "ignored",
                reason = "no_pending_response"
            )
            return
        }
        responseCreateFallbackJob?.cancel()
        responseCreateFallbackJob = null
        createResponseInternal(
            instructions = instructions,
            turnHintStatus = if (instructions.isNullOrBlank()) "none" else "applied"
        )
    }

    override fun endUserTurn(durationMs: Long?) {
        // duration 은 USER final transcript metadata 이고, endUserTurn 은 transport commit 의 명시적 경계다.
        // 이 둘을 분리해야 ViewModel 이 "발화 길이 저장"과 "AI 응답 시작 허용"을 혼동하지 않는다.
        updatePendingUserTurnDuration(durationMs)
        repositoryScope.launch {
            delay(COMMIT_GRACE_DELAY_MS)
            commitUserAudioAndCreateResponse()
        }
    }

    override suspend fun sendTextData(text: String) {
        if (text.isBlank()) return

        // 현재 사용자 플로우는 음성 입력이 기준이지만, 연결 검증이나 내부 테스트에서는
        // text message 를 직접 보낼 수 있어 기존 보조 API 계약을 유지한다.
        webSocket?.send(
            buildJsonObject {
                put("type", "conversation.item.create")
                put(
                    "item",
                    buildJsonObject {
                        put("type", "message")
                        put("role", "user")
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "input_text")
                                        put("text", text)
                                    }
                                )
                            }
                        )
                    }
                )
            }.toString()
        )
        createResponseInternal(instructions = null, turnHintStatus = "not_requested")
    }

    override fun observeAIEvent(): Flow<AIEvent> = events.asSharedFlow()

    override suspend fun reportCurrentSession(reportNote: String?): Result<Unit> {
        if (!chatPromptReviewRepository.isEnabled()) {
            return Result.failure(IllegalStateException("prompt review is disabled"))
        }

        val userId = firebaseAuth.currentUser?.uid
        val sessionId = activeSessionId
        val language = currentLang
        if (userId.isNullOrBlank() || sessionId.isNullOrBlank()) {
            return Result.failure(IllegalStateException("active chat session is required for prompt review report"))
        }
        if (language == null) {
            return Result.failure(IllegalStateException("active chat language is required for prompt review report"))
        }

        // 신고 버튼은 transport 상태를 바꾸지 않고, 현재까지 메모리에 모인 dev review buffer만 저장한다.
        // 실패해도 대화 기능 자체의 실패가 아니므로 caller가 UI 메시지만 보여줄 수 있게 Result로 전달한다.
        chatPromptReviewRepository.recordSessionStarted(
            userId = userId,
            sessionId = sessionId,
            language = language,
            sessionPromptTrace = currentSystemInstructionDebugTrace,
            metadata = "label=manual_report speed=$currentOutputAudioSpeed"
        ).getOrThrow()
        chatPromptReviewRepository.reportSession(
            userId = userId,
            sessionId = sessionId,
            reportNote = reportNote
        ).getOrThrow()
        return Result.success(Unit)
    }

    override suspend fun stopSession(clearAppSession: Boolean) = sessionMutex.withLock {
        stopInternal(clearAppSession)
    }

    private fun buildWebSocketListener(
        sessionId: String,
        systemInstruction: String,
        transcriptionPrompt: String?,
        outputAudioSpeed: Double,
        eventChannel: Channel<ServerRealtimeEvent>
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "OpenAI Realtime WebSocket opened code=${response.code}")
                // Cloud Function 이 만든 session 은 기본 VAD 설정일 수 있으므로
                // 앱이 원하는 수동 turn 제어 설정을 WebSocket 연결 직후 다시 적용한다.
                webSocket.send(
                    buildSessionUpdateEvent(
                        systemInstruction = systemInstruction,
                        transcriptionPrompt = transcriptionPrompt,
                        outputAudioSpeed = outputAudioSpeed
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // WebSocket 메시지는 서버가 보낸 순서대로 queue 에 넣고, 하나의 consumer 에서만 처리한다.
                // 별도 coroutine 을 메시지마다 띄우면 response.done 과 audio delta 의 처리 완료 순서가 뒤집힐 수 있다.
                val result = eventChannel.trySend(ServerRealtimeEvent(sessionId, text))
                if (result.isFailure) {
                    Log.w(TAG, "OpenAI Realtime event dropped: event queue is closed")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // 앱이 의도적으로 닫은 socket 의 failure callback 은 오류로 취급하지 않습니다.
                // close 직후 callback 순서는 OkHttp 내부 scheduling 에 따라 달라질 수 있습니다.
                if (requestedCloseSockets.remove(webSocket)) return

                // session.updated 를 기다리는 중 발생한 실패는 startSession/reconnectSession 의 Result 로
                // 돌려보내야 합니다. 자동 재연결로 넘기면 아직 준비되지 않은 세션이 UI에 복구 중으로 보일 수 있습니다.
                sessionReadySignal?.completeExceptionally(t)
                if (sessionReadySignal != null) {
                    Log.e(TAG, "OpenAI Realtime WebSocket failed during session setup", t)
                    return
                }
                repositoryScope.launch {
                    beginAutomaticReconnect(
                        reason = classifyTransportException(t),
                        message = t.message ?: "OpenAI Realtime connection interrupted"
                    )
                }
                Log.e(TAG, "OpenAI Realtime WebSocket failed", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "OpenAI Realtime WebSocket closed code=$code reason=$reason")
                // 정상 close 나 stop/reconnect 과정에서 닫힌 socket 은 사용자에게 오류로 보이지 않아야 합니다.
                if (requestedCloseSockets.remove(webSocket) || code == WEBSOCKET_NORMAL_CLOSE) return

                repositoryScope.launch {
                    beginAutomaticReconnect(
                        reason = SessionInterruptedReason.STREAM_ERROR,
                        message = reason.ifBlank { "OpenAI Realtime connection closed" }
                    )
                }
            }
        }
    }

    private suspend fun connectRealtimeTransport(
        langCode: LangCode,
        systemInstruction: String,
        transcriptionPrompt: String?,
        outputAudioSpeed: Double,
        systemInstructionDebugTrace: String?,
        sessionId: String,
        resetTurnSequence: Boolean
    ) {
        validateOpenAIConfig()

        // Android 앱에는 OpenAI API key 가 없으므로 Cloud Function 에서 client secret 을 받아온다.
        val token = fetchRealtimeToken()
        val readySignal = CompletableDeferred<Unit>()
        sessionReadySignal = readySignal
        val eventChannel = startServerEventQueue()

        // app session 상태는 WebSocket 생성 전에 먼저 기록합니다.
        // 그래야 setup 실패가 onFailure 로 먼저 들어와도 cleanup/retry 경로가 어떤 session 을 다루는지 알 수 있습니다.
        activeSessionId = sessionId
        currentLang = langCode
        currentSystemInstruction = systemInstruction
        currentSystemInstructionDebugTrace = systemInstructionDebugTrace
        currentTranscriptionPrompt = transcriptionPrompt
        currentOutputAudioSpeed = outputAudioSpeed
        if (resetTurnSequence) {
            turnSequence = 0L
        }
        logPromptTrace(
            label = if (resetTurnSequence) {
                "session_start"
            } else {
                "session_reconnect"
            },
            trace = systemInstructionDebugTrace,
            metadata = "sessionId=$sessionId lang=${langCode.code} speed=$outputAudioSpeed"
        )
        recordPromptReviewSessionStarted(
            sessionId = sessionId,
            language = langCode,
            trace = systemInstructionDebugTrace,
            metadata = "label=${if (resetTurnSequence) "session_start" else "session_reconnect"} speed=$outputAudioSpeed"
        )

        webSocket = client.newWebSocket(
            buildWebSocketRequest(token),
            buildWebSocketListener(
                sessionId = sessionId,
                systemInstruction = systemInstruction,
                transcriptionPrompt = transcriptionPrompt,
                outputAudioSpeed = outputAudioSpeed,
                eventChannel = eventChannel
            )
        )

        // session.updated를 기다려 수동 turn 제어 설정이 실제 세션에 반영된 뒤 READY로 본다.
        withTimeout(SESSION_READY_TIMEOUT_MS) {
            readySignal.await()
        }
        sessionReadySignal = null
    }

    private suspend fun handleServerEvent(sessionId: String, rawJson: String) {
        // OpenAI Realtime 은 모든 메시지가 type 기반 JSON event 로 들어옵니다.
        // 여기서 provider event 를 앱 공통 AIEvent 로 변환해야 ViewModel 이 OpenAI 를 직접 알지 않습니다.
        val payload = runCatching {
            json.parseToJsonElement(rawJson).jsonObject
        }.getOrElse { error ->
            Log.w(TAG, "OpenAI Realtime event parse skipped: ${error.message}")
            return
        }

        when (val type = payload.string("type")) {
            "session.created" -> Log.i(TAG, "OpenAI Realtime session.created")
            "session.updated" -> {
                // 이 이벤트 이후에야 turn_detection=null 설정이 반영됐다고 보고 세션을 READY 로 전환한다.
                sessionReadySignal?.complete(Unit)
            }
            "input_audio_buffer.committed" -> {
                // commit 완료 시점은 USER transcript latency 측정의 시작점입니다.
                // response.create 는 아직 보내지 않았으므로 AI 응답은 시작되지 않습니다.
                userTurnCommittedAtMs = System.currentTimeMillis()
                Log.i(TAG, "OpenAI Realtime input audio committed")
            }
            "conversation.item.input_audio_transcription.delta" -> {
                // 현재 MVP 는 발화 종료 후 final 자막을 기준으로 하지만,
                // delta 도 role 분리 가능성을 확인하기 위해 AIEvent 로 전달한다.
                emitPartialTranscript(payload.string("delta"), TurnSpeaker.USER)
            }
            "conversation.item.input_audio_transcription.completed" -> {
                // 사용자의 final transcript 를 먼저 emit 한 뒤 response.create 를 보내야
                // 화면에서 USER 자막이 AI 자막보다 먼저 보이는 UX-002 기반이 됩니다.
                markUserTranscriptCompleted()
                val userTranscript = payload.string("transcript")
                val userTurnId = emitFinalTranscript(
                    sessionId = sessionId,
                    text = userTranscript,
                    role = TurnSpeaker.USER
                )
                // transcription usage는 response usage와 별도 과금/분석 대상이 될 수 있어
                // final transcript 저장 이벤트와 분리된 usage 이벤트로 ViewModel에 전달한다.
                emitUsageReport(
                    sessionId = sessionId,
                    turnId = userTurnId,
                    kind = ChatUsageKind.TRANSCRIPTION,
                    model = INPUT_TRANSCRIPTION_MODEL,
                    transcriptionModel = INPUT_TRANSCRIPTION_MODEL,
                    usage = parseTokenUsage(payload.jsonObject("usage")),
                    usageEventId = payload.string("event_id")
                )
                Log.i(TAG, "OpenAI Realtime user transcription usage=${payload["usage"]}")
                scheduleResponseAfterUserTranscriptIfNeeded()
            }
            "response.output_audio.delta",
            "response.audio.delta" -> {
                emitAudioDelta(payload)
            }
            "response.output_audio_transcript.delta",
            "response.audio_transcript.delta" -> {
                if (shouldIgnoreResponseDelta(payload, "transcript")) return
                markFirstAiTranscriptDeltaIfNeeded()
                val delta = payload.string("delta")
                aiTranscriptBuffer += delta.orEmpty()
                emitPartialTranscript(delta, TurnSpeaker.AI)
            }
            "response.output_audio_transcript.done",
            "response.audio_transcript.done" -> {
                if (shouldIgnoreResponseDelta(payload, "transcript_done")) return
                val transcript = payload.string("transcript").orEmpty()
                // 기존 Gemini 구현은 turnComplete 시점에 AI final 을 저장했다.
                // OpenAI에서도 transcript done 직후가 아니라 response.done 에서 저장해야
                // 같은 response 의 audio duration 까지 함께 반영할 수 있다.
                if (transcript.isNotBlank()) {
                    aiTranscriptBuffer = transcript
                }
            }
            "response.done" -> {
                markResponseDone(payload)
                // 일부 이벤트 순서에서는 transcript done 이 오기 전에 response.done 이 올 수 있어
                // 남은 buffer 가 있으면 여기서 final 로 보정해 기존 저장 계약을 지킨다.
                var aiTurnId: String? = null
                if (aiTranscriptBuffer.isNotBlank()) {
                    aiTurnId = emitFinalTranscript(
                        sessionId = sessionId,
                        text = aiTranscriptBuffer,
                        role = TurnSpeaker.AI
                    )
                    aiTranscriptBuffer = ""
                }
                // response.done usage는 AI 응답 전체의 text/audio breakdown을 포함한다.
                // usage 저장은 운영 데이터라서, 이 이벤트 실패가 final subtitle/turn 저장을 막지 않아야 한다.
                emitUsageReport(
                    sessionId = sessionId,
                    turnId = aiTurnId,
                    kind = ChatUsageKind.RESPONSE,
                    model = BuildConfig.OPENAI_REALTIME_MODEL,
                    transcriptionModel = null,
                    usage = extractResponseUsage(payload),
                    usageEventId = payload.string("event_id")
                )
                logResponseDoneUsage(payload)
                Log.d(
                    DIAG_TAG,
                    "realtime_response_done responseId=${payload.responseIdOrNull()} audioBytes=$aiAudioByteCount"
                )
                resetTurnTransportState()
                events.emit(AIEvent.StateChanged(AIState.IDLE))
            }
            "error" -> {
                // setup 중 error 는 start/reconnect Result 로도 전달되어야 하고,
                // setup 이후 error 는 ViewModel 이 Error UI 로 전환할 수 있도록 AIEvent 로도 전달합니다.
                val message = payload.jsonObject("error")?.string("message")
                    ?: "OpenAI Realtime error"
                sessionReadySignal?.completeExceptionally(IllegalStateException(message))
                events.emit(AIEvent.Error(message))
                events.emit(AIEvent.StateChanged(AIState.ERROR))
                Log.e(TAG, "OpenAI Realtime error event=$rawJson")
            }
            else -> Log.d(TAG, "OpenAI Realtime event ignored type=$type")
        }
    }

    private suspend fun commitUserAudioAndCreateResponse() {
        // 아직 audio 가 append 되지 않았다면 commit 자체가 OpenAI 오류가 될 수 있어 무시합니다.
        // commitInFlight 는 빠른 중복 클릭이나 stopRecordingForAiSpeaking 재진입을 막습니다.
        if (commitInFlight || !hasBufferedAudioForTurn) return

        // commit 은 사용자가 두 번째 마이크 버튼을 눌러 발화를 끝낸 뒤에만 실행된다.
        // 여기서는 아직 response.create 를 보내지 않아 AI 응답이 사용자 자막보다 먼저 시작되지 않는다.
        commitInFlight = true
        webSocket?.send(buildInputAudioCommitEvent())
        responsePendingUntilUserTranscript = true
        events.emit(AIEvent.StateChanged(AIState.THINKING))
    }

    private fun scheduleResponseAfterUserTranscriptIfNeeded() {
        if (!responsePendingUntilUserTranscript) return

        // CHAT-ENGINE-001의 핵심 확인값은 "사용자 발화 종료 후 사용자 자막을 먼저 보여준 뒤
        // AI 응답을 시작할 수 있는가"이다. 따라서 commit 직후가 아니라 user transcription
        // completed 이벤트를 받은 다음 response.create를 보낸다.
        if (!shouldWaitForNextResponseInstructions) {
            createResponseInternal(instructions = null, turnHintStatus = "not_requested")
            return
        }

        // Tune008에서는 ViewModel이 USER final event를 받아 세션용 snapshot을 갱신하고
        // 짧은 turn hint를 넘길 수 있도록 작은 fallback window를 둔다.
        // hint가 오지 않아도 자동 응답을 보내 사용자 대화 흐름이 멈추지 않게 한다.
        responseCreateFallbackJob?.cancel()
        responseCreateFallbackJob = repositoryScope.launch {
            delay(RESPONSE_HINT_FALLBACK_DELAY_MS)
            if (!responsePendingUntilUserTranscript) return@launch
            createResponseInternal(instructions = null, turnHintStatus = "fallback", turnHintReason = "timeout")
        }
    }

    private fun createResponseInternal(
        instructions: String?,
        turnHintStatus: String,
        turnHintReason: String? = null
    ) {
        responsePendingUntilUserTranscript = false
        shouldWaitForNextResponseInstructions = false
        responseCreateFallbackJob?.cancel()
        responseCreateFallbackJob = null
        // response.create 를 보낸 시각이 first audio/transcript delta latency 의 기준점이다.
        responseCreatedAtMs = System.currentTimeMillis()
        firstAudioReceivedAtMs = null
        firstAiTranscriptDeltaAtMs = null
        // 새 response.create 이후에는 이전 response.done 의 fallback guard 를 해제한다.
        // 아직 provider response id 를 모르는 구간이므로 첫 delta/done 에서 id 를 확정한다.
        activeResponseId = null
        responseDoneUntilNextCreate = false
        logTurnHintTrace(status = turnHintStatus, reason = turnHintReason)
        recordPromptReviewTurnHint(
            instructions = instructions,
            status = turnHintStatus,
            reason = turnHintReason
        )
        if (!instructions.isNullOrBlank()) {
            // OpenAI Realtime의 response.create.instructions는 해당 response의 session 설정을 override할 수 있다.
            // turn hint는 작은 맥락 업데이트이므로 conversation system item으로 넣고 session prompt는 유지한다.
            webSocket?.send(buildTurnHintSystemMessageEvent(instructions))
        }
        webSocket?.send(buildResponseCreateEvent())
        repositoryScope.launch {
            events.emit(AIEvent.StateChanged(AIState.THINKING))
        }
    }

    private suspend fun emitPartialTranscript(text: String?, role: TurnSpeaker) {
        if (text.isNullOrBlank()) return
        events.emit(AIEvent.PartialTranscription(text = text, role = role))
    }

    private fun logPromptTrace(
        label: String,
        trace: String?,
        metadata: String
    ) {
        if (!BuildConfig.DEBUG) return

        val safeTrace = trace?.takeIf { it.isNotBlank() }
        if (safeTrace == null) {
            Log.d(PROMPT_TRACE_TAG, "$label metadata=[$metadata] trace=null")
            return
        }
        Log.d(PROMPT_TRACE_TAG, "$label metadata=[$metadata] $safeTrace")
    }

    private fun logTurnHintTrace(
        status: String,
        reason: String? = null
    ) {
        if (!BuildConfig.DEBUG) return

        // turn hint 원문에는 현재 대화 위치와 사용자 의도가 들어갈 수 있어 로그에 남기지 않는다.
        // 수동 분석에는 어떤 세션/언어에서 snapshot hint가 적용됐는지만 있으면 충분하다.
        val lang = currentLang?.code ?: "none"
        val session = activeSessionId ?: "none"
        val source = when (status) {
            "applied",
            "ignored" -> "snapshot"
            else -> "none"
        }
        val reasonPart = reason?.let { " reason=$it" }.orEmpty()
        Log.d(PROMPT_TRACE_TAG, "turn_hint status=$status lang=$lang session=$session source=$source$reasonPart")
    }

    private suspend fun emitFinalTranscript(
        sessionId: String,
        text: String?,
        role: TurnSpeaker
    ): String? {
        // SessionMemory 에 저장되는 것은 partial 이 아니라 final transcript 뿐입니다.
        // 빈 transcript 는 correctionAvailable 신호를 만들면 안 되므로 여기서 방어합니다.
        val finalText = text?.trim().orEmpty()
        val sessionLang = currentLang ?: return null
        if (finalText.isBlank()) return null
        val turnId = nextTurnId(sessionId, role)
        val createdAt = System.currentTimeMillis()
        val event = AIEvent.FinalTranscription(
            turnId = turnId,
            sessionId = sessionId,
            text = finalText,
            sessionLang = sessionLang,
            role = role,
            createdAt = createdAt,
            durationMs = when (role) {
                TurnSpeaker.USER -> pendingUserTurnDurationMs
                TurnSpeaker.AI -> computePcmDurationMs(aiAudioByteCount)
            },
            tokenCount = computeTokenCount(finalText),
            confidence = null
        )

        events.emit(event)
        recordPromptReviewFinalTurn(event)

        if (role == TurnSpeaker.USER) {
            pendingUserTurnDurationMs = null
        }

        return turnId
    }

    private fun recordPromptReviewSessionStarted(
        sessionId: String,
        language: LangCode,
        trace: String?,
        metadata: String
    ) {
        if (!chatPromptReviewRepository.isEnabled()) return
        val userId = firebaseAuth.currentUser?.uid ?: return

        // 리뷰 자료수집은 개발 보조 도구이므로 저장 실패나 지연이 세션 연결을 막으면 안 된다.
        repositoryScope.launch {
            chatPromptReviewRepository.recordSessionStarted(
                userId = userId,
                sessionId = sessionId,
                language = language,
                sessionPromptTrace = trace,
                metadata = metadata
            ).onFailure { error ->
                Log.w(TAG, "chat prompt review session record skipped: ${error.message}", error)
            }
        }
    }

    private fun recordPromptReviewFinalTurn(event: AIEvent.FinalTranscription) {
        if (!chatPromptReviewRepository.isEnabled()) return
        val userId = firebaseAuth.currentUser?.uid ?: return

        // final turn mirror는 SessionMemory와 별도 컬렉션에만 저장해 운영 source of truth와 섞이지 않는다.
        repositoryScope.launch {
            chatPromptReviewRepository.recordEvent(
                userId = userId,
                event = ChatPromptReviewEvent(
                    eventId = promptReviewEventId(
                        type = ChatPromptReviewEventType.FinalTurn,
                        createdAt = event.createdAt,
                        suffix = event.turnId
                    ),
                    sessionId = event.sessionId,
                    type = ChatPromptReviewEventType.FinalTurn,
                    language = event.sessionLang,
                    createdAt = event.createdAt,
                    role = event.role,
                    turnId = event.turnId,
                    text = event.text,
                    metadata = "durationMs=${event.durationMs} tokenCount=${event.tokenCount}"
                )
            ).onFailure { error ->
                Log.w(TAG, "chat prompt review final turn record skipped: ${error.message}", error)
            }
        }
    }

    private fun recordPromptReviewTurnHint(
        instructions: String?,
        status: String,
        reason: String?
    ) {
        if (!chatPromptReviewRepository.isEnabled()) return
        val userId = firebaseAuth.currentUser?.uid ?: return
        val sessionId = activeSessionId ?: return
        val language = currentLang ?: return
        val createdAt = System.currentTimeMillis()
        val source = turnHintSource(status)

        // TurnHint는 prompt review 문서에서 final turn 사이에 끼워 보는 추적용 event다.
        // 실제로 provider에 실린 문장은 applied 상태일 때만 저장해 "생성됐지만 미적용" hint와 구분한다.
        repositoryScope.launch {
            chatPromptReviewRepository.recordEvent(
                userId = userId,
                event = ChatPromptReviewEvent(
                    eventId = promptReviewEventId(
                        type = ChatPromptReviewEventType.TurnHint,
                        createdAt = createdAt,
                        suffix = "$sessionId-$status"
                    ),
                    sessionId = sessionId,
                    type = ChatPromptReviewEventType.TurnHint,
                    language = language,
                    createdAt = createdAt,
                    role = null,
                    turnId = null,
                    text = instructions.takeIf { status == "applied" && !it.isNullOrBlank() },
                    metadata = buildTurnHintMetadata(
                        status = status,
                        source = source,
                        reason = reason
                    )
                )
            ).onFailure { error ->
                Log.w(TAG, "chat prompt review turn hint record skipped: ${error.message}", error)
            }
        }
    }

    private fun buildTurnHintMetadata(
        status: String,
        source: String,
        reason: String?
    ): String {
        // 사람이 Firestore/Markdown에서 바로 읽을 수 있게 key=value 형태만 유지한다.
        // 원문 hint는 text 필드가 담당하므로 metadata에는 상태와 출처만 둔다.
        val reasonPart = reason?.let { " reason=$it" }.orEmpty()
        return "status=$status source=$source$reasonPart"
    }

    private fun turnHintSource(status: String): String {
        return when (status) {
            "applied",
            "ignored" -> "snapshot"
            else -> "none"
        }
    }

    private fun promptReviewEventId(
        type: ChatPromptReviewEventType,
        createdAt: Long,
        suffix: String
    ): String {
        // Firestore document id로 안전하게 쓰기 위해 사람이 읽을 수 있는 순서 prefix와 sanitized suffix를 함께 둔다.
        val safeSuffix = suffix.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "$createdAt-${type.name}-$safeSuffix-${UUID.randomUUID()}"
    }

    private suspend fun emitUsageReport(
        sessionId: String,
        turnId: String?,
        kind: ChatUsageKind,
        model: String,
        transcriptionModel: String?,
        usage: ChatTokenUsage?,
        usageEventId: String?
    ) {
        val sessionLang = currentLang ?: return
        if (usage == null) return

        // provider event_id가 있으면 그대로 local idempotency key로 쓴다.
        // 없는 경우도 있어 session/turn/kind 조합으로 fallback을 만든다.
        // turnId가 없는 provider 예외에서는 생성 시각을 섞어 서로 다른 usage를 덮어쓰지 않게 한다.
        val stableUsageEventId = usageEventId
            ?: "$sessionId-${turnId ?: System.currentTimeMillis()}-${kind.name}"

        events.emit(
            AIEvent.ChatUsageReported(
                usageEventId = stableUsageEventId,
                sessionId = sessionId,
                turnId = turnId,
                sessionLang = sessionLang,
                kind = kind,
                model = model,
                transcriptionModel = transcriptionModel,
                createdAt = System.currentTimeMillis(),
                usage = usage
            )
        )
    }

    private suspend fun emitAudioDelta(base64Audio: String?) {
        if (base64Audio.isNullOrBlank()) return

        runCatching {
            Base64.decode(base64Audio, Base64.DEFAULT)
        }.onSuccess { audio ->
            aiAudioByteCount += audio.size.toLong()
            Log.d(
                DIAG_TAG,
                "realtime_audio_delta bytes=${audio.size} totalAudioBytes=$aiAudioByteCount"
            )
            events.emit(AIEvent.AudioResponse(audio))
            events.emit(AIEvent.StateChanged(AIState.SPEAKING))
        }.onFailure { error ->
            Log.w(TAG, "OpenAI Realtime audio delta decode failed: ${error.message}")
        }
    }

    private suspend fun emitAudioDelta(payload: JsonObject) {
        if (shouldIgnoreResponseDelta(payload, "audio")) return
        // 첫 audio delta 기준으로 실제 사용자가 듣기 시작할 수 있는 지연시간을 남긴다.
        markFirstAudioLatencyIfNeeded()
        emitAudioDelta(payload.string("delta"))
    }

    /**
     * 자동 재연결을 시작합니다.
     *
     * 기존 Gemini Live 구현의 핵심은 "transport 가 끊겨도 앱 세션과 저장 흐름은 유지"하는 것이었다.
     * OpenAI 이식 후에도 같은 사용자 대화 세션을 유지해야 하므로 activeSessionId 는 바꾸지 않고
     * WebSocket transport 만 다시 연결한다.
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
        val transcriptionPrompt = currentTranscriptionPrompt
        val outputAudioSpeed = currentOutputAudioSpeed

        if (sessionId == null || langCode == null || systemInstruction == null || outputAudioSpeed == null) {
            Log.e(
                TAG,
                "automatic reconnect aborted: missing session context sessionId=$sessionId, langCode=$langCode, hasPrompt=${systemInstruction != null}, hasSpeed=${outputAudioSpeed != null}"
            )
            events.emit(
                AIEvent.ReconnectFailed(
                    message = "복구할 세션 정보가 없습니다.",
                    recoverable = false
                )
            )
            return
        }

        Log.w(
            TAG,
            "automatic reconnect started: reason=$reason, message=$message, sessionId=$sessionId"
        )
        events.emit(AIEvent.StateChanged(AIState.RECONNECTING))

        reconnectJob = repositoryScope.launch {
            var lastError: Throwable? = null

            for (attempt in 1..reconnectPolicy.maxAttempts) {
                Log.w(
                    TAG,
                    "automatic reconnect attempt=$attempt/${reconnectPolicy.maxAttempts}, reason=$reason, sessionId=$sessionId"
                )
                events.emit(
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
                        closeRealtimeTransport()
                        connectRealtimeTransport(
                            langCode = langCode,
                            systemInstruction = systemInstruction,
                            transcriptionPrompt = transcriptionPrompt,
                            outputAudioSpeed = outputAudioSpeed,
                            systemInstructionDebugTrace = currentSystemInstructionDebugTrace,
                            sessionId = sessionId,
                            resetTurnSequence = false
                        )
                    }
                }

                if (result.isSuccess) {
                    events.emit(AIEvent.Reconnected(sessionId))
                    events.emit(AIEvent.StateChanged(AIState.IDLE))
                    reconnectJob = null
                    return@launch
                }

                lastError = result.exceptionOrNull()
                Log.e(
                    TAG,
                    "automatic reconnect attempt failed: attempt=$attempt, type=${lastError?.javaClass?.simpleName}, message=${lastError?.message}",
                    lastError
                )
                sessionMutex.withLock {
                    closeRealtimeTransport()
                }
            }

            events.emit(
                AIEvent.ReconnectFailed(
                    message = lastError?.message ?: "자동 재연결에 실패했습니다.",
                    recoverable = true
                )
            )
            Log.e(
                TAG,
                "automatic reconnect exhausted: sessionId=$sessionId, lastMessage=${lastError?.message}",
                lastError
            )
            reconnectJob = null
        }
    }

    /**
     * transport exception 을 중단 원인으로 분류합니다.
     *
     * @param error transport 계층에서 발생한 예외
     * @return 세션 중단 원인
     */
    private fun classifyTransportException(error: Throwable): SessionInterruptedReason {
        return if (error is IOException) {
            SessionInterruptedReason.NETWORK_ERROR
        } else {
            SessionInterruptedReason.STREAM_ERROR
        }
    }

    private fun markFirstAudioLatencyIfNeeded() {
        if (firstAudioReceivedAtMs != null) return

        val createdAt = responseCreatedAtMs ?: return
        val firstAudioAt = System.currentTimeMillis()
        firstAudioReceivedAtMs = firstAudioAt
        Log.i(TAG, "CHAT-ENGINE-001 first_audio_latency_ms=${firstAudioAt - createdAt}")
    }

    private fun markUserTranscriptCompleted() {
        val completedAt = System.currentTimeMillis()
        userTranscriptCompletedAtMs = completedAt
        userTurnCommittedAtMs?.let { committedAt ->
            Log.i(TAG, "CHAT-ENGINE-001 user_transcript_latency_ms=${completedAt - committedAt}")
        }
    }

    private fun markFirstAiTranscriptDeltaIfNeeded() {
        if (firstAiTranscriptDeltaAtMs != null) return

        val firstDeltaAt = System.currentTimeMillis()
        firstAiTranscriptDeltaAtMs = firstDeltaAt
        responseCreatedAtMs?.let { createdAt ->
            Log.i(TAG, "CHAT-ENGINE-001 first_ai_transcript_delta_latency_ms=${firstDeltaAt - createdAt}")
        }

        // user transcript와 AI transcript 순서가 PoC 핵심 판단값이므로 로그로 명확히 남긴다.
        val userCompletedAt = userTranscriptCompletedAtMs
        Log.i(
            TAG,
            "CHAT-ENGINE-001 transcript_order user_before_ai=${userCompletedAt != null && userCompletedAt <= firstDeltaAt}"
        )
    }

    private fun logResponseDoneUsage(payload: JsonObject) {
        val usage = extractResponseUsageJson(payload)
        Log.i(TAG, "CHAT-ENGINE-001 response_done_usage=$usage")
    }

    private fun extractResponseUsage(payload: JsonObject): ChatTokenUsage? {
        return parseTokenUsage(extractResponseUsageJson(payload))
    }

    private fun extractResponseUsageJson(payload: JsonObject): JsonObject? {
        return payload.jsonObject("response")?.jsonObject("usage")
            ?: payload.jsonObject("usage")
    }

    private fun parseTokenUsage(usage: JsonObject?): ChatTokenUsage? {
        if (usage == null) return null

        // OpenAI usage payload는 top-level total/input/output과 세부 breakdown을 함께 내려준다.
        // 전체 비용 추정에는 total이 편하지만, 실제 단가는 text/audio/input/output별로 달라질 수 있다.
        val inputDetails = usage.jsonObject("input_token_details")
        val outputDetails = usage.jsonObject("output_token_details")

        return ChatTokenUsage(
            totalTokens = usage.long("total_tokens"),
            inputTokens = usage.long("input_tokens"),
            outputTokens = usage.long("output_tokens"),
            inputTextTokens = inputDetails?.long("text_tokens"),
            inputAudioTokens = inputDetails?.long("audio_tokens"),
            inputCachedTokens = inputDetails?.long("cached_tokens"),
            outputTextTokens = outputDetails?.long("text_tokens"),
            outputAudioTokens = outputDetails?.long("audio_tokens")
        )
    }

    private fun resetTurnTransportState() {
        // 한 user turn / AI response 에만 유효한 transport 상태를 정리합니다.
        // activeSessionId/currentLang/currentSystemInstruction/currentOutputAudioSpeed 는 앱 세션 상태이므로 여기서 지우지 않습니다.
        hasBufferedAudioForTurn = false
        hasClearedInputForTurn = false
        commitInFlight = false
        responsePendingUntilUserTranscript = false
        shouldWaitForNextResponseInstructions = false
        responseCreateFallbackJob?.cancel()
        responseCreateFallbackJob = null
        responseCreatedAtMs = null
        firstAudioReceivedAtMs = null
        userTurnCommittedAtMs = null
        userTranscriptCompletedAtMs = null
        firstAiTranscriptDeltaAtMs = null
        aiAudioByteCount = 0L
    }

    private fun resetResponseLifecycleState() {
        // transport 자체가 닫힐 때는 이전 response 의 done/delta guard 도 함께 폐기한다.
        // 새 WebSocket 에 오래된 completed id 를 남기면 다음 응답을 잘못 late delta 로 볼 수 있다.
        activeResponseId = null
        responseDoneUntilNextCreate = false
        completedResponseIds.clear()
    }

    private fun startServerEventQueue(): Channel<ServerRealtimeEvent> {
        // reconnect/start 경계에서 이전 socket 의 consumer 가 남아 있으면 오래된 이벤트가 새 세션 상태를 덮을 수 있다.
        // 새 WebSocket 을 만들기 전에 이전 queue 를 닫아 현재 transport 이벤트만 받도록 한다.
        stopServerEventQueue()
        val channel = Channel<ServerRealtimeEvent>(capacity = Channel.UNLIMITED)
        serverEventChannel = channel
        serverEventJob = repositoryScope.launch {
            for (event in channel) {
                handleServerEvent(event.sessionId, event.rawJson)
            }
        }
        return channel
    }

    private fun stopServerEventQueue() {
        serverEventChannel?.close()
        serverEventChannel = null
        serverEventJob?.cancel()
        serverEventJob = null
    }

    private fun markResponseEventObserved(payload: JsonObject): String? {
        val responseId = payload.responseIdOrNull() ?: return null
        activeResponseId = responseId
        return responseId
    }

    private fun shouldIgnoreResponseDelta(payload: JsonObject, label: String): Boolean {
        val responseId = payload.responseIdOrNull()
        if (responseId != null && responseId in completedResponseIds) {
            // response.done 이 처리된 같은 response 의 late delta 는 UI 상태를 SPEAKING 으로 되돌리면 안 된다.
            Log.w(TAG, "OpenAI Realtime late $label delta ignored: responseId=$responseId")
            return true
        }
        if (responseId == null && responseDoneUntilNextCreate) {
            // 일부 provider 이벤트에는 response_id 가 없을 수 있다.
            // done 이후 다음 response.create 전이면 늦게 도착한 delta 로 보고 상태 전환을 막는다.
            Log.w(TAG, "OpenAI Realtime late $label delta ignored after response.done")
            return true
        }
        markResponseEventObserved(payload)
        return false
    }

    private fun markResponseDone(payload: JsonObject) {
        val responseId = payload.responseIdOrNull() ?: activeResponseId
        if (responseId != null) {
            completedResponseIds += responseId
            while (completedResponseIds.size > COMPLETED_RESPONSE_ID_LIMIT) {
                completedResponseIds.removeAt(0)
            }
        }
        activeResponseId = null
        responseDoneUntilNextCreate = true
    }

    private fun buildSessionUpdateEvent(
        systemInstruction: String,
        transcriptionPrompt: String?,
        outputAudioSpeed: Double
    ): String {
        return buildJsonObject {
            put("type", "session.update")
            put(
                "session",
                buildJsonObject {
                    put("type", "realtime")
                    put("model", BuildConfig.OPENAI_REALTIME_MODEL)
                    // StartSessionUseCase 가 만든 기존 prompt 를 그대로 전달해 provider 교체만 검증한다.
                    put("instructions", systemInstruction)
                    put(
                        "output_modalities",
                        buildJsonArray {
                            add(JsonPrimitive("audio"))
                        }
                    )
                    put(
                        "audio",
                        buildJsonObject {
                            put(
                                "input",
                                buildJsonObject {
                                    put(
                                        "format",
                                        buildJsonObject {
                                            put("type", "audio/pcm")
                                            put("rate", OPENAI_PCM_SAMPLE_RATE)
                                        }
                                    )
                                    put(
                                        "transcription",
                                        buildJsonObject {
                                            // 사용자 발화 종료 후 final transcript 를 받기 위한 input transcription 모델이다.
                                            put("model", INPUT_TRANSCRIPTION_MODEL)
                                            // STT prompt는 AI 응답 정책이 아니라 자막 전사 힌트다.
                                            // 사용자가 기준언어/학습언어/영어를 한 문장에 섞는 상황을 모델이 자연스럽게 받아 적도록 돕는다.
                                            if (!transcriptionPrompt.isNullOrBlank()) {
                                                put("prompt", transcriptionPrompt)
                                            }
                                        }
                                    )
                                    // 핵심 설정: 서버 VAD 가 아니라 앱의 두 번째 마이크 버튼이 turn 종료 기준이다.
                                    put("turn_detection", JsonNull)
                                }
                            )
                            put(
                                "output",
                                buildJsonObject {
                                    put(
                                        "format",
                                        buildJsonObject {
                                            put("type", "audio/pcm")
                                            // AudioPlayer 가 24kHz PCM 을 재생하므로 output 도 같은 rate 로 요청한다.
                                            put("rate", OPENAI_PCM_SAMPLE_RATE)
                                        }
                                    )
                                    put("voice", OPENAI_VOICE)
                                    // Realtime의 speed는 생성된 audio 후처리 속도다.
                                    // 프롬프트의 발화 속도 지시와 함께 써야 초급자에게 더 안정적으로 느리게 들린다.
                                    put("speed", outputAudioSpeed)
                                }
                            )
                        }
                    )
                }
            )
        }.toString()
    }

    private fun buildInputAudioClearEvent(): String {
        return buildJsonObject {
            put("type", "input_audio_buffer.clear")
        }.toString()
    }

    private fun buildInputAudioAppendEvent(base64Audio: String): String {
        return buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", base64Audio)
        }.toString()
    }

    private fun buildInputAudioCommitEvent(): String {
        return buildJsonObject {
            put("type", "input_audio_buffer.commit")
        }.toString()
    }

    private fun buildTurnHintSystemMessageEvent(instructions: String): String {
        return buildJsonObject {
            put("type", "conversation.item.create")
            put(
                "item",
                buildJsonObject {
                    put("type", "message")
                    put("role", "system")
                    put(
                        "content",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "input_text")
                                    put("text", instructions)
                                }
                            )
                        }
                    )
                }
            )
        }.toString()
    }

    private fun buildResponseCreateEvent(): String {
        return buildJsonObject {
            put("type", "response.create")
            put(
                "response",
                buildJsonObject {
                    put(
                        "output_modalities",
                        buildJsonArray {
                            add(JsonPrimitive("audio"))
                        }
                    )
                }
            )
        }.toString()
    }

    private fun buildWebSocketRequest(token: String): Request {
        return Request.Builder()
            .url("${BuildConfig.OPENAI_REALTIME_WS_URL}?model=${BuildConfig.OPENAI_REALTIME_MODEL}")
            .addHeader("Authorization", "Bearer $token")
            .build()
    }

    private suspend fun fetchRealtimeToken(): String = withContext(Dispatchers.IO) {
        // endpoint 는 Firebase Cloud Function 이며, OpenAI API key 는 서버 Secret Manager 에만 있다.
        val firebaseIdToken = fetchFirebaseIdToken()
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(BuildConfig.OPENAI_REALTIME_TOKEN_URL)
            .post(body)
            // CHAT-ENGINE-001-A: token endpoint 는 Firebase 로그인 사용자에게만 short-lived client secret 을 발급한다.
            // Android 앱에는 OpenAI API key 를 두지 않고, Firebase ID token 만 bearer 로 전달한다.
            .addHeader("Authorization", "Bearer $firebaseIdToken")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "OpenAI Realtime token request failed: code=${response.code}"
                )
            }
            parseTokenResponse(responseBody)
        }
    }

    private suspend fun fetchFirebaseIdToken(): String {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("Firebase login is required for OpenAI Realtime token.")

        // forceRefresh=false 로 일반 경로에서는 캐시된 유효 token 을 사용한다.
        // 만료된 경우 Firebase SDK 가 내부적으로 갱신하므로 매 turn 마다 강제 갱신하지 않는다.
        return user.getIdToken(false).await().token
            ?: throw IllegalStateException("Firebase ID token is empty.")
    }

    private fun parseTokenResponse(body: String): String {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return trimmed

        // OpenAI client secret 응답(value)과 내부 테스트용 단순 token 응답을 모두 허용한다.
        val root = json.parseToJsonElement(trimmed).jsonObject
        return root.jsonObject("client_secret")?.string("value")
            ?: root.string("client_secret")
            ?: root.string("value")
            ?: root.string("token")
            ?: root.string("ephemeral_key")
            ?: root.string("ephemeralKey")
            ?: throw IllegalStateException("OpenAI Realtime token response has no token field")
    }

    private fun validateOpenAIConfig() {
        // 설정이 빠진 상태에서 조용히 기존 구현처럼 보이지 않게 명확한 실패 메시지를 낸다.
        check(BuildConfig.OPENAI_REALTIME_TOKEN_URL.isNotBlank()) {
            "OPENAI_REALTIME_TOKEN_URL is required for OpenAI Realtime transport."
        }
        check(BuildConfig.OPENAI_REALTIME_MODEL.isNotBlank()) {
            "OPENAI_REALTIME_MODEL is required for OpenAI Realtime transport."
        }
    }

    /**
     * realtime transport 만 정리합니다.
     */
    private fun closeRealtimeTransport() {
        stopServerEventQueue()
        webSocket?.let { socket ->
            requestedCloseSockets.add(socket)
            socket.close(WEBSOCKET_NORMAL_CLOSE, "chat session stopped")
        }
        webSocket = null
        sessionReadySignal?.cancel()
        sessionReadySignal = null
        aiTranscriptBuffer = ""
        resetTurnTransportState()
        resetResponseLifecycleState()
    }

    /**
     * 내부 세션 상태를 정리합니다.
     *
     * @param clearAppSession 앱 레벨 세션 정보까지 정리할지 여부
     */
    private fun stopInternal(clearAppSession: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = null

        val sessionIdToFlush = activeSessionId
        val userIdToFlush = firebaseAuth.currentUser?.uid

        closeRealtimeTransport()
        pendingUserTurnDurationMs = null

        // 신고된 세션만 종료 시 최종 flush한다. 신고하지 않은 일반 개발 세션은 원격에 남기지 않는다.
        flushPromptReviewSession(
            userId = userIdToFlush,
            sessionId = sessionIdToFlush
        )

        if (!clearAppSession) return

        activeSessionId = null
        currentLang = null
        currentSystemInstruction = null
        currentSystemInstructionDebugTrace = null
        currentTranscriptionPrompt = null
        currentOutputAudioSpeed = null
        turnSequence = 0L
    }

    private fun flushPromptReviewSession(
        userId: String?,
        sessionId: String?
    ) {
        if (!chatPromptReviewRepository.isEnabled()) return
        if (userId.isNullOrBlank() || sessionId.isNullOrBlank()) return

        // final flush는 개발용 리뷰 데이터 저장이므로 stopSession 완료를 막지 않고 백그라운드에서 처리합니다.
        repositoryScope.launch {
            chatPromptReviewRepository.flushSession(
                userId = userId,
                sessionId = sessionId,
                finalFlush = true
            ).onFailure { error ->
                Log.w(TAG, "chat prompt review flush skipped: ${error.message}", error)
            }
        }
    }

    private fun nextTurnId(sessionId: String, speaker: TurnSpeaker): String {
        turnSequence += 1L
        return "$sessionId-$turnSequence-${speaker.name}"
    }

    private fun computeTokenCount(text: String): Int {
        val normalized = text.trim()
        if (normalized.isEmpty()) return 0
        return ceil(normalized.length / 4.0).toInt().coerceAtLeast(1)
    }

    private fun computePcmDurationMs(byteCount: Long): Long? {
        if (byteCount <= 0L) return null
        // PCM 16-bit mono 이므로 2 bytes 를 1 sample 로 보고 재생 길이를 계산한다.
        val sampleCount = byteCount / BYTES_PER_SAMPLE
        return ((sampleCount * 1000L) / OPENAI_PCM_SAMPLE_RATE).coerceAtLeast(1L)
    }

    private fun upsamplePcm16Mono16kTo24k(source: ByteArray): ByteArray {
        val sampleCount = source.size / BYTES_PER_SAMPLE
        if (sampleCount <= 1) return source

        val sourceSamples = ShortArray(sampleCount) { index ->
            val low = source[index * BYTES_PER_SAMPLE].toInt() and BYTE_MASK
            val high = source[index * BYTES_PER_SAMPLE + 1].toInt()
            ((high shl BYTE_BITS) or low).toShort()
        }

        val targetCount = sampleCount * OPENAI_PCM_SAMPLE_RATE / APP_PCM_SAMPLE_RATE
        val targetSamples = ShortArray(targetCount)
        for (targetIndex in 0 until targetCount) {
            val sourcePosition = targetIndex * APP_PCM_SAMPLE_RATE.toDouble() / OPENAI_PCM_SAMPLE_RATE
            val lowerIndex = sourcePosition.toInt().coerceIn(0, sampleCount - 1)
            val upperIndex = (lowerIndex + 1).coerceAtMost(sampleCount - 1)
            val ratio = sourcePosition - lowerIndex

            // 16kHz와 24kHz는 2:3 비율이지만, target sample 기준으로 보간해야
            // 긴 오디오 chunk에서도 target buffer 크기와 write 횟수가 정확히 일치한다.
            val lower = sourceSamples[lowerIndex].toInt()
            val upper = sourceSamples[upperIndex].toInt()
            targetSamples[targetIndex] = (lower + ((upper - lower) * ratio)).toInt().toShort()
        }

        val target = ByteArray(targetSamples.size * BYTES_PER_SAMPLE)
        targetSamples.forEachIndexed { index, sample ->
            target[index * BYTES_PER_SAMPLE] = (sample.toInt() and BYTE_MASK).toByte()
            target[index * BYTES_PER_SAMPLE + 1] = ((sample.toInt() shr BYTE_BITS) and BYTE_MASK).toByte()
        }
        return target
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitiveOrNull()?.contentOrNull
    }

    private fun JsonObject.long(key: String): Long? {
        val primitive = this[key]?.jsonPrimitiveOrNull() ?: return null
        return primitive.contentOrNull?.toLongOrNull()
    }

    private fun JsonObject.jsonObject(key: String): JsonObject? {
        return this[key] as? JsonObject
    }

    private fun JsonObject.responseIdOrNull(): String? {
        // OpenAI Realtime event 는 type 별로 response_id 를 top-level 로 주거나,
        // response.done 처럼 response 객체 안의 id 로 제공할 수 있다.
        // 두 위치를 모두 확인해야 lifecycle guard 가 provider event shape 변화에 덜 취약하다.
        return string("response_id")
            ?: jsonObject("response")?.string("id")
            ?: string("id")
    }

    private fun JsonElement.jsonPrimitiveOrNull() = runCatching {
        jsonPrimitive
    }.getOrNull()

    private companion object {
        const val TAG = "OpenAIRealtime"
        const val DIAG_TAG = "AiChatPlayback"
        const val PROMPT_TRACE_TAG = "AiChatPromptTrace"
        const val EVENT_BUFFER_CAPACITY = 64
        const val COMPLETED_RESPONSE_ID_LIMIT = 24
        const val SESSION_READY_TIMEOUT_MS = 10_000L
        const val COMMIT_GRACE_DELAY_MS = 150L
        const val RESPONSE_HINT_FALLBACK_DELAY_MS = 180L
        const val WEBSOCKET_NORMAL_CLOSE = 1000
        const val APP_PCM_SAMPLE_RATE = 16_000
        const val OPENAI_PCM_SAMPLE_RATE = 24_000
        const val BYTES_PER_SAMPLE = 2
        const val BYTE_BITS = 8
        const val BYTE_MASK = 0xFF
        const val INPUT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe"
        const val OPENAI_VOICE = "marin"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/**
 * WebSocket callback 에서 받은 원본 server event 입니다.
 *
 * OkHttp callback 마다 별도 coroutine 을 만들면 이벤트 처리 완료 순서가 바뀔 수 있으므로,
 * 이 모델을 단일 Channel 에 넣어 수신 순서대로 provider event mapping 을 수행한다.
 */
private data class ServerRealtimeEvent(
    val sessionId: String,
    val rawJson: String
)

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
