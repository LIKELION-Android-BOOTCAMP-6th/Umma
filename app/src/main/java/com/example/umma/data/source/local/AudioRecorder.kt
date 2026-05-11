package com.example.umma.data.source.local

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor() {

    // 오디오 레코더 규격 정의
    companion object {
        // 샘플링 레이트
        private const val SAMPLE_RATE = 16000
        // 오디오 레코더 채널
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        // 오디오 레코더 인코딩 포맷 (샘플링 레이트와 동일 규격 포맷)
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 버퍼 사이즈
        private const val BUFFER_SIZE_FACTOR = 2
    }

    // 최소 버퍼 사이즈
    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR

    // 레코딩 시작
    @SuppressLint("MissingPermission")
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    fun startRecording(): Flow<ByteArray> = flow {
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )
        // 예외처리: 마이크 초기화 실패 시
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("마이크 초기화에 실패했습니다.")
        }

        // 오디오 버퍼
        val buffer = ByteArray(minBufferSize)

        try {
            // 레코딩 시작
            audioRecord.startRecording()

            // 예외 처리: 시작 시 녹음 불가 시
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("녹음을 시작할 수 없습니다.")
            }

            // 오디오 바이트 플로우
            while (currentCoroutineContext().isActive) {
                val readBytes = audioRecord.read(
                    buffer,
                    0,
                    buffer.size
                )

                when (readBytes) {
                    AudioRecord.ERROR_INVALID_OPERATION -> throw IllegalStateException("잘못된 녹음 동작입니다.")
                    AudioRecord.ERROR_BAD_VALUE -> throw IllegalStateException("잘못된 버퍼 값입니다.")
                    AudioRecord.ERROR_DEAD_OBJECT -> throw IllegalStateException("오디오 객체가 소멸되었습니다.")
                    AudioRecord.ERROR -> throw IllegalStateException("알 수 없는 오류입니다.")
                    else -> {
                        if (readBytes > 0) {
                            emit(buffer.copyOfRange(0, readBytes))
                        }
                    }
                }

            }
        } catch (e: Exception) {
            throw e
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (e: Exception) { }

            audioRecord.release()
        }
    }.flowOn(Dispatchers.IO)
}