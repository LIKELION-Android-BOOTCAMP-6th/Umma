package com.app.umma.wear.data

import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import kotlinx.coroutines.flow.Flow

interface WearBridgeRepository {
    fun observeEvents(): Flow<WatchBridgeEvent>

    suspend fun sendCommand(command: WatchBridgeCommand): Result<NodeDispatchInfo>
}

data class NodeDispatchInfo(
    val nodeId: String,
    val displayName: String,
    val isNearby: Boolean
)
