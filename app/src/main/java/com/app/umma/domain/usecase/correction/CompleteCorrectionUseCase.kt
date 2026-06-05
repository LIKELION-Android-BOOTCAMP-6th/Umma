package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CompleteCorrectionInput
import com.app.umma.domain.model.correction.CompleteCorrectionResult
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionResult
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.repository.CorrectionRepository
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.usecase.learningstate.ApplyCorrectionSignalUpdateUseCase
import com.app.umma.domain.usecase.learningstate.ApplyFlashcardSummaryUpdateUseCase
import com.app.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import com.app.umma.domain.usecase.realtime.CompressSessionMemoryUseCase
import com.app.umma.domain.usecase.realtime.SummarizeRecentTopicsUseCase
import com.app.umma.domain.usecase.statistics.RecordStatisticsHistoryUseCase
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
    private val applyCorrectionSignalUpdateUseCase: ApplyCorrectionSignalUpdateUseCase,
    private val recordStatisticsHistoryUseCase: RecordStatisticsHistoryUseCase,
    private val buildSessionCompressionPayloadUseCase: BuildSessionCompressionPayloadUseCase,
    private val compressSessionMemoryUseCase: CompressSessionMemoryUseCase,
    // Flashcard 저장 직후 dueFlashcards / savedFlashcards 를 즉시 재계산해 DashSummary 에 반영한다.
    // SRS 의 ApplyReviewDecisionUseCase 와 동일 패턴을 차용하되, 실패 시 rollback 없이 pending only 로 처리한다.
    private val flashcardRepository: FlashcardRepository,
    private val applyFlashcardSummaryUpdateUseCase: ApplyFlashcardSummaryUpdateUseCase,
    // 교정 완료 후 최근 5개 세션 주제를 AI 로 요약해 Session Memory 에 저장한다. (#162-C)
    // 실패해도 Done 흐름을 막지 않으며 기존 topicSummaries 는 변경하지 않는다.
    private val summarizeRecentTopicsUseCase: SummarizeRecentTopicsUseCase
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

        val saveableSuggestions = filterSaveableSuggestions(
            selectedSuggestions = input.selectedSuggestions,
            saveRequest = saveRequest
        )

        if (saveRequest.flashcards.isEmpty()) {
            return completeEmptySaveRequest(input)
        }

        // 교정 결과는 LangState 업데이트용 최소 모델만 넘긴다.
        // 품질 필터로 제외된 suggestion 은 저장 가치가 없으므로 학습 근거에도 섞지 않는다.
        val correctionResult = buildCorrectionResult(saveableSuggestions)

        // 0) compression payload 를 단계 진입 직후 한 번만 계산해 캐시한다.
        // 이 payload 의 recentTopics 는 Session Memory 압축용 키워드로만 사용한다.
        // Dashboard 주제 칩(recentTopic)은 단어 키워드가 아니라 AI 세션 요약 title 로만 갱신한다. (#173)
        val compressionCommandResult = buildCompressionCommand(
            input = input,
            selectedSuggestions = saveableSuggestions
        )
        val cachedCompressionCommand = compressionCommandResult.getOrNull()

        var saveResult: CorrectionSaveResult? = null

        return try {
            // 1) 사용자가 선택한 교정 결과를 새 Flashcard 원본으로 먼저 남긴다.
            // 이 단계가 실패하면 사용자가 기대한 저장 결과가 없으므로 전체 완료를 실패로 본다.
            saveResult = correctionRepository.saveFlashcards(saveRequest).getOrThrow()

            // 1.5) Flashcard 저장 직후 dueFlashcards / savedFlashcards 를 즉시 재계산해 DashSummary 에 반영한다.
            // LangState 갱신과 의존이 없는 이 위치에서 호출하면, LS 업데이트가 실패해도
            // 카드 카운트만은 정확한 상태로 남는다.
            // 실패 시에는 Flashcard 저장이 이미 commit 되었으므로 rollback 없이 pending only 로 처리한다.
            val flashcardSummaryResult = applyFlashcardSummaryIfPossible(input)

            // 2) 최근 세션 주제를 AI 로 요약해 Session Memory topicSummaries 에 저장하고,
            // Dashboard 표시용 짧은 topic title 을 얻는다.
            // 실패/빈 title 은 recentTopic=null 로 내려 기존 Dashboard topic 을 보존한다. (#173)
            val topicSummaryResult = summarizeRecentTopicsIfPossible(input)

            // 3) 저장 성공 후에는 같은 완료 흐름 안에서 Session/Dashboard 요약도 닫는다.
            // correctionAvailable 을 false 로 내려야 Dashboard 와 Correction 진입 판단이 같은 상태를 본다.
            // recentTopic 은 AI summary title 이 있을 때만 갱신한다.
            // null 이면 Repository 가 이전 값을 보존하므로 "hello" 같은 키워드 fallback 이 덮어쓰지 못한다.
            val learningStateUpdateResult = applyLanguageStateUpdateUseCase(
                input.langStateUpdateInput.copy(
                    correctionResult = correctionResult,
                    correctionAvailableOverride = false,
                    recentTopic = topicSummaryResult.displayTitle,
                    analyzedAt = input.requestedAt
                )
            ).getOrThrow()

            // 4) LS 저장 결과가 확정되면 Statistics history를 local-first로 기록한다.
            // history 실패는 교정 완료 자체를 되돌리지 않고, pending/error 상태로만 남긴다.
            val statisticsHistoryResult = recordStatisticsHistoryIfPossible(
                userId = input.langStateUpdateInput.uid,
                updateResult = learningStateUpdateResult
            )

            // 5) 앞의 네 단계가 성공한 뒤에만 Session Memory 압축을 시도한다.
            // 압축은 RT-003 소유 저장소에 대한 후속 정리라 실패해도 저장 완료를 rollback 하지 않는다.
            // step 0 에서 만든 동일 payload 를 그대로 재사용한다.
            val compressionResult = compressSessionMemoryIfPossible(
                buildResult = compressionCommandResult,
                command = cachedCompressionCommand
            )

            Result.success(
                CompleteCorrectionResult(
                    savedFlashcardIds = saveResult.localSavedFlashcardIds,
                    pendingSyncFlashcardIds = saveResult.pendingSyncFlashcardIds,
                    sessionMemoryKey = input.langStateUpdateInput.sessionMemoryKey,
                    sessionCompressionApplied = compressionResult.applied,
                    sessionCompressionPending = compressionResult.pending,
                    sessionCompressionErrorMessage = compressionResult.errorMessage,
                    statisticsHistoryApplied = statisticsHistoryResult.applied,
                    statisticsHistoryPending = statisticsHistoryResult.pending,
                    statisticsHistoryErrorMessage = statisticsHistoryResult.errorMessage,
                    flashcardSummaryApplied = flashcardSummaryResult.applied,
                    flashcardSummaryPending = flashcardSummaryResult.pending,
                    topicSummariesApplied = topicSummaryResult.applied,
                    topicSummariesPending = topicSummaryResult.pending,
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

    private suspend fun recordStatisticsHistoryIfPossible(
        userId: String,
        updateResult: com.app.umma.domain.model.learningstate.LearningStateUpdateResult
    ): StatisticsHistoryResult {
        // Statistics 기록은 LS 저장 완료 결과를 입력으로 받는다.
        // 여기서 실패해도 Correction 완료 자체는 성립하므로 pending/error로만 노출한다.
        return recordStatisticsHistoryUseCase(
            userId = userId,
            updateResult = updateResult
        ).fold(
            onSuccess = { result ->
                StatisticsHistoryResult(
                    applied = result.applied,
                    pending = result.isSyncPending,
                    errorMessage = null
                )
            },
            onFailure = { error ->
                StatisticsHistoryResult(
                    applied = false,
                    pending = false,
                    errorMessage = error.message
                )
            }
        )
    }

    private suspend fun compressSessionMemoryIfPossible(
        buildResult: Result<CompressSessionMemoryCommand?>,
        command: CompressSessionMemoryCommand?
    ): SessionCompressionResult {
        // payload 생성 자체가 실패한 경우에도 Flashcard 저장은 이미 완료된 상태다.
        // 따라서 사용자 흐름은 성공으로 유지하고, compression 만 pending 으로 알려준다.
        // step 0 에서 이미 build 결과를 받아 두므로 같은 Result 를 그대로 검사한다.
        buildResult.exceptionOrNull()?.let { error ->
            return SessionCompressionResult(
                applied = false,
                pending = true,
                errorMessage = error.message
            )
        }
        if (command == null) {
            return SessionCompressionResult(
                applied = false,
                pending = false,
                errorMessage = null
            )
        }

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
        input: CompleteCorrectionInput,
        selectedSuggestions: List<CorrectionSuggestion>
    ): Result<CompressSessionMemoryCommand?> {
        // CompressionPayload 는 Correction 이 알고 있는 교정 결과와 분석 turn 으로 만들지만,
        // 실제 Session Memory 저장/초기화 실행은 RT-003 UseCase 가 담당한다.
        return buildSessionCompressionPayloadUseCase(
            language = input.langStateUpdateInput.lang,
            selectedSuggestions = selectedSuggestions,
            recentUserTurns = input.langStateUpdateInput.recentUserTurns,
            compressedAt = input.requestedAt
        )
    }

    /**
     * COR-TUNE-007 빈 저장 결과 완료 경로.
     *
     * 저장 가능한 Flashcard가 0개라는 것은 저장소 실패가 아니라 품질 필터가 이번 선택을 모두
     * 정리했다는 뜻이다. 따라서 Flashcard 저장과 LangState 분석은 건너뛰되, 사용자가 이 교정
     * 세션을 처리했다는 사실만 summary 신호로 닫아 같은 교정이 다시 노출되지 않게 한다.
     */
    private suspend fun completeEmptySaveRequest(
        input: CompleteCorrectionInput
    ): Result<CompleteCorrectionResult> {
        val signalResult = applyCorrectionSignalUpdateUseCase(
            CorrectionSignalUpdateInput(
                uid = input.langStateUpdateInput.uid,
                lang = input.langStateUpdateInput.lang,
                sessionMemoryKey = input.langStateUpdateInput.sessionMemoryKey,
                sourceEventId = input.langStateUpdateInput.analysisEventId
                    ?.takeIf { it.isNotBlank() }
                    ?: buildNoopCompletionEventId(input),
                correctionAvailable = false,
                updatedAt = input.requestedAt
            )
        )
        if (signalResult.isFailure) {
            return Result.failure(signalResult.exceptionOrNull()!!)
        }

        return Result.success(
            CompleteCorrectionResult(
                savedFlashcardIds = emptyList(),
                pendingSyncFlashcardIds = emptyList(),
                sessionMemoryKey = input.langStateUpdateInput.sessionMemoryKey,
                completedAt = input.requestedAt
            )
        )
    }

    private fun filterSaveableSuggestions(
        selectedSuggestions: List<CorrectionSuggestion>,
        saveRequest: CorrectionSaveRequest
    ): List<CorrectionSuggestion> {
        val suggestionsById = selectedSuggestions.distinctBy { it.id }.associateBy { it.id }
        return saveRequest.flashcards.mapNotNull { item -> suggestionsById[item.suggestionId] }
    }

    private fun buildNoopCompletionEventId(
        input: CompleteCorrectionInput
    ): String {
        val selectedIds = input.selectedSuggestions
            .map { it.id.trim() }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(separator = ",")
        return "correction-noop:${input.langStateUpdateInput.sessionMemoryKey}:$selectedIds"
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
        // 이전 구현은 selectedSuggestions.first().afterText 만 correctedText 로 흘려보냈다.
        // N개 카드를 선택했을 때 첫 번째 카드만 반영되는 문제를 수정한다. (#162-B)
        // correctedText 는 LS 메트릭 계산(correctionCount 기반)이 아닌 저장/표시 목적 필드이므로
        // 모델 구조 변경 없이 줄바꿈으로 합치는 방식을 택한다.
        return CorrectionResult(
            correctedText = selectedSuggestions.joinToString(separator = "\n") { it.afterText },
            correctionCount = selectedSuggestions.size,
            // LangState 에는 화면 카드 전체가 아니라 학습 상태 갱신에 필요한 설명 요약만 전달한다.
            notes = selectedSuggestions.joinToString(separator = "\n") { suggestion ->
                suggestion.explanation
            },
            // COR-TUNE-02: 선택된 suggestion 의 관찰 학습 신호만 집계해 completion pipeline 으로 넘긴다.
            // 신호가 없는(=null) suggestion 은 mapNotNull 로 자연스럽게 빠진다. 신호 소비는 LearningState 책임.
            learningSignals = selectedSuggestions.mapNotNull { it.learningSignal }
        )
    }

    /**
     * 최근 5개 세션 주제를 AI 로 요약해 Session Memory 에 저장한다.
     *
     * 실패 시 기존 topicSummaries 는 변경하지 않으며 Done 흐름을 계속 진행한다. (#162-C)
     */
    private suspend fun summarizeRecentTopicsIfPossible(
        input: CompleteCorrectionInput
    ): TopicSummaryStepResult {
        val result = summarizeRecentTopicsUseCase(
            SummarizeTopicsCommand(
                language = input.langStateUpdateInput.lang,
                requestedAt = input.requestedAt
            )
        )
        return TopicSummaryStepResult(
            applied = result.applied,
            pending = result.pending,
            displayTitle = result.displayTitle
        )
    }

    /**
     * Flashcard 저장 직후 카드 카운트를 재계산해 DashSummary / FlashcardSummary 에 즉시 반영한다.
     *
     * SRS 의 ApplyReviewDecisionUseCase 패턴을 차용한다. 차이점:
     * - SRS 는 review schedule 실패 시 rollback 보상 트랜잭션을 수행한다.
     * - Correction 완료 파이프라인은 Flashcard 저장과 LangState 가 이미 commit 상태이므로
     *   보상 없이 pending only 로 처리하고 Done 흐름을 계속 진행한다.
     */
    private suspend fun applyFlashcardSummaryIfPossible(
        input: CompleteCorrectionInput
    ): FlashcardSummaryResult {
        // getReviewSummary 로 현재 시점 카드 수를 새로 계산한다.
        // null 반환(실패)이면 applyFlashcardSummaryUpdateUseCase 호출 자체를 건너뛴다.
        val snapshot = flashcardRepository.getReviewSummary(
            userId = input.langStateUpdateInput.uid,
            language = input.langStateUpdateInput.lang,
            now = input.requestedAt
        ).getOrNull() ?: return FlashcardSummaryResult(
            applied = false,
            pending = true
        )

        // summary 반영 실패도 Done 흐름을 막지 않는다. runCatching 으로 swallow 한다.
        val summaryResult = runCatching {
            applyFlashcardSummaryUpdateUseCase(
                FlashcardSummaryUpdateInput(
                    uid = input.langStateUpdateInput.uid,
                    lang = input.langStateUpdateInput.lang,
                    dueFlashcards = snapshot.dueFlashcards,
                    notifiableDueFlashcards = snapshot.notifiableDueFlashcards,
                    savedFlashcards = snapshot.savedFlashcards,
                    // sourceEventId 는 중복 반영 방지용 이벤트 식별자다.
                    // analysisEventId 는 LangState 갱신에 쓰이므로, Flashcard Summary 전용 prefix 를 붙여 분리한다.
                    sourceEventId = "correction-save:${input.langStateUpdateInput.analysisEventId}",
                    updatedAt = input.requestedAt
                )
            )
        }

        return if (summaryResult.isSuccess && summaryResult.getOrNull()?.isSuccess == true) {
            FlashcardSummaryResult(applied = true, pending = false)
        } else {
            FlashcardSummaryResult(applied = false, pending = true)
        }
    }

    private data class FlashcardSummaryResult(
        val applied: Boolean,
        val pending: Boolean
    )

    private data class TopicSummaryStepResult(
        val applied: Boolean,
        val pending: Boolean,
        val displayTitle: String?
    )

    private data class SessionCompressionResult(
        val applied: Boolean,
        val pending: Boolean,
        val errorMessage: String?
    )

    private data class StatisticsHistoryResult(
        val applied: Boolean,
        val pending: Boolean,
        val errorMessage: String?
    )
}
