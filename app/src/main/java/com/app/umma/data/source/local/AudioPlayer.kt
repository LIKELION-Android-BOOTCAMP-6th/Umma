package com.app.umma.data.source.local

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.app.umma.domain.audio.AudioOutput
import com.app.umma.presentation.util.calculateLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AudioPlayer @Inject constructor() : AudioOutput {

    companion object {
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val BYTES_PER_SAMPLE = 2
    }

    private var audioTrack: AudioTrack? = null
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null
    private val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private var pendingChunkCount: Int = 0
    private var currentPlaybackStartedAtMs: Long? = null
    private var currentPlaybackEndedAtMs: Long? = null
    private val audioQueue = Channel<ByteArray>(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
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

    override fun startPlaying() {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }
            startPlaybackWorkerIfNeeded()
        }
    }

    override fun playAudioChunk(audio: ByteArray) {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED &&
            audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING
        ) {
            audioTrack?.play()
            startPlaybackWorkerIfNeeded()
        }
        if (audioQueue.trySend(audio.copyOf()).isSuccess) {
            pendingChunkCount += 1
            // 서버 response.done은 로컬 스피커 재생 완료보다 먼저 올 수 있다.
            // UI 입력 방어는 실제 출력 큐에 남은 오디오를 기준으로 해야 하므로 여기서 재생 중 상태를 올린다.
            _isPlaying.value = true
        }
    }

    override fun consumeLastPlaybackDurationMs(): Long? {
        val startedAt = currentPlaybackStartedAtMs ?: return null
        val endedAt = currentPlaybackEndedAtMs ?: return null
        currentPlaybackStartedAtMs = null
        currentPlaybackEndedAtMs = null
        return (endedAt - startedAt).coerceAtLeast(0L)
    }

    override fun stopPlaying() {
        playbackJob?.cancel()
        playbackJob = null
        clearAudioQueue()
        _outputLevel.value = 0f
        _isPlaying.value = false
        resetPlaybackMetrics()
        audioTrack?.stop()
        audioTrack?.flush()
    }

    override fun release() {
        playbackJob?.cancel()
        playbackJob = null
        clearAudioQueue()
        _outputLevel.value = 0f
        _isPlaying.value = false
        resetPlaybackMetrics()
        playerScope.cancel()
        audioTrack?.release()
        audioTrack = null
    }

    private fun startPlaybackWorkerIfNeeded() {
        if (playbackJob?.isActive == true) return

        playbackJob = playerScope.launch {
            for (chunk in audioQueue) {
                if (currentPlaybackStartedAtMs == null) {
                    currentPlaybackStartedAtMs = System.currentTimeMillis()
                    currentPlaybackEndedAtMs = null
                }
                _outputLevel.value = calculateLevel(chunk)
                pendingChunkCount = (pendingChunkCount - 1).coerceAtLeast(0)

                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }

                audioTrack?.write(chunk, 0, chunk.size)

                if (pendingChunkCount == 0) {
                    delay(calculateChunkDurationMs(chunk.size))
                    if (pendingChunkCount == 0) {
                        currentPlaybackEndedAtMs = System.currentTimeMillis()
                        _outputLevel.value = 0f
                        // 마지막 chunk의 실제 재생 시간이 지난 뒤에야 입력 가능 상태로 돌린다.
                        // 이 값이 false가 되기 전까지 ChatViewModel은 마이크를 비활성화한다.
                        _isPlaying.value = false
                    }
                }
            }
        }
    }

    private fun clearAudioQueue() {
        while (true) {
            val result = audioQueue.tryReceive()
            if (result.isFailure) break
            pendingChunkCount = (pendingChunkCount - 1).coerceAtLeast(0)
        }
    }

    private fun calculateChunkDurationMs(byteSize: Int): Long {
        val sampleCount = byteSize / BYTES_PER_SAMPLE
        if (sampleCount <= 0) return 0L
        return ((sampleCount * 1000L) / SAMPLE_RATE).coerceAtLeast(1L)
    }

    private fun resetPlaybackMetrics() {
        currentPlaybackStartedAtMs = null
        currentPlaybackEndedAtMs = null
    }
}
