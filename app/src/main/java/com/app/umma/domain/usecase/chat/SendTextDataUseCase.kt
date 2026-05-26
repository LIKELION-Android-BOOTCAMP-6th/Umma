package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import javax.inject.Inject

class SendTextDataUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(text: String) = repository.sendTextData(text)
}