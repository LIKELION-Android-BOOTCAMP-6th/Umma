package com.app.umma.data.source.local

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.app.umma.domain.audio.AudioInput
import com.app.umma.domain.model.audio.AudioInputFrame
import com.app.umma.presentation.util.calculateLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Microphone recorder that emits PCM frames for live chat.
 */
@Singleton
class AudioRecorder @Inject constructor() : AudioInput {

    private companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR

    @Volatile
    private var activeAudioRecord: AudioRecord? = null

    /**
     * Starts recording from the microphone.
     *
     * Throws if the microphone cannot be initialized or started.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission("android.permission.RECORD_AUDIO")
    override fun startRecording(): Flow<AudioInputFrame> = flow {
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )
        activeAudioRecord = audioRecord

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            activeAudioRecord = null
            releaseAudioRecord(audioRecord)
            throw IllegalStateException("Microphone initialization failed.")
        }

        val buffer = ByteArray(minBufferSize)

        try {
            audioRecord.startRecording()

            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Microphone recording did not start.")
            }

            while (currentCoroutineContext().isActive) {
                val readBytes = audioRecord.read(
                    buffer,
                    0,
                    buffer.size
                )

                when (readBytes) {
                    AudioRecord.ERROR_INVALID_OPERATION ->
                        throw IllegalStateException("Invalid recording operation.")

                    AudioRecord.ERROR_BAD_VALUE ->
                        throw IllegalStateException("Invalid recording buffer value.")

                    AudioRecord.ERROR_DEAD_OBJECT ->
                        throw IllegalStateException("Microphone recorder is no longer available.")

                    AudioRecord.ERROR ->
                        throw IllegalStateException("Unknown microphone error.")

                    else -> {
                        if (readBytes > 0) {
                            val chunk = buffer.copyOfRange(0, readBytes)
                            emit(
                                AudioInputFrame(
                                    pcm = chunk,
                                    level = calculateLevel(chunk)
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            if (activeAudioRecord === audioRecord) {
                activeAudioRecord = null
            }
            releaseAudioRecord(audioRecord)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stops the active microphone recording immediately.
     */
    override fun stopRecording() {
        val audioRecord = activeAudioRecord ?: return
        activeAudioRecord = null
        releaseAudioRecord(audioRecord)
    }

    /**
     * Stops and releases the given [audioRecord] safely.
     */
    private fun releaseAudioRecord(audioRecord: AudioRecord) {
        try {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
        } catch (_: Exception) {
        }

        try {
            audioRecord.release()
        } catch (_: Exception) {
        }
    }
}
