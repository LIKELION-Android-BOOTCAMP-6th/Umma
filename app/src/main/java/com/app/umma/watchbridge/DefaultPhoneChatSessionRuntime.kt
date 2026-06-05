package com.app.umma.watchbridge

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.usecase.chat.CancelPendingUserTurnUseCase
import com.app.umma.domain.usecase.chat.EndUserTurnUseCase
import com.app.umma.domain.usecase.chat.ObserveAIEventUseCase
import com.app.umma.domain.usecase.chat.RetryConnectionResult
import com.app.umma.domain.usecase.chat.RetryConnectionUseCase
import com.app.umma.domain.usecase.chat.SendAudioDataUseCase
import com.app.umma.domain.usecase.chat.StartSessionUseCase
import com.app.umma.domain.usecase.chat.StopSessionUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DefaultPhoneChatSessionRuntime @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val retryConnectionUseCase: RetryConnectionUseCase,
    private val observeAIEventUseCase: ObserveAIEventUseCase,
    private val sendAudioDataUseCase: SendAudioDataUseCase,
    private val endUserTurnUseCase: EndUserTurnUseCase,
    private val cancelPendingUserTurnUseCase: CancelPendingUserTurnUseCase,
    private val stopSessionUseCase: StopSessionUseCase,
    private val chatRepository: ChatRepository
) : PhoneChatSessionRuntime {
    override suspend fun startSession(): Result<String> = startSessionUseCase()

    override suspend fun retryConnection(): RetryConnectionResult = retryConnectionUseCase()

    override fun observeAIEvents(): Flow<AIEvent> = observeAIEventUseCase()

    override suspend fun sendAudioData(audio: ByteArray) {
        sendAudioDataUseCase(audio)
    }

    override fun endUserTurn(durationMs: Long?) {
        endUserTurnUseCase(durationMs)
    }

    override fun cancelPendingUserTurn() {
        cancelPendingUserTurnUseCase()
    }

    override suspend fun stopSession(clearAppSession: Boolean) {
        stopSessionUseCase(clearAppSession = clearAppSession)
    }

    override fun getActiveSessionId(): String? = chatRepository.getActiveSessionId()

    override fun getCurrentSessionLang(): LangCode? = chatRepository.getCurrentSessionLang()
}
