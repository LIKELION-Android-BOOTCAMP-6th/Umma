package com.app.umma.data.model.correction

import com.app.umma.domain.model.correction.CachedCorrectionResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.CorrectionLearningSignal
import com.app.umma.domain.model.learningstate.CorrectionEditSpan
import com.app.umma.domain.model.learningstate.CorrectionImprovementType
import com.app.umma.domain.model.learningstate.CorrectionIssueCategory
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LanguageFeatureSignal
import com.app.umma.domain.model.learningstate.SpokenRegister
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import kotlinx.serialization.Serializable

@Serializable
data class CorrectionSuggestionCacheEnvelopeDto(
    val language: String,
    val sessionFingerprint: Long,
    val primaryLanguage: String? = null,
    val cachedAt: Long,
    val suggestions: List<CorrectionSuggestionCacheDto>,
)

@Serializable
data class CorrectionSuggestionCacheDto(
    val id: String,
    val lang: String,
    val sourceCandidateIds: List<String>,
    val sourceTurnIndex: Int,
    val beforeText: String,
    val nativeText: String,
    val afterText: String,
    val explanation: String,
    val learningSignal: CorrectionLearningSignalCacheDto? = null,
    val sourceLang: String? = null,
)

@Serializable
data class CorrectionLearningSignalCacheDto(
    val candidateId: String,
    val sourceTurnId: String? = null,
    val sourceTurnIndex: Int,
    val sourceText: String,
    val correctedText: String,
    val issueCategories: List<String>,
    val languageFeatures: List<LanguageFeatureSignalCacheDto>,
    val improvementTypes: List<String>,
    val editSpans: List<CorrectionEditSpanCacheDto>,
    val register: String,
    val severity: String,
    val meaningPreserved: Boolean,
    val confidence: Double? = null,
)

@Serializable
data class LanguageFeatureSignalCacheDto(
    val lang: String,
    val featureKey: String,
)

@Serializable
data class CorrectionEditSpanCacheDto(
    val sourceFragment: String,
    val correctedFragment: String,
    val issueCategory: String,
    val languageFeatureKey: String? = null,
    val improvementType: String,
)

internal fun CachedCorrectionResult.toCacheEnvelopeDto(): CorrectionSuggestionCacheEnvelopeDto =
    CorrectionSuggestionCacheEnvelopeDto(
        language = language.code,
        sessionFingerprint = sessionFingerprint,
        primaryLanguage = primaryLanguage?.code,
        cachedAt = cachedAt,
        suggestions = suggestions.map(CorrectionSuggestion::toCacheDto),
    )

internal fun CorrectionSuggestionCacheEnvelopeDto.toDomain(): CachedCorrectionResult =
    CachedCorrectionResult(
        language = language.toLangCode("language"),
        suggestions = suggestions.map(CorrectionSuggestionCacheDto::toDomain),
        sessionFingerprint = sessionFingerprint,
        primaryLanguage = primaryLanguage?.toLangCode("primaryLanguage"),
        cachedAt = cachedAt,
    )

internal fun CorrectionSuggestion.toCacheDto(): CorrectionSuggestionCacheDto =
    CorrectionSuggestionCacheDto(
        id = id,
        lang = lang.code,
        sourceCandidateIds = sourceCandidateIds,
        sourceTurnIndex = sourceTurnIndex,
        beforeText = beforeText,
        nativeText = nativeText,
        afterText = afterText,
        explanation = explanation,
        learningSignal = learningSignal?.toCacheDto(),
        sourceLang = sourceLang?.code,
    )

internal fun CorrectionSuggestionCacheDto.toDomain(): CorrectionSuggestion =
    CorrectionSuggestion(
        id = id,
        lang = lang.toLangCode("lang"),
        sourceCandidateIds = sourceCandidateIds,
        sourceTurnIndex = sourceTurnIndex,
        beforeText = beforeText,
        nativeText = nativeText,
        afterText = afterText,
        explanation = explanation,
        learningSignal = learningSignal?.toDomain(),
        sourceLang = sourceLang?.toLangCode("sourceLang"),
    )

private fun CorrectionLearningSignal.toCacheDto(): CorrectionLearningSignalCacheDto =
    CorrectionLearningSignalCacheDto(
        candidateId = candidateId,
        sourceTurnId = sourceTurnId,
        sourceTurnIndex = sourceTurnIndex,
        sourceText = sourceText,
        correctedText = correctedText,
        issueCategories = issueCategories.map(Enum<*>::name),
        languageFeatures = languageFeatures.map(LanguageFeatureSignal::toCacheDto),
        improvementTypes = improvementTypes.map(Enum<*>::name),
        editSpans = editSpans.map(CorrectionEditSpan::toCacheDto),
        register = register.name,
        severity = severity.name,
        meaningPreserved = meaningPreserved,
        confidence = confidence,
    )

private fun CorrectionLearningSignalCacheDto.toDomain(): CorrectionLearningSignal =
    CorrectionLearningSignal(
        candidateId = candidateId,
        sourceTurnId = sourceTurnId,
        sourceTurnIndex = sourceTurnIndex,
        sourceText = sourceText,
        correctedText = correctedText,
        issueCategories = issueCategories.map { enumValueOf<CorrectionIssueCategory>(it) },
        languageFeatures = languageFeatures.map(LanguageFeatureSignalCacheDto::toDomain),
        improvementTypes = improvementTypes.map { enumValueOf<CorrectionImprovementType>(it) },
        editSpans = editSpans.map(CorrectionEditSpanCacheDto::toDomain),
        register = enumValueOf<SpokenRegister>(register),
        severity = enumValueOf<CorrectionSeverity>(severity),
        meaningPreserved = meaningPreserved,
        confidence = confidence,
    )

private fun LanguageFeatureSignal.toCacheDto(): LanguageFeatureSignalCacheDto =
    LanguageFeatureSignalCacheDto(
        lang = lang.code,
        featureKey = featureKey,
    )

private fun LanguageFeatureSignalCacheDto.toDomain(): LanguageFeatureSignal =
    LanguageFeatureSignal(
        lang = lang.toLangCode("languageFeatures.lang"),
        featureKey = featureKey,
    )

private fun CorrectionEditSpan.toCacheDto(): CorrectionEditSpanCacheDto =
    CorrectionEditSpanCacheDto(
        sourceFragment = sourceFragment,
        correctedFragment = correctedFragment,
        issueCategory = issueCategory.name,
        languageFeatureKey = languageFeatureKey,
        improvementType = improvementType.name,
    )

private fun CorrectionEditSpanCacheDto.toDomain(): CorrectionEditSpan =
    CorrectionEditSpan(
        sourceFragment = sourceFragment,
        correctedFragment = correctedFragment,
        issueCategory = enumValueOf<CorrectionIssueCategory>(issueCategory),
        languageFeatureKey = languageFeatureKey,
        improvementType = enumValueOf<CorrectionImprovementType>(improvementType),
    )

private fun String.toLangCode(fieldName: String): LangCode =
    LangCode.fromCode(this)
        ?: throw IllegalArgumentException("Unknown LangCode for $fieldName: $this")
