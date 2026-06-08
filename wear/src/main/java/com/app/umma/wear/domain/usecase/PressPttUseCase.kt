package com.app.umma.wear.domain.usecase

import com.app.umma.wear.domain.repository.WearChatRepository

class PressPttUseCase(
    private val repository: WearChatRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.startUserTurn()
}
