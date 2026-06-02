package com.app.umma.data.source.local

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.app.umma.domain.audio.AudioOutput
import com.app.umma.presentation.util.calculateLevel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * OpenAI Realtime 이 내려주는 PCM16 mono audio chunk 를 로컬 스피커로 이어 붙여 재생합니다.
 *
 * Realtime 응답은 하나의 완성된 오디오 파일이 아니라 짧은 chunk 스트림이므로,
 * 이 클래스의 핵심 책임은 "수신된 chunk 를 버리지 않고 순서대로 끝까지 쓰는 것"입니다.
 */
@Singleton
class AudioPlayer @Inject constructor() : AudioOutput {

    companion object {
        private const val TAG = "AiAudioPlayback"
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 4
        private const val BYTES_PER_SAMPLE = 2
        private const val PREBUFFER_CHUNK_COUNT = 2
        private const val PREBUFFER_MAX_WAIT_MS = 120L
        private const val WRITE_RETRY_DELAY_MS = 2L
        private const val PLAYBACK_DRAIN_WAIT_STEP_MS = 10L
        private const val PLAYBACK_DRAIN_EXTRA_GUARD_MS = 20L
        private const val SILENCE_PADDING_MS = 160L
        private const val ZERO_WRITE_MAX_RETRY_COUNT = 20
        private const val PENDING_CHUNK_WARNING_THRESHOLD = 128
    }

    private var audioTrack: AudioTrack? = null
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null
    private val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val pendingChunkCount = AtomicInteger(0)
    private val enqueuedChunkCount = AtomicLong(0L)
    private val playedChunkCount = AtomicLong(0L)
    private val failedWriteCount = AtomicLong(0L)
    private val submittedFrameCount = AtomicLong(0L)
    private var currentPlaybackStartedAtMs: Long? = null
    private var currentPlaybackEndedAtMs: Long? = null
    private var lastLoggedUnderrunCount: Int = 0
    private val audioQueue = Channel<ByteArray>(
        capacity = Channel.UNLIMITED
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

        logAudioTrackConfig(minBufferSize)
    }

    override fun startPlaying() {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            // 실제 play()는 첫 chunk 직후가 아니라 pre-buffer 조건을 만족한 뒤 시작한다.
            // 응답 초반부에서 AudioTrack 버퍼가 비어 생기는 underrun 가능성을 줄이기 위함이다.
            startPlaybackWorkerIfNeeded()
        }
    }

    override fun playAudioChunk(audio: ByteArray) {
        val track = audioTrack
        if (track?.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "chunk ignored: track is not initialized, bytes=${audio.size}")
            return
        }

        // Channel.UNLIMITED 를 쓰는 이유는 음성 일부를 조용히 버리는 DROP_OLDEST 정책이
        // 실제 사용자에게 "단어가 빠진 음성"으로 들릴 수 있기 때문이다.
        val pendingAfterEnqueue = pendingChunkCount.incrementAndGet()
        val enqueueResult = audioQueue.trySend(audio.copyOf())
        if (enqueueResult.isSuccess) {
            val enqueued = enqueuedChunkCount.incrementAndGet()
            if (pendingAfterEnqueue >= PENDING_CHUNK_WARNING_THRESHOLD) {
                Log.w(
                    TAG,
                    "queue backlog: pending=$pendingAfterEnqueue, enqueued=$enqueued"
                )
            }
            Log.d(
                TAG,
                "chunk enqueued: index=$enqueued, bytes=${audio.size}, pending=$pendingAfterEnqueue"
            )
        } else {
            val pendingAfterRollback = decrementPendingChunkCount()
            Log.w(
                TAG,
                "chunk enqueue failed: bytes=${audio.size}, pending=$pendingAfterRollback"
            )
            return
        }

        // startPlaying()이 세션 시작 시 호출되지만, stop/restart 경계에서 worker가 죽어 있을 수 있다.
        // chunk를 받은 시점에도 한 번 더 확인해 큐에 쌓인 음성이 방치되지 않게 한다.
        startPlaybackWorkerIfNeeded()

        // 서버 response.done은 로컬 스피커 재생 완료보다 먼저 올 수 있다.
        // UI 입력 방어는 실제 출력 큐에 남은 오디오를 기준으로 해야 하므로 여기서 재생 중 상태를 올린다.
        _isPlaying.value = true
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
            try {
                for (chunk in audioQueue) {
                    if (currentPlaybackStartedAtMs == null) {
                        currentPlaybackStartedAtMs = System.currentTimeMillis()
                        currentPlaybackEndedAtMs = null
                    }
                    _outputLevel.value = calculateLevel(chunk)

                    val shouldStartPlayback =
                        audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING
                    if (shouldStartPlayback) {
                        waitForPrebuffer()
                        audioTrack?.play()
                        Log.d(
                            TAG,
                            "playback started: pending=${pendingChunkCount.get()}, underruns=${getUnderrunCountCompat()}"
                        )
                    }

                    // Android 기기별 AudioTrack 구현은 play 전 write에서 0 byte를 반복 반환할 수 있다.
                    // 따라서 pre-buffer로 앱 큐를 먼저 채운 뒤, play 상태에서 chunk를 끝까지 write한다.
                    val writeSucceeded = writeChunkFully(chunk)
                    val submittedFramesAfterChunk = if (writeSucceeded) {
                        submittedFrameCount.addAndGet(calculateFrameCount(chunk.size))
                    } else {
                        submittedFrameCount.get()
                    }
                    logUnderrunIfChanged()
                    val pendingAfterPlayback = decrementPendingChunkCount()
                    val played = playedChunkCount.incrementAndGet()
                    if (writeSucceeded) {
                        Log.d(
                            TAG,
                            "chunk played: index=$played, bytes=${chunk.size}, pending=$pendingAfterPlayback"
                        )
                    } else {
                        Log.w(
                            TAG,
                            "chunk playback incomplete: index=$played, bytes=${chunk.size}, pending=$pendingAfterPlayback"
                        )
                    }

                    if (pendingAfterPlayback == 0) {
                        val submittedFramesAfterPadding = writeSilencePaddingAfterLastChunk(
                            submittedFramesAfterChunk
                        )
                        waitUntilSubmittedFramesArePlayed(submittedFramesAfterPadding)
                        if (pendingChunkCount.get() == 0) {
                            currentPlaybackEndedAtMs = System.currentTimeMillis()
                            _outputLevel.value = 0f
                            // 마지막 chunk의 실제 재생 시간이 지난 뒤에야 입력 가능 상태로 돌린다.
                            // 이 값이 false가 되기 전까지 ChatViewModel은 마이크를 비활성화한다.
                            _isPlaying.value = false
                            pausePlaybackAfterDrain()
                            Log.d(
                                TAG,
                                "playback drained: enqueued=${enqueuedChunkCount.get()}, played=${playedChunkCount.get()}, failedWrites=${failedWriteCount.get()}"
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                // stop/release 는 정상 종료 경로이므로 호출자에게 cancellation 을 그대로 전파한다.
                throw error
            } catch (error: Throwable) {
                Log.e(
                    TAG,
                    "playback worker failed: type=${error::class.java.simpleName}, message=${error.message}",
                    error
                )
            } finally {
                // AudioTrack write/drain 경계에서 예외가 나도 UI가 영구 SPEAKING 상태에 머무르면 안 된다.
                // 따라서 worker 종료 경계에서는 출력 레벨과 playing flag 를 항상 안전 상태로 내린다.
                if (currentPlaybackStartedAtMs != null && currentPlaybackEndedAtMs == null) {
                    currentPlaybackEndedAtMs = System.currentTimeMillis()
                }
                _outputLevel.value = 0f
                _isPlaying.value = false
            }
        }
    }

    /**
     * AudioTrack.write()는 요청한 byte 전체를 항상 기록한다고 보장하지 않는다.
     *
     * 반환값이 요청 크기보다 작으면 그 뒤 PCM byte를 버리는 결과가 되므로,
     * 실제로 기록된 byte 수를 누적하면서 chunk 끝까지 반복 기록한다.
     */
    private suspend fun writeChunkFully(chunk: ByteArray): Boolean {
        val track = audioTrack ?: return false
        var writtenTotal = 0
        var zeroWriteCount = 0

        while (writtenTotal < chunk.size) {
            val writeResult = track.write(
                chunk,
                writtenTotal,
                chunk.size - writtenTotal
            )

            when {
                writeResult > 0 -> {
                    writtenTotal += writeResult
                    if (writtenTotal < chunk.size) {
                        Log.d(
                            TAG,
                            "partial write: written=$writeResult, remaining=${chunk.size - writtenTotal}"
                        )
                    }
                }

                writeResult == 0 -> {
                    zeroWriteCount += 1
                    // 0 byte write는 즉시 실패는 아니지만 반복되면 재생 경계가 막힌 상태다.
                    // 매 반복마다 로그를 찍으면 Logcat이 폭주하므로 첫 회와 포기 시점만 남긴다.
                    if (zeroWriteCount == 1) {
                        Log.d(
                            TAG,
                            "zero-byte write: remaining=${chunk.size - writtenTotal}"
                        )
                    }
                    if (zeroWriteCount >= ZERO_WRITE_MAX_RETRY_COUNT) {
                        failedWriteCount.incrementAndGet()
                        Log.w(
                            TAG,
                            "zero-byte write aborted: retries=$zeroWriteCount, written=$writtenTotal, total=${chunk.size}"
                        )
                        return false
                    }
                    delay(WRITE_RETRY_DELAY_MS)
                }

                else -> {
                    failedWriteCount.incrementAndGet()
                    Log.w(
                        TAG,
                        "write failed: code=$writeResult, written=$writtenTotal, total=${chunk.size}"
                    )
                    return false
                }
            }
        }

        return true
    }

    private fun clearAudioQueue() {
        while (true) {
            val result = audioQueue.tryReceive()
            if (result.isFailure) break
            decrementPendingChunkCount()
        }
    }

    /**
     * 첫 chunk가 오자마자 play()를 호출하면 AudioTrack이 충분히 채워지기 전에 소비를 시작할 수 있다.
     *
     * 응답 초반부의 "말이 점프하는 느낌"은 underrun과 잘 맞기 때문에,
     * 최소 chunk가 쌓일 때까지 아주 짧게 기다린 뒤 재생을 시작한다.
     */
    private suspend fun waitForPrebuffer() {
        val startedAt = System.currentTimeMillis()
        while (pendingChunkCount.get() < PREBUFFER_CHUNK_COUNT) {
            if (System.currentTimeMillis() - startedAt >= PREBUFFER_MAX_WAIT_MS) {
                Log.d(
                    TAG,
                    "prebuffer timeout: pending=${pendingChunkCount.get()}, waitedMs=$PREBUFFER_MAX_WAIT_MS"
                )
                return
            }
            delay(WRITE_RETRY_DELAY_MS)
        }
    }

    /**
     * pending count는 UI의 "아직 로컬 스피커 출력이 남았는지" 판단과 연결된다.
     *
     * enqueue 실패 rollback, queue clear, playback 완료가 서로 다른 타이밍에서 호출될 수 있으므로
     * 음수로 내려가지 않게 compare-and-set으로 안전하게 줄인다.
     */
    private fun decrementPendingChunkCount(): Int {
        while (true) {
            val current = pendingChunkCount.get()
            val next = (current - 1).coerceAtLeast(0)
            if (pendingChunkCount.compareAndSet(current, next)) {
                return next
            }
        }
    }

    private fun calculateChunkDurationMs(byteSize: Int): Long {
        val sampleCount = calculateFrameCount(byteSize)
        if (sampleCount <= 0) return 0L
        return ((sampleCount * 1000L) / SAMPLE_RATE).coerceAtLeast(1L)
    }

    private fun calculateFrameCount(byteSize: Int): Long {
        return (byteSize / BYTES_PER_SAMPLE).toLong()
    }

    /**
     * 마지막 음성 chunk 직후에 바로 빈 PLAYING 상태로 들어가면 AudioTrack underrun 이 발생할 수 있다.
     *
     * 말끝 보호용 대기 시간을 단순 delay 로 두지 않고 짧은 0 PCM 으로 채우면,
     * AudioTrack 이 실제로 소비할 buffer 를 확보한 상태에서 자연스럽게 응답을 닫을 수 있다.
     */
    private suspend fun writeSilencePaddingAfterLastChunk(currentSubmittedFrames: Long): Long {
        val silencePadding = createSilencePadding()
        val writeSucceeded = writeChunkFully(silencePadding)
        if (!writeSucceeded) {
            return currentSubmittedFrames
        }

        val submittedFramesAfterPadding = submittedFrameCount.addAndGet(
            calculateFrameCount(silencePadding.size)
        )
        Log.d(
            TAG,
            "silence padding written: bytes=${silencePadding.size}, targetFrames=$submittedFramesAfterPadding"
        )
        return submittedFramesAfterPadding
    }

    private fun createSilencePadding(): ByteArray {
        val paddingFrameCount = SAMPLE_RATE * SILENCE_PADDING_MS / 1000
        return ByteArray((paddingFrameCount * BYTES_PER_SAMPLE).toInt())
    }

    /**
     * 마지막 chunk write 완료 시점은 "스피커 출력 완료"가 아니라 "AudioTrack 내부 버퍼 제출 완료"에 가깝다.
     *
     * 기존처럼 chunk 길이만큼 단순 delay 하면 기기별 buffer / scheduler 오차에 의해 말끝이 살짝 잘릴 수 있다.
     * 그래서 AudioTrack 이 실제로 소비한 frame 위치를 확인하고, 제출한 마지막 frame 근처까지 도달한 뒤 종료한다.
     */
    private suspend fun waitUntilSubmittedFramesArePlayed(targetSubmittedFrames: Long) {
        val track = audioTrack ?: return
        val maxWaitMs = calculateChunkDurationMs(
            (targetSubmittedFrames - track.playbackHeadPosition.toLong())
                .coerceAtLeast(0L)
                .coerceAtMost(SAMPLE_RATE.toLong())
                .toInt() * BYTES_PER_SAMPLE
        ) + PLAYBACK_DRAIN_EXTRA_GUARD_MS
        val startedAt = System.currentTimeMillis()

        while (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            val playedFrames = track.playbackHeadPosition.toLong()
            if (playedFrames >= targetSubmittedFrames) {
                delay(PLAYBACK_DRAIN_EXTRA_GUARD_MS)
                return
            }
            if (System.currentTimeMillis() - startedAt >= maxWaitMs) {
                Log.d(
                    TAG,
                    "drain wait timeout: playedFrames=$playedFrames, targetFrames=$targetSubmittedFrames, waitedMs=$maxWaitMs"
                )
                return
            }
            delay(PLAYBACK_DRAIN_WAIT_STEP_MS)
        }
    }

    /**
     * 응답 하나가 끝난 뒤 AudioTrack 을 계속 PLAYING 으로 두면 빈 stream buffer 를 소비한다.
     *
     * 다음 응답의 첫 chunk 가 도착하기 전까지 빈 버퍼가 재생되면 Android 는 underrun 을 기록하고,
     * 실제 청감상으로는 응답 초반 음성이 살짝 점프하는 것처럼 들릴 수 있다.
     * 그래서 응답 단위가 drain 되면 재생을 멈추고 다음 chunk 에서 다시 pre-buffer 후 play 한다.
     *
     * 여기서는 flush()를 호출하지 않는다. 응답 종료 직후 flush()는 기기별로 남은 PCM 경계를
     * 강제로 끊어 작은 pop/click 사운드를 만들 수 있으므로, 명시적 stop/release 경계에서만 사용한다.
     */
    private fun pausePlaybackAfterDrain() {
        val track = audioTrack ?: return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            val underrunCount = getUnderrunCountCompat()
            lastLoggedUnderrunCount = underrunCount
            Log.d(
                TAG,
                "playback paused after drain: underruns=$underrunCount"
            )
        }
    }

    /**
     * AudioTrack 설정값을 남겨 실제 기기에서 buffer가 얼마나 확보됐는지 확인한다.
     *
     * Android 문서상 streaming AudioTrack은 buffer가 작을수록 underrun에 취약하므로,
     * 기기별 effective buffer size를 확인할 수 있어야 한다.
     */
    private fun logAudioTrackConfig(bufferSizeInBytes: Int) {
        val track = audioTrack ?: return
        val bufferFrames = track.bufferSizeInFrames
        Log.d(
            TAG,
            "track config: bufferBytes=$bufferSizeInBytes, bufferFrames=$bufferFrames, sampleRate=$SAMPLE_RATE, prebufferChunks=$PREBUFFER_CHUNK_COUNT, prebufferMaxWaitMs=$PREBUFFER_MAX_WAIT_MS"
        )
    }

    /**
     * underrun count는 AudioTrack 버퍼가 비어 재생 glitch가 날 수 있었는지 확인하는 핵심 지표다.
     */
    private fun logUnderrunIfChanged() {
        val underrunCount = getUnderrunCountCompat()
        if (underrunCount > lastLoggedUnderrunCount) {
            Log.w(
                TAG,
                "underrun detected: previous=$lastLoggedUnderrunCount, current=$underrunCount, pending=${pendingChunkCount.get()}"
            )
            lastLoggedUnderrunCount = underrunCount
        }
    }

    private fun getUnderrunCountCompat(): Int {
        val track = audioTrack ?: return 0
        return track.underrunCount
    }

    private fun resetPlaybackMetrics() {
        currentPlaybackStartedAtMs = null
        currentPlaybackEndedAtMs = null
        submittedFrameCount.set(0L)
        lastLoggedUnderrunCount = getUnderrunCountCompat()
    }
}
