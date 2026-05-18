package com.example.umma.domain.audio


/**
 * 오디오 재생 추상화 계층 인터페이스
 *  */
interface AudioOutput {
    fun startPlaying()
    fun playAudioChunk(audio: ByteArray)
    fun stopPlaying()
    fun release()
}