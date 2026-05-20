package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CompleteCorrectionInput
import com.example.umma.domain.model.correction.CompleteCorrectionResult
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.learningstate.CorrectionResult
import com.example.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.example.umma.domain.repository.CorrectionRepository
import com.example.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import com.example.umma.domain.usecase.realtime.CompressSessionMemoryUseCase
import javax.inject.Inject

/**
 * Correction 완료 파이프라인을 하나의 UseCase 경계로 묶는다.
 *
 * 이 UseCase는 화면에서 해야 할 "저장 후 처리"를 받아서,
 * local-first 저장과 LangState / Summary 완료 갱신을 한 번에 정리한다.
 *
 * Session Memory 원문 buffer 저장/압축 구현은 RT-003 책임이다.
 * Correction-infra는 교정 결과 기반 compression payload 를 만든 뒤,
 * 기존 RT-003 compression 계약을 호출하는 연결 책임만 가진다.
 */
class CompleteCorrectionUseCase @Inject constructor(
    private val prepareSaveRequestUseCase: PrepareSaveRequestUseCase,
    private val correctionRepository: CorrectionRepository,
    private val applyLanguageStateUpdateUseCase: ApplyLanguageStateUpdateUseCase,
    private val buildSessionCompressionPayloadUseCase: BuildSessionCompressionPayloadUseCase,
    private val compressSessionMemoryUseCase: CompressSessionMemoryUseCase
) {

    suspend operator fun invoke(
        input: CompleteCorrectionInput
    ): Result<CompleteCorrectionResult> {
        // 저장할 카드가 없는 완료 요청은 사용자의 "저장" 행동과 맞지 않으므로
        // repository 나 LangState 를 건드리기 전에 실패로 종료한다.
        if (input.selectedSuggestions.isEmpty()) {
            return Result.failure(IllegalArgumentException("selectedSuggestions must not be empty"))
        }

        // 화면은 CorrectionSuggestion 만 넘기고, Flashcard 앞/뒷면 계약은 domain 에서 만든다.
        // 이렇게 해야 COR-005 저장 요청 형식이 화면 구현에 흩어지지 않는다.
        val saveRequest = prepareSaveRequestUseCase(
            uid = input.langStateUpdateInput.uid,
            selectedSuggestions = input.selectedSuggestions,
            requestedAt = input.requestedAt
        ).getOrElse { error ->
            return Result.failure(error)
        }

        // 교정 결과는 LangState 업데이트용 최소 모델만 넘긴다.
        // 화면 카드용 CorrectionSuggestion 을 LearningState 모델로 직접 저장하지 않기 위한 분리다.
        val correctionResult = buildCorrectionResult(input.selectedSuggestions)

        var saveResult: CorrectionSaveResult? = null

        return try {
            // 1) 사용자가 선택한 교정 결과를 새 Flashcard 원본으로 먼저 남긴다.
            // 이 단계가 실패하면 사용자가 기대한 저장 결과가 없으므로 전체 완료를 실패로 본다.
            saveResult = correctionRepository.saveFlashcards(saveRequest).getOrThrow()

            // 2) 저장 성공 후에는 같은 완료 흐름 안에서 Session/Dashboard 요약도 닫는다.
            // correctionAvailable 을 false 로 내려야 Dashboard 와 Correction 진입 판단이 같은 상태를 본다.
            applyLanguageStateUpdateUseCase(
                input.langStateUpdateInput.copy(
                    correctionResult = correctionResult,
                    correctionAvailableOverride = false,
                    analyzedAt = input.requestedAt
                )
            ).getOrThrow()

            // 3) 앞의 두 단계가 성공한 뒤에만 Session Memory 압축을 시도한다.
            // 압축은 RT-003 소유 저장소에 대한 후속 정리라 실패해도 저장 완료를 rollback 하지 않는다.
            val compressionResult = compressSessionMemoryIfPossible(input)

            Result.success(
                CompleteCorrectionResult(
                    savedFlashcardIds = saveResult.localSavedFlashcardIds,
                    pendingSyncFlashcardIds = saveResult.pendingSyncFlashcardIds,
                    sessionMemoryKey = input.langStateUpdateInput.sessionMemoryKey,
                    sessionCompressionApplied = compressionResult.applied,
                    sessionCompressionPending = compressionResult.pending,
                    sessionCompressionErrorMessage = compressionResult.errorMessage,
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

    private suspend fun compressSessionMemoryIfPossible(
        input: CompleteCorrectionInput
    ): SessionCompressionResult {
        // payload 생성 자체가 실패한 경우에도 Flashcard 저장은 이미 완료된 상태다.
        // 따라서 사용자 흐름은 성공으로 유지하고, compression 만 pending 으로 알려준다.
        val command = buildCompressionCommand(input).getOrElse { error ->
            return SessionCompressionResult(
                applied = false,
                pending = true,
                errorMessage = error.message
            )
        } ?: return SessionCompressionResult(
            applied = false,
            pending = false,
            errorMessage = null
        )

        return compressSessionMemoryUseCase(command).fold(
            onSuccess = {
                SessionCompressionResult(
                    applied = true,
                    pending = false,
                    errorMessage = null
                )
            },
            onFailure = { error ->
                // Flashcard 저장과 LangState/Summary 갱신은 이미 local completion 으로 성립했다.
                // compression 실패만으로 사용자 저장 결과를 되돌리지 않고 후속 재시도 대상으로 남긴다.
                SessionCompressionResult(
                    applied = false,
                    pending = true,
                    errorMessage = error.message
                )
            }
        )
    }

    private fun buildCompressionCommand(
        input: CompleteCorrectionInput
    ): Result<CompressSessionMemoryCommand?> {
        // CompressionPayload 는 Correction 이 알고 있는 교정 결과와 분석 turn 으로 만들지만,
        // 실제 Session Memory 저장/초기화 실행은 RT-003 UseCase 가 담당한다.
        return buildSessionCompressionPayloadUseCase(
            language = input.langStateUpdateInput.lang,
            selectedSuggestions = input.selectedSuggestions,
            recentUserTurns = input.langStateUpdateInput.recentUserTurns,
            compressedAt = input.requestedAt
        )
    }

    private suspend fun rollbackLocalChanges(
        saveRequest: CorrectionSaveRequest,
        saveResult: CorrectionSaveResult?
    ) {
        // 현재 Correction-infra가 직접 수행한 local 저장만 되돌린다.
        // Session Memory는 RT-003 소유 저장소이므로 이 UseCase에서 rollback하지 않는다.
        // compression 실패는 완료 실패로 키우지 않고 pending 결과로 남기는 정책을 따른다.
        if (saveResult != null && saveResult.localSavedFlashcardIds.isNotEmpty()) {
            // saveResult.localSavedFlashcardIds는 DB insert가 실제로 성공한 카드만 담는다.
            // 이 값을 기준으로 좁히면 중복 저장 요청 중 이미 존재하던 카드를 잘못 삭제하지 않는다.
            val newlySavedIds = saveResult.localSavedFlashcardIds.toSet()
            val rollbackRequest = saveRequest.copy(
                // 중복 요청으로 이미 존재하던 카드는 이번 완료 흐름이 만든 카드가 아니다.
                // rollback은 이번 요청에서 새로 저장된 카드만 대상으로 삼아 기존 카드를 지우지 않는다.
                flashcards = saveRequest.flashcards.filter { it.suggestionId in newlySavedIds }
            )
            correctionRepository.rollbackFlashcards(rollbackRequest)
        }
    }

    private fun buildCorrectionResult(
        selectedSuggestions: List<CorrectionSuggestion>
    ): CorrectionResult {
        val firstSuggestion = selectedSuggestions.first()
        return CorrectionResult(
            correctedText = firstSuggestion.afterText,
            correctionCount = selectedSuggestions.size,
            // LangState 에는 화면 카드 전체가 아니라 학습 상태 갱신에 필요한 설명 요약만 전달한다.
            notes = selectedSuggestions.joinToString(separator = "\n") { suggestion ->
                suggestion.explanation
            }
        )
    }

    private data class SessionCompressionResult(
        val applied: Boolean,
        val pending: Boolean,
        val errorMessage: String?
    )
}
