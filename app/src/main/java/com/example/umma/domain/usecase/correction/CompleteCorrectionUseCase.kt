package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CompleteCorrectionInput
import com.example.umma.domain.model.correction.CompleteCorrectionResult
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.learningstate.CorrectionResult
import com.example.umma.domain.repository.CorrectionRepository
import com.example.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import javax.inject.Inject

/**
 * Correction 완료 파이프라인을 하나의 UseCase 경계로 묶는다.
 *
 * 이 UseCase는 화면에서 해야 할 "저장 후 처리"를 받아서,
 * local-first 저장과 LangState / Summary 완료 갱신을 한 번에 정리한다.
 *
 * Session Memory 원문 buffer 압축은 RT-003의 실제 저장소 계약이 머지된 뒤
 * 후속 작업에서 연결한다. Correction-infra는 해당 저장소 구현을 선점하지 않는다.
 */
class CompleteCorrectionUseCase @Inject constructor(
    private val prepareSaveRequestUseCase: PrepareSaveRequestUseCase,
    private val correctionRepository: CorrectionRepository,
    private val applyLanguageStateUpdateUseCase: ApplyLanguageStateUpdateUseCase
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

        var saveResult: CorrectionSaveResult? = null

        return try {
            // Flashcard 저장이 먼저 성공해야 다음 단계가 이어진다.
            saveResult = correctionRepository.saveFlashcards(saveRequest).getOrThrow()

            // RT-003 머지 후 이 지점에서 Session Memory compression 계약을 연결한다.
            // 현재 브랜치에서는 Realtime-infra의 저장소 구현과 충돌하지 않도록 호출을 비워 둔다.

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
                    savedFlashcardIds = saveResult.localSavedFlashcardIds,
                    pendingSyncFlashcardIds = saveResult.pendingSyncFlashcardIds,
                    sessionMemoryKey = input.langStateUpdateInput.sessionMemoryKey,
                    completedAt = input.requestedAt
                )
            )
        } catch (error: Throwable) {
            rollbackLocalChanges(
                saveRequest = saveRequest,
                saveResult = saveResult
            )
            Result.failure(error)
        }
    }

    private suspend fun rollbackLocalChanges(
        saveRequest: CorrectionSaveRequest,
        saveResult: CorrectionSaveResult?
    ) {
        // 현재 Correction-infra가 직접 수행한 local 저장만 되돌린다.
        // Session Memory rollback은 RT-003 compression 계약이 확정된 뒤 후속 연결 작업에서 다룬다.
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
