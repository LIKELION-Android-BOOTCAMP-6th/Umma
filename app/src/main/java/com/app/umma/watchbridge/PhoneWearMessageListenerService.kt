package com.app.umma.watchbridge

import android.util.Log
import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgePath
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedInputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhoneWearMessageListenerService : WearableListenerService() {

    @Inject
    lateinit var commandHandler: WatchBridgeCommandHandler

    @Inject
    lateinit var eventSink: WatchBridgeEventSink

    @Inject
    lateinit var controller: PhoneChatSessionController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path != WatchBridgePath.COMMAND) return
        Log.d(
            TAG,
            "command message received: nodeId=${messageEvent.sourceNodeId} bytes=${messageEvent.data.size}"
        )

        serviceScope.launch {
            runCatching {
                WatchBridgeJson.instance.decodeFromString(
                    WatchBridgeCommand.serializer(),
                    messageEvent.data.decodeToString()
                )
            }.onSuccess { command ->
                Log.d(TAG, "decoded command=$command from nodeId=${messageEvent.sourceNodeId}")
                val response = commandHandler.handle(command)
                eventSink.send(messageEvent.sourceNodeId, response)
                Log.d(TAG, "response sent to nodeId=${messageEvent.sourceNodeId}")
            }.onFailure { error ->
                Log.e(TAG, "Failed to handle watch command", error)
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        if (channel.path != WatchBridgePath.AUDIO_INPUT) return
        Log.d(TAG, "audio channel opened: nodeId=${channel.nodeId} path=${channel.path}")

        serviceScope.launch {
            if (controller.currentSnapshot().owner != SessionOwner.WATCH) {
                Log.d(
                    TAG,
                    "audio channel ignored: currentOwner=${controller.currentSnapshot().owner}"
                )
                return@launch
            }
            runCatching {
                val channelClient = Wearable.getChannelClient(this@PhoneWearMessageListenerService)
                val inputStream = Tasks.await(channelClient.getInputStream(channel))
                BufferedInputStream(inputStream).use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        val chunk = buffer.copyOf(read)
                        totalBytes += read
                        controller.sendAudioData(SessionOwner.WATCH, chunk)
                    }
                    Log.d(TAG, "audio channel consumed: nodeId=${channel.nodeId} totalBytes=$totalBytes")
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to consume watch audio channel", error)
            }
        }
    }

    private companion object {
        const val TAG = "PhoneWearMessage"
    }
}
