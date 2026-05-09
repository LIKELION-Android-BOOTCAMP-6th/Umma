package com.example.umma.domain.usecase

import com.example.umma.domain.model.AIEvent
import com.example.umma.domain.repository.LiveChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAIEventUseCase @Inject constructor(
    private val repository: LiveChatRepository
){
    operator fun invoke(): Flow<AIEvent> = repository.observeAIEvent()
}