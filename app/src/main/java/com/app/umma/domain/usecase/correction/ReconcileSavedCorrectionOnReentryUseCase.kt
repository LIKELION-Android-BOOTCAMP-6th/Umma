package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.usecase.flashcardreview.GetFlashcardsUseCase
import com.app.umma.domain.usecase.learningstate.ApplyCorrectionSignalUpdateUseCase
import javax.inject.Inject

/**
 * 재진입 시 정합성 갭을 감지하고 복구하는 UseCase.
 *
 * 완료 파이프라인의 위험 창(①Flashcard commit ~ clearCorrectionCacheAfterCompletion 이전)에서
 * 프로세스가 사망하면 "카드는 저장됐으나 화면은 미완료"인 정합성 갭이 발생한다.
 * 재진입 시 캐시된 suggestion id 가 전부 이미 저장돼 있으면 갭으로 판정하고
 * correctionAvailable=false 신호를 내려 화면을 올바른 완료 상태로 복구한다.
 *
 * 참고: [COR-FIX-012 #370]
 */
class ReconcileSavedCorrectionOnReentryUseCase @Inject constructor(
    private val getFlashcardsUseCase: GetFlashcardsUseCase,
    private val applyCorrectionSignalUpdateUseCase: ApplyCorrectionSignalUpdateUseCase
) {

    sealed interface Outcome {
        /** 캐시의 suggestion id 가 전부 저장돼 있어 정합성 갭을 감지하고 correctionAvailable 을 닫았다. */
        data object AlreadySaved : Outcome

        /** 저장되지 않은 suggestion id 가 있어 정상 캐시 복원 흐름으로 진행해야 한다. */
        data object NotSaved : Outcome
    }

    suspend operator fun invoke(
        uid: String,
        lang: LangCode,
        sessionMemoryKey: String,
        cachedSuggestionIds: List<String>,
        requestedAt: Long
    ): Result<Outcome> {
        // suggestion id 가 없으면 갭 판정의 근거가 없으므로 정상 복원 흐름으로 위임한다.
        if (cachedSuggestionIds.isEmpty()) return Result.success(Outcome.NotSaved)

        val savedIds = getFlashcardsUseCase(uid, lang)
            .getOrElse { error -> return Result.failure(error) }
            .map { it.id }
            .toSet()

        // 부분 저장은 발생하지 않는다 — 저장은 항상 launchCompletion 으로 일괄 수행된다.
        // 따라서 "전부 저장됨" = 완료 파이프라인이 최소 Flashcard 단계까지 완료된 갭 상태다.
        if (!savedIds.containsAll(cachedSuggestionIds)) return Result.success(Outcome.NotSaved)

        // 갭 상태: correctionAvailable=false 로 닫아 동일 세션이 미완료로 다시 노출되지 않게 한다.
        applyCorrectionSignalUpdateUseCase(
            CorrectionSignalUpdateInput(
                uid = uid,
                lang = lang,
                sessionMemoryKey = sessionMemoryKey,
                sourceEventId = buildReconcileEventId(sessionMemoryKey, cachedSuggestionIds),
                correctionAvailable = false,
                updatedAt = requestedAt
            )
        ).getOrElse { error -> return Result.failure(error) }

        return Result.success(Outcome.AlreadySaved)
    }

    /**
     * "correction-reentry-reconcile:" prefix 는 noop 완료 경로의 "correction-noop:" 과 구분된다.
     * cachedSuggestionIds 를 정렬해 호출 순서에 무관하게 같은 eventId 를 생성한다.
     */
    private fun buildReconcileEventId(
        sessionMemoryKey: String,
        cachedSuggestionIds: List<String>
    ): String {
        val sortedIds = cachedSuggestionIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(",")
        return "correction-reentry-reconcile:$sessionMemoryKey:$sortedIds"
    }
}
