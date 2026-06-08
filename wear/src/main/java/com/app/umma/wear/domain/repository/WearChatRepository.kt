package com.app.umma.wear.domain.repository

import com.app.umma.wear.domain.model.WearChatState
import kotlinx.coroutines.flow.StateFlow

interface WearChatRepository {
    val chatState: StateFlow<WearChatState>

    suspend fun attachToPhoneSession(): Result<Unit>
    suspend fun detachFromPhoneSession(): Result<Unit>
    suspend fun startUserTurn(): Result<Unit>
    suspend fun finishUserTurn(): Result<Unit>
    suspend fun releasePhoneOwnerForDebug(): Result<Unit>
}
