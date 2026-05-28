package com.app.umma.domain.audio

import com.app.umma.domain.model.audio.AudioInputFrame
import kotlinx.coroutines.flow.Flow

/**
 * Contract for microphone input.
 */
interface AudioInput {

    /**
     * Starts recording and emits PCM frames until the caller stops or cancels it.
     */
    fun startRecording(): Flow<AudioInputFrame>

    /**
     * Stops the currently active recording session immediately.
     */
    fun stopRecording()
}
