package com.app.umma.wear.data

import android.media.AudioFormat

object WearAudioConfig {
    const val INPUT_SAMPLE_RATE_HZ = 16_000
    const val OUTPUT_SAMPLE_RATE_HZ = 24_000
    const val INPUT_CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
    const val OUTPUT_CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
    const val PCM_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2
    const val STREAM_CHUNK_SIZE_BYTES = 2_048
    const val RECORD_BUFFER_SIZE_FACTOR = 2
    const val PLAYBACK_BUFFER_SIZE_FACTOR = 4
    const val PREBUFFER_CHUNK_COUNT = 2
    const val PREBUFFER_MAX_WAIT_MS = 120L
    const val WRITE_RETRY_DELAY_MS = 2L
    const val PLAYBACK_DRAIN_WAIT_STEP_MS = 10L
    const val PLAYBACK_DRAIN_EXTRA_GUARD_MS = 20L
    const val SILENCE_PADDING_MS = 160L
    const val ZERO_WRITE_MAX_RETRY_COUNT = 20
}
