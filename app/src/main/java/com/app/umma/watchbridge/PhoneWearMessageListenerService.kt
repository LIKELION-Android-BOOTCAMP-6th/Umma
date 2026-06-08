package com.app.umma.watchbridge

import android.util.Log
import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgePath
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.ChannelIOException
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

    @Inject
    lateinit var runtimeCoordinator: WatchChatRuntimeCoordinator

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
                val response = commandHandler.handle(
                    command = command,
                    sourceNodeId = messageEvent.sourceNodeId
                )
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
        runtimeCoordinator.onInputChannelOpened(channel.nodeId)

        serviceScope.launch {
            val snapshot = controller.currentSnapshot()
            if (!snapshot.canAcceptPendingWatchInputChannel) {
                Log.d(
                    TAG,
                    "audio channel ignored: attached=${snapshot.watchAttached} activeInputSurface=${snapshot.activeInputSurface}"
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
                        runtimeCoordinator.noteWatchActivity()
                        if (!controller.sendAudioData(SessionOwner.WATCH, chunk)) {
                            Log.d(
                                TAG,
                                "audio chunk dropped: activeInputSurface=${controller.currentSnapshot().activeInputSurface} bytes=${chunk.size}"
                            )
                        }
                    }
                    Log.d(TAG, "audio channel consumed: nodeId=${channel.nodeId} totalBytes=$totalBytes")
                }
            }.onFailure { error ->
                if (error is ChannelIOException) {
                    Log.d(
                        TAG,
                        "watch audio channel finished with close event: nodeId=${channel.nodeId} message=${error.message}"
                    )
                } else {
                    Log.e(TAG, "Failed to consume watch audio channel", error)
                }
            }.also {
                runtimeCoordinator.onInputChannelClosed(channel.nodeId)
            }
        }
    }

    override fun onChannelClosed(
        channel: ChannelClient.Channel,
        closeReason: Int,
        appSpecificErrorCode: Int
    ) {
        super.onChannelClosed(channel, closeReason, appSpecificErrorCode)
        if (channel.path != WatchBridgePath.AUDIO_INPUT) return
        Log.d(
            TAG,
            "audio channel closed: nodeId=${channel.nodeId} reason=$closeReason code=$appSpecificErrorCode"
        )
        runtimeCoordinator.onInputChannelClosed(channel.nodeId)
    }

    private companion object {
        const val TAG = "PhoneWearMessage"
    }
}
