package com.app.umma.wear.domain.repository

import com.app.umma.wear.domain.model.WearChatState
import kotlinx.coroutines.flow.StateFlow

interface WearChatRepository {
    val chatState: StateFlow<WearChatState>

    suspend fun refreshPhoneTargets(): Result<Unit>
    suspend fun selectPhoneTarget(nodeId: String): Result<Unit>
    suspend fun setTargetChooserVisible(visible: Boolean)
    suspend fun attachToPhoneSession(): Result<Unit>
    suspend fun detachFromPhoneSession(): Result<Unit>
    suspend fun startUserTurn(): Result<Unit>
    suspend fun finishUserTurn(): Result<Unit>
    suspend fun releasePhoneOwnerForDebug(): Result<Unit>
}
