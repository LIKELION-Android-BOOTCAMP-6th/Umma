package com.app.umma.wear.domain.usecase

import com.app.umma.wear.domain.model.WearChatState
import com.app.umma.wear.domain.repository.WearChatRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveWatchChatStateUseCase(
    private val repository: WearChatRepository
) {
    operator fun invoke(): StateFlow<WearChatState> = repository.chatState
}
