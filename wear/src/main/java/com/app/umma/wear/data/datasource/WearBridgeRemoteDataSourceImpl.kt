package com.app.umma.wear.data.datasource

import android.content.Context
import android.util.Log
import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.watchbridge.contract.WatchBridgePath
import com.app.umma.wear.data.WearAudioConfig
import com.app.umma.wear.domain.model.WearPhoneTarget
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.ChannelIOException
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class WearBridgeRemoteDataSourceImpl(
    private val context: Context
) : WearBridgeRemoteDataSource {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val channelClient by lazy { Wearable.getChannelClient(context) }

    private var inputOutputStream: BufferedOutputStream? = null

    override fun observeBridgeEvents(): Flow<WatchBridgeEvent> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path != WatchBridgePath.EVENT) return@OnMessageReceivedListener
            runCatching {
                json.decodeFromString(
                    WatchBridgeEvent.serializer(),
                    event.data.decodeToString()
                )
            }.onSuccess { bridgeEvent ->
                Log.d(TAG, "bridge event received: ${bridgeEvent.summary()}")
                trySend(bridgeEvent)
            }.onFailure { error ->
                Log.e(TAG, "Failed to decode bridge event", error)
            }
        }

        messageClient.addListener(listener)
        awaitClose {
            messageClient.removeListener(listener)
        }
    }

    override fun observeAudioOutputEvents(): Flow<WearAudioOutputEvent> = callbackFlow {
        val callback = object : ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                if (channel.path != WatchBridgePath.AUDIO_OUTPUT) return
                scope.launch {
                    runCatching {
                        val inputStream = Tasks.await(channelClient.getInputStream(channel))
                        trySend(WearAudioOutputEvent.Started)
                        BufferedInputStream(inputStream).use { stream ->
                            val buffer = ByteArray(WearAudioConfig.STREAM_CHUNK_SIZE_BYTES)
                            while (true) {
                                val read = stream.read(buffer)
                                if (read <= 0) break
                                trySend(WearAudioOutputEvent.Chunk(buffer.copyOf(read)))
                            }
                        }
                    }.onFailure { error ->
                        val apiException = when (error) {
                            is ApiException -> error
                            is ExecutionException -> error.cause as? ApiException
                            else -> null
                        }
                        if (apiException?.statusCode == 10) {
                            Log.d(
                                TAG,
                                "AI audio output channel became invalid before stream attach: nodeId=${channel.nodeId} statusCode=${apiException.statusCode}"
                            )
                        } else {
                            Log.e(TAG, "Failed to consume AI audio output channel", error)
                        }
                    }
                    trySend(WearAudioOutputEvent.Completed)
                }
            }

            override fun onChannelClosed(
                channel: ChannelClient.Channel,
                closeReason: Int,
                appSpecificErrorCode: Int
            ) {
                if (channel.path != WatchBridgePath.AUDIO_OUTPUT) return
                Log.d(
                    TAG,
                    "audio output channel closed: nodeId=${channel.nodeId} reason=$closeReason code=$appSpecificErrorCode"
                )
            }
        }

        channelClient.registerChannelCallback(callback)
        awaitClose {
            channelClient.unregisterChannelCallback(callback)
        }
    }

    override suspend fun listConnectedPhoneTargets(): Result<List<WearPhoneTarget>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                Tasks.await(nodeClient.connectedNodes)
                    .map { node ->
                        WearPhoneTarget(
                            nodeId = node.id,
                            displayName = node.displayName.orEmpty().ifBlank { "이 휴대폰" },
                            isNearby = node.isNearby
                        )
                    }
                    .sortedWith(
                        compareByDescending<WearPhoneTarget> { it.isNearby }
                            .thenBy { it.displayName }
                    )
            }
        }
    }

    override suspend fun sendCommand(command: WatchBridgeCommand, nodeId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val node = requirePhoneNode(nodeId)
                val payload = json.encodeToString(
                    WatchBridgeCommand.serializer(),
                    command
                ).encodeToByteArray()
                Log.d(
                    TAG,
                    "sending command=$command nodeId=${node.id} displayName=${node.displayName} nearby=${node.isNearby}"
                )
                Tasks.await(messageClient.sendMessage(node.id, WatchBridgePath.COMMAND, payload))
                node.id
            }
        }
    }

    override suspend fun openInputAudioChannel(nodeId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                closeInputAudioChannel()
                val channel = Tasks.await(channelClient.openChannel(nodeId, WatchBridgePath.AUDIO_INPUT))
                val outputStream = Tasks.await(channelClient.getOutputStream(channel))
                inputOutputStream = BufferedOutputStream(outputStream)
                Log.d(TAG, "audio input channel opened: nodeId=$nodeId")
                Unit
            }
        }
    }

    override suspend fun writeInputAudioChunk(chunk: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val output = checkNotNull(inputOutputStream) { "Input audio channel is not open." }
                output.write(chunk)
                output.flush()
            }.onFailure { error ->
                if (error is ChannelIOException) {
                    Log.d(TAG, "watch input audio channel closed while writing; ending stream", error)
                    inputOutputStream = null
                }
            }
        }
    }

    override suspend fun closeInputAudioChannel() {
        withContext(Dispatchers.IO) {
            runCatching { inputOutputStream?.close() }
            inputOutputStream = null
        }
    }

    private fun requirePhoneNode(nodeId: String): Node {
        val nodes = Tasks.await(nodeClient.connectedNodes)
        return nodes.firstOrNull { it.id == nodeId }
            ?: error("Selected phone node is not connected.")
    }

    private fun WatchBridgeEvent.summary(): String {
        return when (this) {
            is WatchBridgeEvent.Ack -> "Ack(status=$status, sessionId=$activeSessionId)"
            is WatchBridgeEvent.Snapshot ->
                "Snapshot(status=$status, sessionId=$activeSessionId, errorCode=$errorCode)"
        }
    }

    private companion object {
        const val TAG = "WearBridgeDataSource"
    }
}
