package com.app.umma.domain.usecase.realtime

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.repository.SessionMemoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * AI 교정 요청에 적합하도록 정제 및 필터링된 대화 컨텍스트 목록을 제공하는 비즈니스 유스케이스입니다.
 */
class GetCorrectionContextUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    operator fun invoke(language: LangCode): Flow<List<SessionTurn>> {
        return repository.getCorrectionContext(language)
    }
}
