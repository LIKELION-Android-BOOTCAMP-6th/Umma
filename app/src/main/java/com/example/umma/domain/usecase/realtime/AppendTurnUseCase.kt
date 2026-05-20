package com.example.umma.domain.usecase.realtime

import com.example.umma.domain.model.realtime.AppendTurnCommand
import com.example.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject

/**
 * 확정된 turn 을 Session Memory 에 append 하는 UseCase 입니다.
 */
class AppendTurnUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    /**
     * 확정된 turn 을 저장합니다.
     *
     * @param command 저장 대상 turn 과 저장 스코프
     * @return 성공/실패 결과
     */
    suspend operator fun invoke(command: AppendTurnCommand): Result<Unit> {
        return repository.appendTurn(command)
    }
}
