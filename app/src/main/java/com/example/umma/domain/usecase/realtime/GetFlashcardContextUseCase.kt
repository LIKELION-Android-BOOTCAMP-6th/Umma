package com.example.umma.domain.usecase.realtime

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.realtime.SessionTurn
import com.example.umma.domain.repository.SessionMemoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 플래시카드 생성을 위해 사용자 발화 중심으로 정제된 컨텍스트 목록을 제공하는 비즈니스 유스케이스입니다.
 */
class GetFlashcardContextUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    operator fun invoke(language: LangCode): Flow<List<SessionTurn>> {
        return repository.getFlashcardContext(language)
    }
}
