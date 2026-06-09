package com.app.umma.domain.model.correction

import com.app.umma.domain.model.learningstate.LangCode

/**
 * Unsaved Correction suggestions persisted for the current `(uid, lang)` slot.
 *
 * The cache is only eligible for restore when the session fingerprint matches the
 * current `SessionSummary.updatedAt`, so the ViewModel never has to infer staleness
 * from the suggestions payload itself.
 */
data class CachedCorrectionResult(
    val language: LangCode,
    val suggestions: List<CorrectionSuggestion>,
    val sessionFingerprint: Long,
    val primaryLanguage: LangCode?,
    val cachedAt: Long,
)
