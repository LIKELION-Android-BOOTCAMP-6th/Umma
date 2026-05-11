package com.example.umma.domain.usecase

import com.example.umma.domain.repository.ChatRepository
import javax.inject.Inject

class SendTextDataUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(text: String) = repository.sendTextData(text)
}