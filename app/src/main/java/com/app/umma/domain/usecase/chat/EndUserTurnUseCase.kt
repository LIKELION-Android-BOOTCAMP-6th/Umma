package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * 사용자가 정지 버튼으로 현재 user turn 을 명시적으로 끝냈음을 transport 에 전달합니다.
 *
 * 이 유스케이스는 단순 duration 저장과 turn commit 의 의미를 분리하기 위한 경계입니다.
 * UI 는 provider 를 알 필요 없이 "사용자 발화 종료"라는 의도만 전달하고, repository 가
 * OpenAI Realtime 의 commit/response 흐름에 맞게 처리합니다.
 */
class EndUserTurnUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke(durationMs: Long?) = repository.endUserTurn(durationMs)
}
