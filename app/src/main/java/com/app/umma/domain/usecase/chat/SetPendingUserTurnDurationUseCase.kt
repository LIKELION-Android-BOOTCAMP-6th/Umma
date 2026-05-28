package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import javax.inject.Inject

class SetPendingUserTurnDurationUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke(durationMs: Long?) = repository.setPendingUserTurnDuration(durationMs)
}
