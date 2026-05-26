package com.app.umma.data.source.local

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.app.umma.domain.audio.AudioOutput
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@Singleton
class AudioPlayer @Inject constructor() : AudioOutput {


    // 오디오 플레이어 규격 정의
    companion object {
        // 샘플링 레이트
        private const val SAMPLE_RATE = 24000
        // 재생 오디오 채널
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        // 오디오 포맷 (샘플링 레이트와 동일한 규격 포맷)
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 버퍼 사이즈
        private const val BUFFER_SIZE_FACTOR = 2
    }

    // 오디오 트랙 정의
    private var audioTrack: AudioTrack? = null
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null
    private val audioQueue = Channel<ByteArray>(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // 초기화
    init {
        // 최소 버퍼 사이즈
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR

        // 오디오 트랙 설정
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    // 오디오 트랙에 삽입된 데이터 재생
    override fun startPlaying() {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }
            startPlaybackWorkerIfNeeded()
        }
    }

    // 오디오 트랙에 데이터 삽입
    override fun playAudioChunk(audio: ByteArray) {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED &&
            audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING
        ) {
            audioTrack?.play()
            startPlaybackWorkerIfNeeded()
        }
        audioQueue.trySend(audio.copyOf()).isSuccess
    }


    // 재생 멈춤
    override fun stopPlaying() {
        playbackJob?.cancel()
        playbackJob = null
        clearAudioQueue()
        audioTrack?.stop()
        audioTrack?.flush()
    }

    // 메모리 릴리즈
    override fun release() {
        playbackJob?.cancel()
        playbackJob = null
        clearAudioQueue()
        playerScope.cancel()
        audioTrack?.release()
        audioTrack = null
    }

    private fun startPlaybackWorkerIfNeeded() {
        if (playbackJob?.isActive == true) return

        playbackJob = playerScope.launch {
            for (chunk in audioQueue) {
                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }
                audioTrack?.write(chunk, 0, chunk.size)
            }
        }
    }

    private fun clearAudioQueue() {
        while (true) {
            val result = audioQueue.tryReceive()
            if (result.isFailure) break
        }
    }

}
