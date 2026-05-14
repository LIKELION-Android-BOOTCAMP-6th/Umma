package com.example.umma.domain.usecase.chat

import com.example.umma.domain.model.realtime.AIEvent
import com.example.umma.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * AI 서버로부터 발생하는 모든 실시간 이벤트([AIEvent])를 구독하는 유스케이스입니다.
 *
 * ViewModel은 이 유스케이스를 통해 획득한 Flow를 수집하여 UI를 갱신합니다.
 */
class ObserveAIEventUseCase @Inject constructor(
    private val repository: ChatRepository
){
    /**
     * [AIEvent] 스트림을 반환합니다.
     */
    operator fun invoke(): Flow<AIEvent> = repository.observeAIEvent()
}