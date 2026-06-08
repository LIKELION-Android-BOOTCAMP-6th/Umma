package com.app.umma.watchbridge

import android.content.Context
import android.util.Log
import com.app.umma.di.ApplicationScope
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.watchbridge.contract.WatchBridgePath
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class WatchAudioOutputRelay @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val controller: PhoneChatSessionController,
    private val connectionStore: ActiveWatchConnectionStore,
    private val replayStore: WatchReplayAudioStore
) {
    private var currentNodeId: String? = null
    private var currentChannel: ChannelClient.Channel? = null
    private var currentOutput: BufferedOutputStream? = null
    private var isSpeakingResponseOpen: Boolean = false
    private var hasRetriedCurrentResponse = false

    init {
        applicationScope.launch {
            controller.observeAIEvents().collect(::handleEvent)
        }
    }

    suspend fun replayToWatch(): Boolean {
        val audio = replayStore.getLastReplayAudio()
        val nodeId = connectionStore.state.value.nodeId
        if (audio.isEmpty() || nodeId.isNullOrBlank()) return false
        return runCatching {
            openOutputChannel(nodeId)
            currentOutput?.write(audio)
            currentOutput?.flush()
            finalizeCurrentOutput()
            connectionStore.noteActivity()
            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to replay AI audio to watch", error)
            closeCurrentOutput()
            false
        }
    }

    fun clear() {
        replayStore.clear()
        closeCurrentOutput()
        isSpeakingResponseOpen = false
        hasRetriedCurrentResponse = false
    }

    private suspend fun handleEvent(event: AIEvent) {
        when (event) {
            AIEvent.Initializing -> clear()
            is AIEvent.StateChanged -> handleStateChanged(event.state)
            is AIEvent.AudioResponse -> handleAudioChunk(event.audio)
            is AIEvent.Error,
            is AIEvent.ReconnectFailed,
            is AIEvent.SessionInterrupted -> finalizeResponse()
            else -> Unit
        }
    }

    private suspend fun handleStateChanged(state: AIState) {
        if (state == AIState.SPEAKING) {
            if (!isSpeakingResponseOpen) {
                replayStore.beginResponse()
                isSpeakingResponseOpen = true
                hasRetriedCurrentResponse = false
            }
            ensureOutputChannelOpened()
        } else if (state != AIState.SPEAKING && isSpeakingResponseOpen) {
            finalizeResponse()
        }
    }

    private fun handleAudioChunk(audio: ByteArray) {
        if (!isSpeakingResponseOpen) {
            replayStore.beginResponse()
            isSpeakingResponseOpen = true
        }
        ensureOutputChannelOpened()
        replayStore.append(audio)
        runCatching {
            currentOutput?.write(audio)
            currentOutput?.flush()
            connectionStore.noteActivity()
        }.onFailure { error ->
            if (!retryStreamingChunk(audio, error)) {
                Log.e(TAG, "Failed to stream AI audio chunk to watch", error)
                closeCurrentOutput()
            }
        }
    }

    private fun ensureOutputChannelOpened() {
        if (currentOutput != null) return
        connectionStore.state.value.nodeId?.let { nodeId ->
            runCatching { openOutputChannel(nodeId) }
                .onFailure { error ->
                    if (!retryOpenOutputChannel(nodeId, error)) {
                        Log.e(TAG, "Failed to open watch audio output channel", error)
                        closeCurrentOutput()
                    }
                }
        }
    }

    private fun finalizeResponse() {
        if (!isSpeakingResponseOpen) return
        replayStore.finalizeResponse()
        closeCurrentOutput()
        isSpeakingResponseOpen = false
        hasRetriedCurrentResponse = false
    }

    @Throws(IOException::class)
    private fun openOutputChannel(nodeId: String) {
        if (currentNodeId == nodeId && currentChannel != null && currentOutput != null) return
        closeCurrentOutput()
        val channelClient = Wearable.getChannelClient(context)
        val channel = Tasks.await(channelClient.openChannel(nodeId, WatchBridgePath.AUDIO_OUTPUT))
        val output = Tasks.await(channelClient.getOutputStream(channel))
        currentNodeId = nodeId
        currentChannel = channel
        currentOutput = BufferedOutputStream(output)
        Log.d(TAG, "watch audio output channel opened: nodeId=$nodeId")
    }

    private fun closeCurrentOutput() {
        closeQuietly(currentOutput)
        currentOutput = null
        currentChannel = null
        currentNodeId = null
    }

    private fun finalizeCurrentOutput() {
        closeQuietly(currentOutput)
        currentOutput = null
        currentChannel = null
        currentNodeId = null
    }

    private fun closeQuietly(closeable: Closeable?) {
        runCatching { closeable?.close() }
    }

    private fun retryOpenOutputChannel(nodeId: String, error: Throwable): Boolean {
        if (hasRetriedCurrentResponse) {
            return false
        }
        hasRetriedCurrentResponse = true
        Log.w(TAG, "Retrying watch audio output channel open after failure", error)
        return runCatching {
            closeCurrentOutput()
            openOutputChannel(nodeId)
        }.isSuccess
    }

    private fun retryStreamingChunk(audio: ByteArray, error: Throwable): Boolean {
        if (hasRetriedCurrentResponse) {
            return false
        }
        val nodeId = connectionStore.state.value.nodeId ?: return false
        hasRetriedCurrentResponse = true
        Log.w(TAG, "Retrying watch audio chunk stream after failure", error)
        return runCatching {
            closeCurrentOutput()
            openOutputChannel(nodeId)
            currentOutput?.write(audio)
            currentOutput?.flush()
            connectionStore.noteActivity()
        }.isSuccess
    }

    private companion object {
        const val TAG = "WatchAudioRelay"
    }
}
