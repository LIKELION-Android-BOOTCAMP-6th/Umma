package com.app.umma.domain.audio

import com.app.umma.domain.model.audio.AudioInputFrame
import kotlinx.coroutines.flow.Flow

/**
 * 녹음 추상화 계층 인터페이스
 * */
interface AudioInput {
    fun startRecording(): Flow<AudioInputFrame>
}