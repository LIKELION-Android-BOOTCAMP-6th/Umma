package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.repository.CorrectionRepository
import javax.inject.Inject

/**
 * Correction 후보와 LangState snapshot 을 화면용 교정 결과로 바꾸는 UseCase 입니다.
 */
class GenerateSuggestionsUseCase @Inject constructor(
    private val repository: CorrectionRepository
) {
    suspend operator fun invoke(
        input: GenerateSuggestionsInput
    ): Result<List<CorrectionSuggestion>> {
        return repository.generateSuggestions(input)
    }
}
