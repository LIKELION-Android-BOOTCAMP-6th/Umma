package com.example.umma.domain.usecase.realtime

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.realtime.SessionMemory
import com.example.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject

/**
 * 특정 학습 언어의 세션 메모리 전체(대화 버퍼, 요약 정보 등)를 1회성으로 안전하게 조회하는 비즈니스 유스케이스입니다.
 */
class GetSessionMemoryUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    suspend operator fun invoke(language: LangCode): Result<SessionMemory> {
        return repository.getSessionMemory(language)
    }
}
