package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import javax.inject.Inject

class StopSessionUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(clearAppSession: Boolean = true) =
        repository.stopSession(clearAppSession = clearAppSession)
}
