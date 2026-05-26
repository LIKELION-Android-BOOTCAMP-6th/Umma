package com.app.umma.domain.usecase.realtime

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.repository.SessionMemoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 특정 학습 언어의 대화 원문 목록(recentFullContext) 변경사항을 실시간으로 구독하는 비즈니스 유스케이스입니다.
 */
class ObserveRecentFullContextUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    operator fun invoke(language: LangCode): Flow<List<SessionTurn>> {
        return repository.observeRecentFullContext(language)
    }
}
