package com.example.umma.presentation.live_chat

import com.example.umma.domain.model.AIState

data class LiveChatUiState (
    val isChatting: Boolean = false,
    val aiState: AIState = AIState.IDLE,
    val lastTranscription: String = "",
    val errorMessage: String? = null
)

