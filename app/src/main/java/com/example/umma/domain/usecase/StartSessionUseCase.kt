package com.example.umma.domain.usecase

import com.example.umma.domain.repository.ChatRepository
import javax.inject.Inject

class StartSessionUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.startSession()
}