package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CachedCorrectionResult
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.CorrectionSuggestionCacheRepository
import javax.inject.Inject

/**
 * Returns cached suggestions only when the stored session fingerprint still matches
 * the current session snapshot. A null fingerprint is treated as "must regenerate".
 */
class GetCachedCorrectionUseCase @Inject constructor(
    private val repository: CorrectionSuggestionCacheRepository,
) {
    suspend operator fun invoke(
        uid: String?,
        language: LangCode,
        expectedFingerprint: Long?,
    ): CachedCorrectionResult? {
        if (uid.isNullOrBlank() || expectedFingerprint == null) return null
        val cached = repository.getCachedCorrection(uid = uid, language = language) ?: return null
        return cached.takeIf { it.sessionFingerprint == expectedFingerprint }
    }
}

class SaveCorrectionCacheUseCase @Inject constructor(
    private val repository: CorrectionSuggestionCacheRepository,
) {
    suspend operator fun invoke(
        uid: String?,
        language: LangCode,
        suggestions: List<com.app.umma.domain.model.correction.CorrectionSuggestion>,
        sessionFingerprint: Long?,
        primaryLanguage: LangCode?,
        cachedAt: Long,
    ) {
        if (uid.isNullOrBlank() || sessionFingerprint == null || suggestions.isEmpty()) return
        repository.saveCachedCorrection(
            uid = uid,
            cache = CachedCorrectionResult(
                language = language,
                suggestions = suggestions,
                sessionFingerprint = sessionFingerprint,
                primaryLanguage = primaryLanguage,
                cachedAt = cachedAt,
            ),
        )
    }
}

class ClearCorrectionCacheUseCase @Inject constructor(
    private val repository: CorrectionSuggestionCacheRepository,
) {
    suspend operator fun invoke(uid: String?, language: LangCode) {
        if (uid.isNullOrBlank()) return
        repository.clearCachedCorrection(uid = uid, language = language)
    }
}
