package com.app.umma.wear.data

import android.content.Context
import android.util.Log
import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.watchbridge.contract.WatchBridgePath
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class WearBridgeRepositoryImpl(
    private val context: Context
) : WearBridgeRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeEvents(): Flow<WatchBridgeEvent> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path != WatchBridgePath.EVENT) return@OnMessageReceivedListener
            runCatching {
                json.decodeFromString(
                    WatchBridgeEvent.serializer(),
                    event.data.decodeToString()
                )
            }.onSuccess { bridgeEvent ->
                Log.d(TAG, "event received: ${bridgeEvent.summary()}")
                trySend(bridgeEvent)
            }.onFailure { error ->
                Log.e(TAG, "failed to decode phone response", error)
            }
        }

        Wearable.getMessageClient(context).addListener(listener)
        awaitClose {
            Wearable.getMessageClient(context).removeListener(listener)
        }
    }

    override suspend fun sendCommand(command: WatchBridgeCommand): Result<NodeDispatchInfo> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val node = requirePhoneNode()
                val payload = json.encodeToString(
                    WatchBridgeCommand.serializer(),
                    command
                ).encodeToByteArray()

                Log.d(
                    TAG,
                    "sending command=$command nodeId=${node.id} displayName=${node.displayName} nearby=${node.isNearby}"
                )
                Tasks.await(
                    Wearable.getMessageClient(context).sendMessage(
                        node.id,
                        WatchBridgePath.COMMAND,
                        payload
                    )
                )

                NodeDispatchInfo(
                    nodeId = node.id,
                    displayName = node.displayName,
                    isNearby = node.isNearby
                )
            }
        }
    }

    private fun requirePhoneNode(): Node {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        ?: error("No connected phone node is available.")
    }

    private fun WatchBridgeEvent.summary(): String {
        return when (this) {
            is WatchBridgeEvent.Ack ->
                "Ack(status=$status, sessionId=$activeSessionId)"

            is WatchBridgeEvent.Snapshot ->
                "Snapshot(status=$status, sessionId=$activeSessionId, errorCode=$errorCode)"
        }
    }

    private companion object {
        const val TAG = "WatchBridgeWear"
    }
}
