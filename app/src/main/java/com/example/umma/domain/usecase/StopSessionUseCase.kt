package com.example.umma.domain.usecase

import com.example.umma.domain.repository.LiveChatRepository
import javax.inject.Inject

class StopSessionUseCase @Inject constructor(
    private val repository: LiveChatRepository
) {
    suspend operator fun invoke() = repository.stopSession()
}