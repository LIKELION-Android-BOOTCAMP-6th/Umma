package com.app.umma.domain.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * 오디오 출력 재생기 인터페이스입니다.
 */
interface AudioOutput {
    val outputLevel: StateFlow<Float>
    val isPlaying: StateFlow<Boolean>

    fun startPlaying()

    fun playAudioChunk(audio: ByteArray)

    fun consumeLastPlaybackDurationMs(): Long?

    fun stopPlaying()

    fun release()
}
