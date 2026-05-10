package com.example.umma.presentation.chat

import com.example.umma.domain.model.AIState

data class ChatUiState(
    val isChatting: Boolean = false,
    val aiState: AIState = AIState.IDLE,
    val lastTranscription: String = "",
    val errorMessage: String? = null
)

