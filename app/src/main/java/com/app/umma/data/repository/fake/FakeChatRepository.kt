package com.app.umma.data.repository.fake

import android.util.Log
import com.app.umma.data.repository.fake.demo.chat.ChatDemoFixtures
import com.app.umma.data.repository.fake.demo.chat.ChatDemoPreset
import com.app.umma.data.repository.fake.demo.chat.ChatDemoPresetConfig
import com.app.umma.data.repository.fake.demo.chat.HandoffFixture
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.repository.ChatRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Fake chat transport used by `mockDebug` to reproduce deterministic chat flows.
 */
@Singleton
class FakeChatRepository @Inject constructor() : ChatRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = MutableSharedFlow<AIEvent>(extraBufferCapacity = 32)

    private var activeSessionId: String? = null
    private var currentLang: LangCode? = null
    private var pendingUserTurnDurationMs: Long? = null
    private var emittedUserTurn: Boolean = false
    private var interruptionEmitted: Boolean = false
    private var activeJob: Job? = null
    private var preset: ChatDemoPreset = ChatDemoPresetConfig.activePreset
    private var usesInjectedPreset: Boolean = false

    internal constructor(
        preset: ChatDemoPreset
    ) : this() {
        this.preset = preset
        this.usesInjectedPreset = true
    }

    override suspend fun startSession(
        langCode: LangCode,
        systemInstruction: String
    ): Result<String> {
        stopInternal(clearAppSession = true)
        if (!usesInjectedPreset) {
            preset = ChatDemoPresetConfig.activePreset
        }
        activeSessionId = UUID.randomUUID().toString()
        currentLang = langCode
        emittedUserTurn = false
        interruptionEmitted = false
        logPreset("startSession lang=${langCode.code}, preset=$preset")
        events.emit(AIEvent.Initializing)
        val sessionId = activeSessionId!!
        events.emit(AIEvent.Initialized(sessionId))

        when (preset) {
            ChatDemoPreset.HandoffSuccess,
            ChatDemoPreset.SaveSignalOnly,
            ChatDemoPreset.HandoffDuplicateFinal,
            ChatDemoPreset.RecordingInterrupted,
            ChatDemoPreset.SilentInputNoFinal -> events.emit(AIEvent.StateChanged(AIState.IDLE))
            ChatDemoPreset.ReconnectSuccess,
            ChatDemoPreset.ReconnectFailed -> events.emit(AIEvent.StateChanged(AIState.IDLE))
            ChatDemoPreset.FatalError -> {
                events.emit(AIEvent.StateChanged(AIState.IDLE))
                events.emit(AIEvent.Error(ChatDemoFixtures.fatalErrorMessage))
                logPreset("fatal_error emitted on startSession message=${ChatDemoFixtures.fatalErrorMessage}")
            }
        }

        logPreset("session initialized sessionId=$sessionId")
        return Result.success(sessionId)
    }

    override suspend fun reconnectSession(systemInstruction: String): Result<String> {
        val sessionId = activeSessionId
            ?: return Result.failure(IllegalStateException("Active session not found"))

        return when (preset) {
            ChatDemoPreset.ReconnectSuccess,
            ChatDemoPreset.HandoffSuccess,
            ChatDemoPreset.SaveSignalOnly,
            ChatDemoPreset.HandoffDuplicateFinal,
            ChatDemoPreset.SilentInputNoFinal -> {
                logPreset("reconnectSession success preset=$preset, sessionId=$sessionId")
                events.emit(AIEvent.StateChanged(AIState.RECONNECTING))
                events.emit(AIEvent.Reconnected(sessionId))
                events.emit(AIEvent.StateChanged(AIState.IDLE))
                Result.success(sessionId)
            }

            ChatDemoPreset.ReconnectFailed,
            ChatDemoPreset.RecordingInterrupted -> {
                logPreset("reconnectSession failed preset=$preset, recoverable=true")
                events.emit(AIEvent.StateChanged(AIState.RECONNECTING))
                events.emit(
                    AIEvent.ReconnectFailed(
                        message = ChatDemoFixtures.reconnectFailed.message,
                        recoverable = true
                    )
                )
                Result.failure(IllegalStateException(ChatDemoFixtures.reconnectFailed.message))
            }

            ChatDemoPreset.FatalError -> {
                logPreset("reconnectSession fatal_error message=${ChatDemoFixtures.fatalErrorMessage}")
                events.emit(AIEvent.Error(ChatDemoFixtures.fatalErrorMessage))
                Result.failure(IllegalStateException(ChatDemoFixtures.fatalErrorMessage))
            }
        }
    }

    override fun getActiveSessionId(): String? = activeSessionId

    override fun getCurrentSessionLang(): LangCode? = currentLang

    override suspend fun sendAudioData(audio: ByteArray) {
        logPreset("sendAudioData trigger preset=$preset, bytes=${audio.size}")
        when (preset) {
            ChatDemoPreset.HandoffSuccess -> emitHandoffTurnIfNeeded()
            ChatDemoPreset.SaveSignalOnly -> emitSignalOnlyTurnIfNeeded()
            ChatDemoPreset.HandoffDuplicateFinal -> emitDuplicateFinalTurnIfNeeded()
            ChatDemoPreset.RecordingInterrupted -> scheduleRecordingInterrupted()
            ChatDemoPreset.SilentInputNoFinal -> emitSilentInputIfNeeded()
            ChatDemoPreset.ReconnectSuccess -> scheduleReconnect(success = true)
            ChatDemoPreset.ReconnectFailed -> scheduleReconnect(success = false)
            ChatDemoPreset.FatalError -> logPreset("sendAudioData ignored because fatal_error is emitted on startSession")
        }
    }

    private fun updatePendingUserTurnDuration(durationMs: Long?) {
        pendingUserTurnDurationMs = durationMs
    }

    override fun cancelPendingUserTurn() {
        // Fake 구현에서도 cleanup 의미를 production 과 맞춰 pending duration 만 비운다.
        updatePendingUserTurnDuration(null)
    }

    override fun endUserTurn(durationMs: Long?) {
        // Fake 구현은 실제 transport commit 이 없으므로 production 과 같은 duration metadata 만 보관한다.
        // 시나리오별 final event 발생은 preset 로직이 따로 담당한다.
        updatePendingUserTurnDuration(durationMs)
    }

    private suspend fun emitHandoffTurnIfNeeded() {
        if (emittedUserTurn) {
            logPreset("handoff_success ignored because user final was already emitted")
            return
        }
        logPreset("handoff_success emitting final user turn")
        emitUserFinalTurn(
            fixture = ChatDemoFixtures.handoffSuccess,
            duplicateFinal = false
        )
    }

    private suspend fun emitSignalOnlyTurnIfNeeded() {
        if (emittedUserTurn) {
            logPreset("save_signal_only ignored because user final was already emitted")
            return
        }
        logPreset("save_signal_only emitting final user turn for correction signal")
        emitUserFinalTurn(
            fixture = ChatDemoFixtures.saveSignalOnly,
            duplicateFinal = false
        )
    }

    private suspend fun emitDuplicateFinalTurnIfNeeded() {
        if (emittedUserTurn) {
            logPreset("handoff_duplicate_final ignored because user final was already emitted")
            return
        }
        logPreset("handoff_duplicate_final emitting duplicate final events")
        emitUserFinalTurn(
            fixture = ChatDemoFixtures.handoffDuplicateFinal,
            duplicateFinal = true
        )
    }

    private suspend fun emitSilentInputIfNeeded() {
        if (emittedUserTurn) {
            logPreset("silent_input_no_final ignored because partial was already emitted")
            return
        }
        logPreset("silent_input_no_final emitting partial only")
        emittedUserTurn = true
        events.emit(AIEvent.StateChanged(AIState.LISTENING))
        events.emit(
            AIEvent.PartialTranscription(
                text = ChatDemoFixtures.silentInputPartialText,
                role = TurnSpeaker.USER
            )
        )
        pendingUserTurnDurationMs = null
        events.emit(AIEvent.StateChanged(AIState.IDLE))
        logPreset("silent_input_no_final completed without final transcription")
    }

    private suspend fun emitUserFinalTurn(
        fixture: HandoffFixture,
        duplicateFinal: Boolean
    ) {
        val sessionId = activeSessionId ?: return
        val lang = currentLang ?: return
        val finalEvent = AIEvent.FinalTranscription(
            turnId = "$sessionId-1-${TurnSpeaker.USER.name}",
            sessionId = sessionId,
            text = fixture.finalUserText,
            sessionLang = lang,
            role = TurnSpeaker.USER,
            createdAt = System.currentTimeMillis(),
            durationMs = pendingUserTurnDurationMs,
            tokenCount = fixture.finalUserText.split(' ').size,
            confidence = 0.99
        )

        emittedUserTurn = true
        events.emit(AIEvent.StateChanged(AIState.LISTENING))
        fixture.partialUserText?.let { partial ->
            events.emit(
                AIEvent.PartialTranscription(
                    text = partial,
                    role = TurnSpeaker.USER
                )
            )
        }
        events.emit(finalEvent)
        if (duplicateFinal) {
            events.emit(finalEvent)
            logPreset("duplicate final emitted turnId=${finalEvent.turnId}")
        }
        pendingUserTurnDurationMs = null
        events.emit(AIEvent.StateChanged(AIState.IDLE))
        logPreset(
            "final user turn emitted turnId=${finalEvent.turnId}, lang=${lang.code}, duplicateFinal=$duplicateFinal"
        )
    }

    override suspend fun sendTextData(text: String) {
        sendAudioData(text.encodeToByteArray())
    }

    override fun observeAIEvent(): Flow<AIEvent> = events.asSharedFlow()

    override suspend fun stopSession(clearAppSession: Boolean) {
        stopInternal(clearAppSession = clearAppSession)
    }

    private fun scheduleReconnect(success: Boolean) {
        if (interruptionEmitted) {
            logPreset("reconnect preset ignored because interruption was already emitted")
            return
        }

        val sessionId = activeSessionId ?: return
        val fixture = if (success) {
            ChatDemoFixtures.reconnectSuccess
        } else {
            ChatDemoFixtures.reconnectFailed
        }

        interruptionEmitted = true
        activeJob?.cancel()
        logPreset("reconnect_${if (success) "success" else "failed"} scheduling interruption")
        activeJob = repositoryScope.launch {
            events.emit(
                AIEvent.SessionInterrupted(
                    reason = fixture.reason,
                    attempt = fixture.attempt,
                    maxAttempts = fixture.maxAttempts,
                    recoverable = true,
                    message = fixture.message
                )
            )
            if (success) {
                events.emit(AIEvent.Reconnected(sessionId))
                events.emit(AIEvent.StateChanged(AIState.IDLE))
                logPreset("reconnect_success emitted SessionInterrupted -> Reconnected -> IDLE")
            } else {
                events.emit(
                    AIEvent.ReconnectFailed(
                        message = fixture.message,
                        recoverable = true
                    )
                )
                logPreset("reconnect_failed emitted SessionInterrupted -> ReconnectFailed")
            }
        }
    }

    private fun scheduleRecordingInterrupted() {
        if (interruptionEmitted) {
            logPreset("recording_interrupted ignored because interruption was already emitted")
            return
        }

        val fixture = ChatDemoFixtures.recordingInterrupted
        interruptionEmitted = true
        activeJob?.cancel()
        logPreset("recording_interrupted scheduling recording failure")
        activeJob = repositoryScope.launch {
            events.emit(AIEvent.StateChanged(AIState.LISTENING))
            events.emit(
                AIEvent.SessionInterrupted(
                    reason = fixture.reason,
                    attempt = fixture.attempt,
                    maxAttempts = fixture.maxAttempts,
                    recoverable = true,
                    message = fixture.message
                )
            )
            events.emit(
                AIEvent.ReconnectFailed(
                    message = fixture.message,
                    recoverable = true
                )
            )
            logPreset("recording_interrupted emitted LISTENING -> SessionInterrupted -> ReconnectFailed")
        }
    }

    private suspend fun stopInternal(clearAppSession: Boolean) {
        activeJob?.cancel()
        activeJob = null
        pendingUserTurnDurationMs = null
        emittedUserTurn = false
        interruptionEmitted = false
        if (!clearAppSession) return

        activeSessionId = null
        currentLang = null
    }

    private fun logPreset(message: String) {
        Log.d(CHAT_MOCK_PRESET_TAG, "[$preset] $message")
    }

    private companion object {
        const val CHAT_MOCK_PRESET_TAG = "ChatMockPreset"
    }
}
