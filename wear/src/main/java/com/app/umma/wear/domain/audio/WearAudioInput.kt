package com.app.umma.wear.domain.audio

import kotlinx.coroutines.flow.Flow

interface WearAudioInput {
    fun startRecording(): Flow<ByteArray>
    fun stopRecording()
}
