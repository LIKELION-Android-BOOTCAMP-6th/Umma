package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveZeroReason
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.PrepareCorrectionSaveRequestResult
import javax.inject.Inject

/**
 * 선택한 교정 결과를 Flashcard 저장 요청으로 정리하는 UseCase다.
 *
 * 품질 필터와 안전 차단을 함께 적용하고, 저장 가능한 카드가 0개가 된 사유를 결과에 남긴다.
 */
class PrepareSaveRequestUseCase @Inject constructor(
    private val correctionSafetyPolicy: CorrectionSafetyPolicy
) {

    operator fun invoke(
        uid: String,
        selectedSuggestions: List<CorrectionSuggestion>,
        requestedAt: Long = System.currentTimeMillis()
    ): Result<PrepareCorrectionSaveRequestResult> {
        return runCatching {
            require(uid.isNotBlank()) {
                "uid must not be blank"
            }

            val normalizedSuggestions = selectedSuggestions.distinctBy { it.id }
            require(normalizedSuggestions.isNotEmpty()) {
                "selectedSuggestions must not be empty"
            }

            val lang = normalizedSuggestions.first().lang
            require(normalizedSuggestions.all { it.lang == lang }) {
                "selectedSuggestions must use the same language"
            }

            val safetyBlockedIds = mutableListOf<String>()
            val safetyEligibleSuggestions = normalizedSuggestions.filter { suggestion ->
                val blocked = correctionSafetyPolicy.isBlocked(suggestion)
                if (blocked) {
                    safetyBlockedIds += suggestion.id
                }
                !blocked
            }

            val seenKeys = mutableSetOf<String>()
            val qualityFilteredIds = mutableListOf<String>()
            val saveableSuggestions = safetyEligibleSuggestions.filter { suggestion ->
                val normalizedBefore = CorrectionCardTextNormalizer.normalize(suggestion.beforeText)
                val normalizedAfter = CorrectionCardTextNormalizer.normalize(suggestion.afterText)
                val normalizedFront = CorrectionCardTextNormalizer.normalize(suggestion.nativeText)

                if (normalizedBefore == normalizedAfter) {
                    qualityFilteredIds += suggestion.id
                    return@filter false
                }
                if (normalizedAfter.length < MIN_CARD_CHAR_LENGTH) {
                    qualityFilteredIds += suggestion.id
                    return@filter false
                }
                if (normalizedFront.isEmpty()) {
                    qualityFilteredIds += suggestion.id
                    return@filter false
                }

                val key = "$normalizedFront\t$normalizedAfter"
                if (!seenKeys.add(key)) {
                    qualityFilteredIds += suggestion.id
                    return@filter false
                }
                true
            }

            val flashcards = saveableSuggestions.map { suggestion ->
                CorrectionFlashcardSaveItem(
                    suggestionId = suggestion.id,
                    frontText = suggestion.nativeText.trim(),
                    backText = suggestion.afterText.trim(),
                    explanation = suggestion.explanation.trim()
                )
            }

            val zeroReason = when {
                flashcards.isNotEmpty() -> null
                safetyBlockedIds.isNotEmpty() && qualityFilteredIds.isEmpty() -> CorrectionSaveZeroReason.SAFETY_BLOCKED
                else -> CorrectionSaveZeroReason.QUALITY_FILTERED
            }

            PrepareCorrectionSaveRequestResult(
                request = CorrectionSaveRequest(
                    uid = uid,
                    lang = lang,
                    flashcards = flashcards,
                    requestedAt = requestedAt
                ),
                saveableSuggestionIds = saveableSuggestions.map { it.id },
                qualityFilteredSuggestionIds = qualityFilteredIds,
                safetyBlockedSuggestionIds = safetyBlockedIds,
                zeroReason = zeroReason
            )
        }
    }

    companion object {
        internal const val MIN_CARD_CHAR_LENGTH = 2
    }
}
