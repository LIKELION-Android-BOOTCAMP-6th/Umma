package com.app.umma.wear.data

import android.media.AudioAttributes
import android.media.AudioTrack
import android.util.Log
import com.app.umma.wear.domain.audio.WearAudioOutput
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WearAudioPlayer : WearAudioOutput {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioQueue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val pendingChunkCount = AtomicInteger(0)

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var responseFinished = false

    init {
        createAudioTrack()
    }

    override fun startPlaying() {
        responseFinished = false
        startPlaybackWorkerIfNeeded()
    }

    override fun playAudioChunk(audio: ByteArray) {
        if (audio.isEmpty()) return
        responseFinished = false
        pendingChunkCount.incrementAndGet()
        if (audioQueue.trySend(audio.copyOf()).isFailure) {
            pendingChunkCount.decrementAndGet()
            Log.w(TAG, "Dropped watch audio chunk because queue send failed.")
            return
        }
        _isPlaying.value = true
        startPlaybackWorkerIfNeeded()
    }

    override fun finishPlaybackResponse() {
        responseFinished = true
    }

    override fun stopPlaying() {
        playbackJob?.cancel()
        playbackJob = null
        clearAudioQueue()
        _isPlaying.value = false
        responseFinished = false
        audioTrack?.pause()
        audioTrack?.flush()
    }

    override fun release() {
        stopPlaying()
        scope.cancel()
        audioTrack?.release()
        audioTrack = null
    }

    private fun createAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            WearAudioConfig.OUTPUT_SAMPLE_RATE_HZ,
            WearAudioConfig.OUTPUT_CHANNEL_MASK,
            WearAudioConfig.PCM_ENCODING
        ) * WearAudioConfig.PLAYBACK_BUFFER_SIZE_FACTOR

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(WearAudioConfig.PCM_ENCODING)
                    .setSampleRate(WearAudioConfig.OUTPUT_SAMPLE_RATE_HZ)
                    .setChannelMask(WearAudioConfig.OUTPUT_CHANNEL_MASK)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun startPlaybackWorkerIfNeeded() {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch {
            try {
                for (chunk in audioQueue) {
                    val track = audioTrack ?: continue
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        waitForPrebuffer()
                        track.play()
                        Log.d(TAG, "watch playback started: pending=${pendingChunkCount.get()}")
                    }
                    writeChunkFully(chunk)
                    val remaining = decrementPendingChunkCount()
                    if (remaining == 0 && responseFinished) {
                        val targetFrames = writeSilencePaddingAfterLastChunk(track)
                        waitUntilSubmittedFramesArePlayed(track, targetFrames)
                        if (pendingChunkCount.get() == 0) {
                            pausePlaybackAfterDrain(track)
                            responseFinished = false
                            _isPlaying.value = false
                            Log.d(TAG, "watch playback drained")
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Watch playback worker failed", error)
            } finally {
                _isPlaying.value = false
            }
        }
    }

    private suspend fun writeChunkFully(chunk: ByteArray) {
        val track = audioTrack ?: return
        var writtenTotal = 0
        var zeroWriteCount = 0

        while (writtenTotal < chunk.size) {
            val writeResult = track.write(chunk, writtenTotal, chunk.size - writtenTotal)
            when {
                writeResult > 0 -> writtenTotal += writeResult
                writeResult == 0 -> {
                    zeroWriteCount += 1
                    if (zeroWriteCount >= WearAudioConfig.ZERO_WRITE_MAX_RETRY_COUNT) {
                        Log.w(TAG, "watch zero-byte write aborted: written=$writtenTotal total=${chunk.size}")
                        return
                    }
                    delay(WearAudioConfig.WRITE_RETRY_DELAY_MS)
                }

                else -> {
                    Log.w(TAG, "watch write failed: code=$writeResult")
                    return
                }
            }
        }
    }

    private suspend fun waitForPrebuffer() {
        val startedAt = System.currentTimeMillis()
        while (pendingChunkCount.get() < WearAudioConfig.PREBUFFER_CHUNK_COUNT) {
            if (System.currentTimeMillis() - startedAt >= WearAudioConfig.PREBUFFER_MAX_WAIT_MS) {
                return
            }
            delay(WearAudioConfig.WRITE_RETRY_DELAY_MS)
        }
    }

    private suspend fun writeSilencePaddingAfterLastChunk(track: AudioTrack): Long {
        val paddingFrameCount =
            WearAudioConfig.OUTPUT_SAMPLE_RATE_HZ * WearAudioConfig.SILENCE_PADDING_MS / 1000
        val silencePadding = ByteArray((paddingFrameCount * WearAudioConfig.BYTES_PER_SAMPLE).toInt())
        return runCatching {
            val writtenBytes = track.write(silencePadding, 0, silencePadding.size)
            track.playbackHeadPosition.toLong() + (writtenBytes / WearAudioConfig.BYTES_PER_SAMPLE)
        }.getOrElse {
            track.playbackHeadPosition.toLong()
        }
    }

    private suspend fun waitUntilSubmittedFramesArePlayed(
        track: AudioTrack,
        targetFrames: Long
    ) {
        val startedAt = System.currentTimeMillis()
        val maxWaitMs =
            ((targetFrames * 1_000L) / WearAudioConfig.OUTPUT_SAMPLE_RATE_HZ) +
                WearAudioConfig.PLAYBACK_DRAIN_EXTRA_GUARD_MS

        while (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            val playedFrames = track.playbackHeadPosition.toLong()
            if (playedFrames >= targetFrames) {
                delay(WearAudioConfig.PLAYBACK_DRAIN_EXTRA_GUARD_MS)
                return
            }
            if (System.currentTimeMillis() - startedAt >= maxWaitMs) {
                Log.w(TAG, "watch drain wait timeout: playedFrames=$playedFrames targetFrames=$targetFrames")
                return
            }
            delay(WearAudioConfig.PLAYBACK_DRAIN_WAIT_STEP_MS)
        }
    }

    private fun pausePlaybackAfterDrain(track: AudioTrack) {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
        }
    }

    private fun clearAudioQueue() {
        while (true) {
            val result = audioQueue.tryReceive()
            if (result.isFailure) break
            decrementPendingChunkCount()
        }
    }

    private fun decrementPendingChunkCount(): Int {
        while (true) {
            val current = pendingChunkCount.get()
            val next = (current - 1).coerceAtLeast(0)
            if (pendingChunkCount.compareAndSet(current, next)) {
                return next
            }
        }
    }

    private companion object {
        const val TAG = "WearAudioPlayer"
    }
}
