package com.app.umma.watchbridge

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchReplayAudioStore @Inject constructor() {
    private var currentBuffer: ByteArrayOutputStream? = null
    private var lastReplayAudio: ByteArray = ByteArray(0)

    @Synchronized
    fun beginResponse() {
        currentBuffer = ByteArrayOutputStream()
        lastReplayAudio = ByteArray(0)
    }

    @Synchronized
    fun append(audio: ByteArray) {
        val buffer = currentBuffer ?: ByteArrayOutputStream().also {
            currentBuffer = it
            lastReplayAudio = ByteArray(0)
        }
        buffer.write(audio)
    }

    @Synchronized
    fun finalizeResponse(): ByteArray {
        val audio = currentBuffer?.toByteArray() ?: ByteArray(0)
        lastReplayAudio = audio
        currentBuffer = null
        return audio
    }

    @Synchronized
    fun clear() {
        currentBuffer = null
        lastReplayAudio = ByteArray(0)
    }

    @Synchronized
    fun getLastReplayAudio(): ByteArray = lastReplayAudio.copyOf()

    @Synchronized
    fun hasReplayAudio(): Boolean = lastReplayAudio.isNotEmpty()
}
