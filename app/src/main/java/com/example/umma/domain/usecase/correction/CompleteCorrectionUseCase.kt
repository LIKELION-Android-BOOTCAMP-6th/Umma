package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CompleteCorrectionInput
import com.example.umma.domain.model.correction.CompleteCorrectionResult
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.learningstate.CorrectionResult
import com.example.umma.domain.model.learningstate.SessionMemoryCompressionInput
import com.example.umma.domain.model.learningstate.SessionMemoryCompressionResult
import com.example.umma.domain.repository.CorrectionRepository
import com.example.umma.domain.repository.SessionMemoryRepository
import com.example.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import javax.inject.Inject

/**
 * Correction 완료 파이프라인을 하나의 UseCase 경계로 묶는다.
 *
 * 이 UseCase는 화면에서 해야 할 "저장 후 처리"를 받아서,
 * local-first 저장, Session Memory 압축, LangState / Summary 갱신을 한 번에 정리한다.
 */
class CompleteCorrectionUseCase @Inject constructor(
    private val prepareSaveRequestUseCase: PrepareSaveRequestUseCase,
    private val correctionRepository: CorrectionRepository,
    private val applyLanguageStateUpdateUseCase: ApplyLanguageStateUpdateUseCase,
    private val sessionMemoryRepository: SessionMemoryRepository
) {

    suspend operator fun invoke(
        input: CompleteCorrectionInput
    ): Result<CompleteCorrectionResult> {
        // 저장 요청과 상태 업데이트 입력이 비어 있으면 완료 흐름 자체를 시작하지 않는다.
        if (input.selectedSuggestions.isEmpty()) {
            return Result.failure(IllegalArgumentException("selectedSuggestions must not be empty"))
        }

        val saveRequest = prepareSaveRequestUseCase(
            selectedSuggestions = input.selectedSuggestions,
            requestedAt = input.requestedAt
        ).getOrElse { error ->
            return Result.failure(error)
        }

        // 교정 결과는 LangState 업데이트용 최소 모델만 넘긴다.
        val correctionResult = buildCorrectionResult(input.selectedSuggestions)
        val compressionInput = SessionMemoryCompressionInput(
            uid = input.langStateUpdateInput.uid,
            lang = input.langStateUpdateInput.lang,
            sessionMemoryKey = input.langStateUpdateInput.sessionMemoryKey,
            requestedAt = input.requestedAt
        )

        var saveResult: CorrectionSaveResult? = null
        var compressionResult: SessionMemoryCompressionResult? = null

        return try {
            // Flashcard 저장이 먼저 성공해야 다음 단계가 이어진다.
            saveResult = correctionRepository.saveFlashcards(saveRequest).getOrThrow()

            // 세션 원문은 교정 이후 압축해서 다음 교정 주기의 입력을 줄인다.
            compressionResult = sessionMemoryRepository.compressRecentFullContext(compressionInput).getOrThrow()

            // 교정 완료 시점에는 correctionAvailable 을 false 로 내려서 다음 진입 판단을 맞춘다.
            applyLanguageStateUpdateUseCase(
                input.langStateUpdateInput.copy(
                    correctionResult = correctionResult,
                    correctionAvailableOverride = false,
                    analyzedAt = input.requestedAt
                )
            ).getOrThrow()

            Result.success(
                CompleteCorrectionResult(
                    savedFlashcardIds = saveResult.localSavedSuggestionIds,
                    pendingSyncFlashcardIds = saveResult.pendingSyncSuggestionIds,
                    sessionMemoryKey = input.langStateUpdateInput.sessionMemoryKey,
                    completedAt = input.requestedAt
                )
            )
        } catch (error: Throwable) {
            rollbackLocalChanges(
                saveRequest = saveRequest,
                saveResult = saveResult,
                compressionInput = compressionInput,
                compressionResult = compressionResult
            )
            Result.failure(error)
        }
    }

    private suspend fun rollbackLocalChanges(
        saveRequest: CorrectionSaveRequest,
        saveResult: CorrectionSaveResult?,
        compressionInput: SessionMemoryCompressionInput,
        compressionResult: SessionMemoryCompressionResult?
    ) {
        // 성공한 단계만 역순으로 되돌려 부분 완료 상태가 남지 않도록 한다.
        if (compressionResult != null) {
            sessionMemoryRepository.rollbackRecentFullContextCompression(compressionInput)
        }

        if (saveResult != null) {
            correctionRepository.rollbackFlashcards(saveRequest)
        }
    }

    private fun buildCorrectionResult(
        selectedSuggestions: List<CorrectionSuggestion>
    ): CorrectionResult {
        val firstSuggestion = selectedSuggestions.first()
        return CorrectionResult(
            correctedText = firstSuggestion.afterText,
            correctionCount = selectedSuggestions.size,
            notes = selectedSuggestions.joinToString(separator = "\n") { suggestion ->
                suggestion.explanation
            }
        )
    }
}
