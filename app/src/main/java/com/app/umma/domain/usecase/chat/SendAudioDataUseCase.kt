package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import javax.inject.Inject

class SendAudioDataUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(audio: ByteArray) = repository.sendAudioData(audio)
}