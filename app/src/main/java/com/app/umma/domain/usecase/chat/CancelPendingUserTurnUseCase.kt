package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * 아직 commit 되지 않은 user turn 을 취소합니다.
 *
 * 이 유스케이스는 화면 이탈, 새 발화 시작 전 초기화, 녹음 실패 같은 cleanup 경로에서
 * transport 내부의 pending duration/commit 상태를 비우기 위한 명시적 경계입니다.
 */
class CancelPendingUserTurnUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    operator fun invoke() = repository.cancelPendingUserTurn()
}
