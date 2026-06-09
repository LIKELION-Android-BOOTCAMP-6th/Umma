package com.app.umma.wear.domain.usecase

import com.app.umma.wear.domain.repository.WearChatRepository

class SelectPhoneTargetUseCase(
    private val repository: WearChatRepository
) {
    suspend operator fun invoke(nodeId: String): Result<Unit> = repository.selectPhoneTarget(nodeId)
}
