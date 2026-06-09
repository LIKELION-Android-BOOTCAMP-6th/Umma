package com.app.umma.devtools.correctionpromptreview

import javax.inject.Inject

/**
 * Presentation-facing entry point for dev-only Correction review reports.
 */
class ReportCorrectionPromptReviewUseCase @Inject constructor(
    private val repository: CorrectionPromptReviewRepository
) {
    fun isEnabled(): Boolean = repository.isEnabled()

    suspend operator fun invoke(snapshot: CorrectionPromptReviewSnapshot): Result<Unit> {
        return repository.report(snapshot)
    }
}
