package com.example.umma.domain.repository

import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.correction.GenerateSuggestionsInput

/**
 * Correction 결과 생성과 Flashcard 저장 계약을 숨기는 Repository 입니다.
 */
interface CorrectionRepository {

    /**
     * 후보와 LangState snapshot 을 이용해 화면용 교정 결과를 생성한다.
     */
    suspend fun generateSuggestions(
        input: GenerateSuggestionsInput
    ): Result<List<CorrectionSuggestion>>

    /**
     * 선택된 교정 결과를 local-first Flashcard 저장 계약으로 반영한다.
     */
    suspend fun saveFlashcards(
        request: CorrectionSaveRequest
    ): Result<CorrectionSaveResult>
}
