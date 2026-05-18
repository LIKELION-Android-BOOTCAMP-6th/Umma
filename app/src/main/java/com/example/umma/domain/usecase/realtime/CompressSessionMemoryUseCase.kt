package com.example.umma.domain.usecase.realtime

import com.example.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.example.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject

/**
 * Session Memory 원문 버퍼를 압축 결과로 교체하는 UseCase 입니다.
 */
class CompressSessionMemoryUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    /**
     * Session Memory 를 압축합니다.
     *
     * @param command 압축 대상 언어와 압축 결과
     * @return 성공/실패 결과
     */
    suspend operator fun invoke(command: CompressSessionMemoryCommand): Result<Unit> {
        return repository.compressSessionMemory(command)
    }
}
