package com.app.umma.data.repository

import android.util.Base64
import android.util.Log
import com.app.umma.BuildConfig
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.repository.ChatRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * CHAT-POC-001 전용 OpenAI Realtime transport 구현체입니다.
 *
 * 기존 Gemini 기반 [ChatRepositoryImpl]을 제거하지 않고 같은 [ChatRepository] 계약만 구현해,
 * presentation/domain 레이어를 유지한 상태에서 OpenAI Realtime이 MVP toggle-to-talk 요구를
 * 만족하는지 검증하기 위한 PoC 경로입니다.
 */
@Singleton
class OpenAIRealtimeChatRepositoryPoC @Inject constructor() : ChatRepository {

    private val client = OkHttpClient()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private val events = MutableSharedFlow<AIEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private var webSocket: WebSocket? = null
    private var activeSessionId: String? = null
    private var currentLang: LangCode? = null
    private var currentSystemInstruction: String? = null
    // session.updated 를 받기 전에는 수동 turn 설정이 반영됐다고 볼 수 없으므로 READY 처리를 막는다.
    private var sessionReadySignal: CompletableDeferred<Unit>? = null

    // 기존 AIEvent.FinalTranscription 계약은 turnId 를 요구하므로 PoC 구현도 세션 내부 순번을 만든다.
    private var turnSequence: Long = 0L
    // ViewModel 이 녹음 종료 시 전달하는 duration 이 현재 계약상 user turn 종료 신호 역할도 한다.
    private var pendingUserTurnDurationMs: Long? = null
    // OpenAI input_audio_buffer 에 실제 오디오가 들어갔는지 추적해 빈 commit 을 방지한다.
    private var hasBufferedAudioForTurn: Boolean = false
    // 새 사용자 turn 의 첫 audio chunk 에서만 clear 를 보내 이전 turn buffer 혼입을 막는다.
    private var hasClearedInputForTurn: Boolean = false
    // 같은 사용자 turn 에서 commit/response 생성이 중복 실행되지 않도록 막는 플래그다.
    private var commitInFlight: Boolean = false
    // 사용자 자막 선표시를 검증하기 위해 response.create 를 user transcription completed 이후로 지연한다.
    private var responsePendingUntilUserTranscript: Boolean = false

    // AI transcript 는 delta 로 들어오므로 done 이벤트 전까지 누적해 final transcript 로 내보낸다.
    private var aiTranscriptBuffer: String = ""
    // response.create 기준 첫 audio/transcript delta 지연시간을 측정하기 위한 타임스탬프들이다.
    private var responseCreatedAtMs: Long? = null
    private var firstAudioReceivedAtMs: Long? = null
    private var userTurnCommittedAtMs: Long? = null
    private var userTranscriptCompletedAtMs: Long? = null
    private var firstAiTranscriptDeltaAtMs: Long? = null

    override suspend fun startSession(
        langCode: LangCode,
        systemInstruction: String
    ): Result<String> = sessionMutex.withLock {
        if (
            webSocket != null &&
            activeSessionId != null &&
            currentLang == langCode &&
            currentSystemInstruction == systemInstruction
        ) {
            return Result.success(activeSessionId!!)
        }

        runCatching {
            // 새 연결을 시작할 때는 이전 WebSocket 과 buffer 상태를 먼저 정리한다.
            stopInternal(clearAppSession = true)
            validatePoCConfig()
            events.emit(AIEvent.Initializing)

            // Android 앱에는 OpenAI API key 가 없으므로 Cloud Function 에서 client secret 을 받아온다.
            val token = fetchRealtimeToken()
            val newSessionId = UUID.randomUUID().toString()
            val readySignal = CompletableDeferred<Unit>()
            sessionReadySignal = readySignal

            activeSessionId = newSessionId
            currentLang = langCode
            currentSystemInstruction = systemInstruction
            turnSequence = 0L

            webSocket = client.newWebSocket(
                buildWebSocketRequest(token),
                buildWebSocketListener(
                    sessionId = newSessionId,
                    systemInstruction = systemInstruction
                )
            )

            // session.updated를 기다려 수동 turn 제어 설정이 실제 세션에 반영된 뒤 READY로 본다.
            withTimeout(SESSION_READY_TIMEOUT_MS) {
                readySignal.await()
            }

            events.emit(AIEvent.Initialized(newSessionId))
            events.emit(AIEvent.StateChanged(AIState.IDLE))
            newSessionId
        }.onFailure { error ->
            stopInternal(clearAppSession = true)
            events.emit(AIEvent.Error(error.message ?: "OpenAI Realtime connection failed"))
        }
    }

    override suspend fun reconnectSession(systemInstruction: String): Result<String> {
        val langCode = currentLang
            ?: return Result.failure(IllegalStateException("Session language not found"))

        // PoC에서는 transport 복구 품질보다 연결/이벤트 검증이 우선이므로 새 WebSocket으로 재시작한다.
        return startSession(langCode, systemInstruction)
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

        // 앱 recorder는 16kHz PCM을 만들고, OpenAI Realtime GA PCM 입력은 24kHz를 요구한다.
        // PoC 단계에서는 mono PCM16을 24kHz로 단순 보간해 transport 가능성부터 검증한다.
        val openAiPcm24k = upsamplePcm16Mono16kTo24k(audio)
        val encodedAudio = Base64.encodeToString(openAiPcm24k, Base64.NO_WRAP)
        socket.send(buildInputAudioAppendEvent(encodedAudio))
        hasBufferedAudioForTurn = true
        events.emit(AIEvent.StateChanged(AIState.LISTENING))
    }

    override fun setPendingUserTurnDuration(durationMs: Long?) {
        pendingUserTurnDurationMs = durationMs

        if (durationMs == null) {
            // stop/cleanup 경로에서 null 이 들어오면 현재 turn commit 도 함께 취소한다.
            commitInFlight = false
            return
        }

        // 기존 ChatRepository 계약에는 "사용자 turn 종료" 메서드가 없으므로,
        // PoC에서는 ViewModel이 녹음 종료 시 호출하는 duration 전달을 commit 트리거로 재사용한다.
        repositoryScope.launch {
            delay(COMMIT_GRACE_DELAY_MS)
            commitUserAudioAndCreateResponse()
        }
    }

    override suspend fun sendTextData(text: String) {
        if (text.isBlank()) return

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
        createResponse()
    }

    override fun observeAIEvent(): Flow<AIEvent> = events.asSharedFlow()

    override suspend fun stopSession(clearAppSession: Boolean) = sessionMutex.withLock {
        stopInternal(clearAppSession)
    }

    private fun buildWebSocketListener(
        sessionId: String,
        systemInstruction: String
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "OpenAI Realtime WebSocket opened code=${response.code}")
                // Cloud Function 이 만든 session 은 기본 VAD 설정일 수 있으므로
                // 앱이 원하는 수동 turn 제어 설정을 WebSocket 연결 직후 다시 적용한다.
                webSocket.send(buildSessionUpdateEvent(systemInstruction))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                repositoryScope.launch {
                    handleServerEvent(sessionId, text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                sessionReadySignal?.completeExceptionally(t)
                repositoryScope.launch {
                    events.emit(
                        AIEvent.Error(
                            t.message ?: "OpenAI Realtime WebSocket failed"
                        )
                    )
                    events.emit(AIEvent.StateChanged(AIState.ERROR))
                }
                Log.e(TAG, "OpenAI Realtime WebSocket failed", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "OpenAI Realtime WebSocket closed code=$code reason=$reason")
            }
        }
    }

    private suspend fun handleServerEvent(sessionId: String, rawJson: String) {
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
                userTurnCommittedAtMs = System.currentTimeMillis()
                Log.i(TAG, "OpenAI Realtime input audio committed")
            }
            "conversation.item.input_audio_transcription.delta" -> {
                // 현재 MVP 는 발화 종료 후 final 자막을 기준으로 하지만,
                // delta 도 role 분리 가능성을 확인하기 위해 AIEvent 로 전달한다.
                emitPartialTranscript(payload.string("delta"), TurnSpeaker.USER)
            }
            "conversation.item.input_audio_transcription.completed" -> {
                markUserTranscriptCompleted()
                emitFinalTranscript(
                    sessionId = sessionId,
                    text = payload.string("transcript"),
                    role = TurnSpeaker.USER
                )
                Log.i(TAG, "OpenAI Realtime user transcription usage=${payload["usage"]}")
                createResponseAfterUserTranscriptIfNeeded()
            }
            "response.output_audio.delta",
            "response.audio.delta" -> {
                // 첫 audio delta 기준으로 실제 사용자가 듣기 시작할 수 있는 지연시간을 남긴다.
                markFirstAudioLatencyIfNeeded()
                emitAudioDelta(payload.string("delta"))
            }
            "response.output_audio_transcript.delta",
            "response.audio_transcript.delta" -> {
                markFirstAiTranscriptDeltaIfNeeded()
                val delta = payload.string("delta")
                aiTranscriptBuffer += delta.orEmpty()
                emitPartialTranscript(delta, TurnSpeaker.AI)
            }
            "response.output_audio_transcript.done",
            "response.audio_transcript.done" -> {
                val transcript = payload.string("transcript").orEmpty()
                val finalText = transcript.ifBlank { aiTranscriptBuffer }
                emitFinalTranscript(
                    sessionId = sessionId,
                    text = finalText,
                    role = TurnSpeaker.AI
                )
                aiTranscriptBuffer = ""
            }
            "response.done" -> {
                // 일부 이벤트 순서에서는 transcript done 이 오기 전에 response.done 이 올 수 있어
                // 남은 buffer 가 있으면 여기서 final 로 보정해 기존 저장 계약을 지킨다.
                if (aiTranscriptBuffer.isNotBlank()) {
                    emitFinalTranscript(
                        sessionId = sessionId,
                        text = aiTranscriptBuffer,
                        role = TurnSpeaker.AI
                    )
                    aiTranscriptBuffer = ""
                }
                logResponseDoneUsage(payload)
                resetTurnTransportState()
                events.emit(AIEvent.StateChanged(AIState.IDLE))
            }
            "error" -> {
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
        if (commitInFlight || !hasBufferedAudioForTurn) return

        // commit 은 사용자가 두 번째 마이크 버튼을 눌러 발화를 끝낸 뒤에만 실행된다.
        // 여기서는 아직 response.create 를 보내지 않아 AI 응답이 사용자 자막보다 먼저 시작되지 않는다.
        commitInFlight = true
        webSocket?.send(buildInputAudioCommitEvent())
        responsePendingUntilUserTranscript = true
        events.emit(AIEvent.StateChanged(AIState.THINKING))
    }

    private fun createResponseAfterUserTranscriptIfNeeded() {
        if (!responsePendingUntilUserTranscript) return

        // CHAT-POC-001의 핵심 확인값은 "사용자 발화 종료 후 사용자 자막을 먼저 보여준 뒤
        // AI 응답을 시작할 수 있는가"이다. 따라서 commit 직후가 아니라 user transcription
        // completed 이벤트를 받은 다음 response.create를 보낸다.
        responsePendingUntilUserTranscript = false
        createResponse()
    }

    private fun createResponse() {
        // response.create 를 보낸 시각이 first audio/transcript delta latency 의 기준점이다.
        responseCreatedAtMs = System.currentTimeMillis()
        firstAudioReceivedAtMs = null
        firstAiTranscriptDeltaAtMs = null
        webSocket?.send(buildResponseCreateEvent())
        repositoryScope.launch {
            events.emit(AIEvent.StateChanged(AIState.THINKING))
        }
    }

    private suspend fun emitPartialTranscript(text: String?, role: TurnSpeaker) {
        if (text.isNullOrBlank()) return
        events.emit(AIEvent.PartialTranscription(text = text, role = role))
    }

    private suspend fun emitFinalTranscript(
        sessionId: String,
        text: String?,
        role: TurnSpeaker
    ) {
        val finalText = text?.trim().orEmpty()
        val sessionLang = currentLang ?: return
        if (finalText.isBlank()) return

        events.emit(
            AIEvent.FinalTranscription(
                turnId = nextTurnId(sessionId, role),
                sessionId = sessionId,
                text = finalText,
                sessionLang = sessionLang,
                role = role,
                createdAt = System.currentTimeMillis(),
                durationMs = when (role) {
                    TurnSpeaker.USER -> pendingUserTurnDurationMs
                    TurnSpeaker.AI -> null
                },
                tokenCount = computeTokenCount(finalText),
                confidence = null
            )
        )

        if (role == TurnSpeaker.USER) {
            pendingUserTurnDurationMs = null
        }
    }

    private suspend fun emitAudioDelta(base64Audio: String?) {
        if (base64Audio.isNullOrBlank()) return

        runCatching {
            Base64.decode(base64Audio, Base64.DEFAULT)
        }.onSuccess { audio ->
            events.emit(AIEvent.AudioResponse(audio))
            events.emit(AIEvent.StateChanged(AIState.SPEAKING))
        }.onFailure { error ->
            Log.w(TAG, "OpenAI Realtime audio delta decode failed: ${error.message}")
        }
    }

    private fun markFirstAudioLatencyIfNeeded() {
        if (firstAudioReceivedAtMs != null) return

        val createdAt = responseCreatedAtMs ?: return
        val firstAudioAt = System.currentTimeMillis()
        firstAudioReceivedAtMs = firstAudioAt
        Log.i(TAG, "CHAT-POC-001 first_audio_latency_ms=${firstAudioAt - createdAt}")
    }

    private fun markUserTranscriptCompleted() {
        val completedAt = System.currentTimeMillis()
        userTranscriptCompletedAtMs = completedAt
        userTurnCommittedAtMs?.let { committedAt ->
            Log.i(TAG, "CHAT-POC-001 user_transcript_latency_ms=${completedAt - committedAt}")
        }
    }

    private fun markFirstAiTranscriptDeltaIfNeeded() {
        if (firstAiTranscriptDeltaAtMs != null) return

        val firstDeltaAt = System.currentTimeMillis()
        firstAiTranscriptDeltaAtMs = firstDeltaAt
        responseCreatedAtMs?.let { createdAt ->
            Log.i(TAG, "CHAT-POC-001 first_ai_transcript_delta_latency_ms=${firstDeltaAt - createdAt}")
        }

        // user transcript와 AI transcript 순서가 PoC 핵심 판단값이므로 로그로 명확히 남긴다.
        val userCompletedAt = userTranscriptCompletedAtMs
        Log.i(
            TAG,
            "CHAT-POC-001 transcript_order user_before_ai=${userCompletedAt != null && userCompletedAt <= firstDeltaAt}"
        )
    }

    private fun logResponseDoneUsage(payload: JsonObject) {
        val usage = payload.jsonObject("response")?.jsonObject("usage")
            ?: payload.jsonObject("usage")
        Log.i(TAG, "CHAT-POC-001 response_done_usage=$usage")
    }

    private fun resetTurnTransportState() {
        hasBufferedAudioForTurn = false
        hasClearedInputForTurn = false
        commitInFlight = false
        responsePendingUntilUserTranscript = false
        responseCreatedAtMs = null
        firstAudioReceivedAtMs = null
        userTurnCommittedAtMs = null
        userTranscriptCompletedAtMs = null
        firstAiTranscriptDeltaAtMs = null
    }

    private fun buildSessionUpdateEvent(systemInstruction: String): String {
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
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(BuildConfig.OPENAI_REALTIME_TOKEN_URL)
            .post(body)
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

    private fun validatePoCConfig() {
        // 설정이 빠진 상태에서 조용히 기존 구현처럼 보이지 않게 명확한 실패 메시지를 낸다.
        check(BuildConfig.OPENAI_REALTIME_ENABLED) {
            "OpenAI Realtime PoC is disabled."
        }
        check(BuildConfig.OPENAI_REALTIME_TOKEN_URL.isNotBlank()) {
            "OPENAI_REALTIME_TOKEN_URL is required for OpenAI Realtime PoC."
        }
        check(BuildConfig.OPENAI_REALTIME_MODEL.isNotBlank()) {
            "OPENAI_REALTIME_MODEL is required for OpenAI Realtime PoC."
        }
    }

    private suspend fun stopInternal(clearAppSession: Boolean) {
        webSocket?.close(WEBSOCKET_NORMAL_CLOSE, "chat session stopped")
        webSocket = null
        sessionReadySignal?.cancel()
        sessionReadySignal = null
        pendingUserTurnDurationMs = null
        aiTranscriptBuffer = ""
        resetTurnTransportState()

        if (!clearAppSession) return

        activeSessionId = null
        currentLang = null
        currentSystemInstruction = null
        turnSequence = 0L
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

    private fun JsonObject.jsonObject(key: String): JsonObject? {
        return this[key] as? JsonObject
    }

    private fun JsonElement.jsonPrimitiveOrNull() = runCatching {
        jsonPrimitive
    }.getOrNull()

    private companion object {
        const val TAG = "OpenAIRealtimePoC"
        const val EVENT_BUFFER_CAPACITY = 64
        const val SESSION_READY_TIMEOUT_MS = 10_000L
        const val COMMIT_GRACE_DELAY_MS = 150L
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
