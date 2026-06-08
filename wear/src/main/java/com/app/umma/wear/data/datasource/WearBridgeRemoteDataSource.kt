package com.app.umma.wear.data.datasource

import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import kotlinx.coroutines.flow.Flow

interface WearBridgeRemoteDataSource {
    fun observeBridgeEvents(): Flow<WatchBridgeEvent>
    fun observeAudioOutputEvents(): Flow<WearAudioOutputEvent>

    suspend fun sendCommand(command: WatchBridgeCommand): Result<String>
    suspend fun openInputAudioChannel(nodeId: String): Result<Unit>
    suspend fun writeInputAudioChunk(chunk: ByteArray): Result<Unit>
    suspend fun closeInputAudioChannel()
}

sealed interface WearAudioOutputEvent {
    data object Started : WearAudioOutputEvent
    data class Chunk(val bytes: ByteArray) : WearAudioOutputEvent
    data object Completed : WearAudioOutputEvent
}
