package com.example.umma.domain.usecase

import com.example.umma.domain.repository.LiveChatRepository
import javax.inject.Inject

class SendAudioDataUseCase @Inject constructor(
    private val repository: LiveChatRepository
) {
    suspend operator fun invoke(audio: ByteArray) = repository.sendAudioData(audio)
}