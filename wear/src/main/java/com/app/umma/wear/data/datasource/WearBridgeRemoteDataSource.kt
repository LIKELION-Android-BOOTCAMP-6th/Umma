package com.app.umma.wear.data.datasource

import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.wear.domain.model.WearPhoneTarget
import kotlinx.coroutines.flow.Flow

interface WearBridgeRemoteDataSource {
    fun observeBridgeEvents(): Flow<WatchBridgeEvent>
    fun observeAudioOutputEvents(): Flow<WearAudioOutputEvent>

    suspend fun listConnectedPhoneTargets(): Result<List<WearPhoneTarget>>
    suspend fun sendCommand(command: WatchBridgeCommand, nodeId: String): Result<String>
    suspend fun openInputAudioChannel(nodeId: String): Result<Unit>
    suspend fun writeInputAudioChunk(chunk: ByteArray): Result<Unit>
    suspend fun closeInputAudioChannel()
}

sealed interface WearAudioOutputEvent {
    data object Started : WearAudioOutputEvent
    data class Chunk(val bytes: ByteArray) : WearAudioOutputEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Chunk

            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }

    data object Completed : WearAudioOutputEvent
}
