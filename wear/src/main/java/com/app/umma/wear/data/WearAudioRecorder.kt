package com.app.umma.wear.data

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import com.app.umma.wear.domain.audio.WearAudioInput

class WearAudioRecorder : WearAudioInput {
    private val minBufferSize = AudioRecord.getMinBufferSize(
        WearAudioConfig.INPUT_SAMPLE_RATE_HZ,
        WearAudioConfig.INPUT_CHANNEL_MASK,
        WearAudioConfig.PCM_ENCODING
    ) * WearAudioConfig.RECORD_BUFFER_SIZE_FACTOR

    @Volatile
    private var activeAudioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    override fun startRecording(): Flow<ByteArray> = flow {
        val bufferSize = minBufferSize.coerceAtLeast(WearAudioConfig.STREAM_CHUNK_SIZE_BYTES)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            WearAudioConfig.INPUT_SAMPLE_RATE_HZ,
            WearAudioConfig.INPUT_CHANNEL_MASK,
            WearAudioConfig.PCM_ENCODING,
            bufferSize
        )
        activeAudioRecord = recorder

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            activeAudioRecord = null
            releaseAudioRecord(recorder)
            throw IllegalStateException("Watch microphone initialization failed.")
        }

        val buffer = ByteArray(bufferSize)

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Watch microphone recording did not start.")
            }

            while (currentCoroutineContext().isActive && activeAudioRecord === recorder) {
                val read = recorder.read(buffer, 0, buffer.size)
                when (read) {
                    AudioRecord.ERROR_INVALID_OPERATION ->
                        throw IllegalStateException("Invalid watch recording operation.")

                    AudioRecord.ERROR_BAD_VALUE ->
                        throw IllegalStateException("Invalid watch recording buffer value.")

                    AudioRecord.ERROR_DEAD_OBJECT ->
                        throw IllegalStateException("Watch microphone recorder is no longer available.")

                    AudioRecord.ERROR ->
                        throw IllegalStateException("Unknown watch microphone error.")

                    else -> if (read > 0) emit(buffer.copyOfRange(0, read))
                }
            }
        } finally {
            if (activeAudioRecord === recorder) {
                activeAudioRecord = null
            }
            releaseAudioRecord(recorder)
        }
    }.flowOn(Dispatchers.IO)

    override fun stopRecording() {
        val recorder = activeAudioRecord ?: return
        activeAudioRecord = null
        stopAudioRecord(recorder)
    }

    fun release() {
        stopRecording()
    }

    private fun stopAudioRecord(audioRecord: AudioRecord) {
        try {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to stop wear audio recorder", error)
        }
    }

    private fun releaseAudioRecord(audioRecord: AudioRecord) {
        stopAudioRecord(audioRecord)
        runCatching { audioRecord.release() }
            .onFailure { error ->
                Log.e(TAG, "Failed to release wear audio recorder", error)
            }
    }

    private companion object {
        const val TAG = "WearAudioRecorder"
    }
}
