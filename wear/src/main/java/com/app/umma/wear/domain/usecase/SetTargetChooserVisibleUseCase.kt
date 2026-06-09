package com.app.umma.wear.domain.usecase

import com.app.umma.wear.domain.repository.WearChatRepository

class SetTargetChooserVisibleUseCase(
    private val repository: WearChatRepository
) {
    suspend operator fun invoke(visible: Boolean) = repository.setTargetChooserVisible(visible)
}
