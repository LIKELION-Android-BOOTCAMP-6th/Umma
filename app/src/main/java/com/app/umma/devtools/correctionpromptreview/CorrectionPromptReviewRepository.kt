package com.app.umma.devtools.correctionpromptreview

/**
 * Development-only storage for Correction quality review reports.
 *
 * Failures in this repository must never change the production Correction flow; callers only use
 * the result to update the report button state.
 */
interface CorrectionPromptReviewRepository {
    fun isEnabled(): Boolean

    suspend fun report(snapshot: CorrectionPromptReviewSnapshot): Result<Unit>
}
