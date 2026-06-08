package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * USER final transcript 이후 AI 응답 생성을 transport에 요청합니다.
 *
 * `instructions`는 현재 대화 위치만 담은 1회성 turn hint이며, session prompt나 band 정책을
 * 재정의하는 용도로 쓰지 않는다. Realtime 구현은 response.create.instructions override를 피하고,
 * 짧은 system conversation item으로만 전달한다.
 */
class CreateChatResponseUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    /**
     * 다음 USER final 뒤에 turn hint를 전달할 수 있도록 transport에 1회성 대기를 요청합니다.
     *
     * 이 호출은 Phone Chat 화면에서만 사용하고, Watch/기타 surface에는 적용하지 않는다.
     */
    fun prepareNextResponseInstructions() {
        repository.prepareNextResponseInstructions()
    }

    operator fun invoke(instructions: String? = null) {
        repository.createResponse(instructions = instructions)
    }
}
