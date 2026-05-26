package com.app.umma.domain.model.audio

data class AudioInputFrame(
    val pcm: ByteArray,
    val level: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioInputFrame

        if (level != other.level) return false
        if (!pcm.contentEquals(other.pcm)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = level.hashCode()
        result = 31 * result + pcm.contentHashCode()
        return result
    }
}