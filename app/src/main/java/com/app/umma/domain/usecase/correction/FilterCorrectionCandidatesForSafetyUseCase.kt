package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import javax.inject.Inject

/**
 * 후보 추출 직후 명백히 유해한 후보를 교정 AI 요청에서 제외한다.
 */
class FilterCorrectionCandidatesForSafetyUseCase @Inject constructor(
    private val safetyPolicy: CorrectionSafetyPolicy
) {

    operator fun invoke(
        candidates: List<CorrectionCandidate>
    ): FilteredCorrectionCandidatesResult {
        val allowed = mutableListOf<CorrectionCandidate>()
        val blocked = mutableListOf<CorrectionCandidate>()

        candidates.forEach { candidate ->
            if (safetyPolicy.isBlocked(candidate)) {
                blocked += candidate
            } else {
                allowed += candidate
            }
        }

        return FilteredCorrectionCandidatesResult(
            allowedCandidates = allowed,
            blockedCandidates = blocked
        )
    }
}

data class FilteredCorrectionCandidatesResult(
    val allowedCandidates: List<CorrectionCandidate>,
    val blockedCandidates: List<CorrectionCandidate>
) {
    val blockedCount: Int
        get() = blockedCandidates.size
}
