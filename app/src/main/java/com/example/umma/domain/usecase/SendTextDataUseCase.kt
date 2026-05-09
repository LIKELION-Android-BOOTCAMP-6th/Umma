package com.example.umma.domain.usecase

import com.example.umma.domain.repository.LiveChatRepository
import javax.inject.Inject

class SendTextDataUseCase @Inject constructor(
    private val repository: LiveChatRepository
) {
    suspend operator fun invoke(text: String) = repository.sendTextData(text)
}