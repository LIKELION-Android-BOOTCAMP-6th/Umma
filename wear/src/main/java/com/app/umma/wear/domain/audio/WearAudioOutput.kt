package com.app.umma.wear.domain.audio

import kotlinx.coroutines.flow.StateFlow

interface WearAudioOutput {
    val isPlaying: StateFlow<Boolean>

    fun startPlaying()
    fun playAudioChunk(audio: ByteArray)
    fun finishPlaybackResponse()
    fun stopPlaying()
    fun release()
}
