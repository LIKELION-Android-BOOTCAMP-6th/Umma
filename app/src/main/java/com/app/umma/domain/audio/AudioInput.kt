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
     * Requests the currently active recording session to stop.
     *
     * Implementations should terminate the active recording loop promptly and
     * release underlying resources from their own lifecycle cleanup.
     */
    fun stopRecording()
}
