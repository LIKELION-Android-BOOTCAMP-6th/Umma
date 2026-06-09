package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.repository.CorrectionRepository
import javax.inject.Inject

/**
 * Correction 후보와 LangState snapshot 을 화면용 교정 결과로 바꾸는 UseCase 입니다.
 *
 * 교정 결과 개수 상한은 표시 정책이 아니라 domain 정책이므로 이 UseCase가 단일 보증 지점이 됩니다.
 * AI 가 10개를 초과해 반환해도 저장/표시 대상은 여기서 최대 [MAX_SUGGESTIONS]개로 수렴시킵니다.
 */
class GenerateSuggestionsUseCase @Inject constructor(
    private val repository: CorrectionRepository
) {
    suspend operator fun invoke(
        input: GenerateSuggestionsInput
    ): Result<List<CorrectionSuggestion>> {
        return repository.generateSuggestions(input)
            .map { suggestions -> suggestions.take(MAX_SUGGESTIONS) }
    }

    private companion object {
        private const val MAX_SUGGESTIONS = 10
    }
}
