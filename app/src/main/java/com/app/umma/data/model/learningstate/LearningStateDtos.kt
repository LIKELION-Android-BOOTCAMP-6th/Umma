package com.app.umma.data.model.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.learningstate.VocabLevel
import kotlinx.serialization.Serializable

/**
 * DataStore에 저장할 학습 상태 전용 DTO 모음.
 *
 * Domain 모델은 짧고 읽기 쉬운 이름을 유지하고,
 * 이 DTO는 저장/복원 시 필요한 외부 필드 이름만 책임진다.
 */
@Serializable
data class UserLangPrefDto(
    // 외부 저장소에서 읽기 쉬운 필드명.
    val primaryLanguage: String,
    val selectedLearningLanguage: String,
    val learningLanguages: List<String> = emptyList(),
    val schemaVersion: Int,
    val updatedAt: Long? = null
)

@Serializable
data class InternalMetricsDto(
    val grammarAccuracy: Double,
    val vocabularyAppropriateness: Double,
    val lexicalDiversity: Double,
    val vocabularyLevel: String,
    val sentenceComplexity: Double,
    val speechRate: Double,
    val pauseFrequency: Double,
    val avgUtteranceLength: Double,
    val spokenNaturalness: Double,
    val naturalExpressionUsage: Double,
    val errorRecurrence: Double,
    val reviewRetention: Double
)

@Serializable
data class ExternalMetricsDto(
    val vocabularyLevel: String,
    val grammarAccuracy: Double,
    val expressionRange: Int,
    val fluencyScore: Double,
    val naturalnessScore: Double
)

@Serializable
data class LangStateDto(
    val language: String,
    val internalMetrics: InternalMetricsDto,
    val externalMetrics: ExternalMetricsDto,
    val schemaVersion: Int,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val lastAnalyzedAt: Long? = null,
    val lastAnalysisEventId: String? = null
)

@Serializable
data class DashSummaryDto(
    val language: String,
    val recentConversationMinutes: Int,
    val recentConversationTopic: String?,
    val correctionAvailable: Boolean,
    val dueFlashcards: Int,
    val recentSavedFlashcards: Int,
    val grammarScoreDelta: Int,
    val fluencyScoreDelta: Int,
    val vocabularyScoreDelta: Int,
    val naturalnessScoreDelta: Int,
    val schemaVersion: Int,
    val updatedAt: Long? = null
)

@Serializable
data class SessionSummaryDto(
    val language: String,
    val correctionAvailable: Boolean,
    val recentConversationMinutes: Int,
    val recentConversationTopic: String?,
    val updatedAt: Long? = null
)

@Serializable
data class FlashcardSummaryDto(
    val language: String,
    val dueFlashcards: Int,
    val recentSavedFlashcards: Int,
    val updatedAt: Long? = null
)

fun UserLangPref.toDto(): UserLangPrefDto {
    // Domain의 짧은 필드를 저장 친화적인 이름으로 풀어 쓴다.
    return UserLangPrefDto(
        primaryLanguage = nativeLang.code,
        selectedLearningLanguage = selectedLang.code,
        learningLanguages = learningLangs.map { it.code },
        schemaVersion = schema,
        updatedAt = updatedAt
    )
}
fun UserLangPrefDto.toDomain(): UserLangPref {
    // 저장값이 일부 비어 있어도 MVP 기본 언어로 복원되게 둔다.
    val restoredLearningLangs  = learningLanguages.mapNotNull(LangCode::fromCode).ifEmpty {
        listOf(LangCode.fromCode(selectedLearningLanguage) ?: LangCode.EN)
    }
    return UserLangPref(
        nativeLang = LangCode.fromCode(primaryLanguage) ?: LangCode.KO,
        primaryLang = restoredLearningLangs.first(),
        selectedLang = LangCode.fromCode(selectedLearningLanguage) ?: LangCode.EN,
        learningLangs = restoredLearningLangs,
        schema = schemaVersion,
        updatedAt = updatedAt
    )
}

fun InternalMetrics.toDto(): InternalMetricsDto {
    return InternalMetricsDto(
        grammarAccuracy = grammarAccuracy,
        vocabularyAppropriateness = vocabularyAppropriateness,
        lexicalDiversity = lexicalDiversity,
        vocabularyLevel = vocabularyLevel.name,
        sentenceComplexity = sentenceComplexity,
        speechRate = speechRate,
        pauseFrequency = pauseFrequency,
        avgUtteranceLength = avgUtteranceLength,
        spokenNaturalness = spokenNaturalness,
        naturalExpressionUsage = naturalExpressionUsage,
        errorRecurrence = errorRecurrence,
        reviewRetention = reviewRetention
    )
}

fun InternalMetricsDto.toDomain(): InternalMetrics {
    return InternalMetrics(
        grammarAccuracy = grammarAccuracy,
        vocabularyAppropriateness = vocabularyAppropriateness,
        lexicalDiversity = lexicalDiversity,
        vocabularyLevel = VocabLevel.valueOf(vocabularyLevel),
        sentenceComplexity = sentenceComplexity,
        speechRate = speechRate,
        pauseFrequency = pauseFrequency,
        avgUtteranceLength = avgUtteranceLength,
        spokenNaturalness = spokenNaturalness,
        naturalExpressionUsage = naturalExpressionUsage,
        errorRecurrence = errorRecurrence,
        reviewRetention = reviewRetention
    )
}

fun ExternalMetrics.toDto(): ExternalMetricsDto {
    return ExternalMetricsDto(
        vocabularyLevel = vocabularyLevel.name,
        grammarAccuracy = grammarAccuracy,
        expressionRange = expressionRange,
        fluencyScore = fluencyScore,
        naturalnessScore = naturalnessScore
    )
}

fun ExternalMetricsDto.toDomain(): ExternalMetrics {
    return ExternalMetrics(
        vocabularyLevel = VocabLevel.valueOf(vocabularyLevel),
        grammarAccuracy = grammarAccuracy,
        expressionRange = expressionRange,
        fluencyScore = fluencyScore,
        naturalnessScore = naturalnessScore
    )
}

fun LangState.toDto(): LangStateDto {
    return LangStateDto(
        language = lang.code,
        internalMetrics = internal.toDto(),
        externalMetrics = external.toDto(),
        schemaVersion = schema,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastAnalyzedAt = lastAnalyzedAt,
        lastAnalysisEventId = lastAnalysisEventId
    )
}

fun LangStateDto.toDomain(): LangState {
    // 분석 상태는 최신 저장 구조가 없을 때도 기본 언어로 안전하게 복원한다.
    return LangState(
        lang = LangCode.fromCode(language) ?: LangCode.EN,
        internal = internalMetrics.toDomain(),
        external = externalMetrics.toDomain(),
        schema = schemaVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastAnalyzedAt = lastAnalyzedAt,
        lastAnalysisEventId = lastAnalysisEventId
    )
}

fun DashSummary.toDto(): DashSummaryDto {
    return DashSummaryDto(
        language = lang.code,
        recentConversationMinutes = recentMinutes,
        recentConversationTopic = recentTopic,
        correctionAvailable = correctionAvailable,
        dueFlashcards = dueFlashcards,
        recentSavedFlashcards = savedFlashcards,
        grammarScoreDelta = grammarDelta,
        fluencyScoreDelta = fluencyDelta,
        vocabularyScoreDelta = vocabDelta,
        naturalnessScoreDelta = naturalnessDelta,
        schemaVersion = schema,
        updatedAt = updatedAt
    )
}

fun DashSummaryDto.toDomain(): DashSummary {
    // Dashboard는 언어별 요약만 읽기 때문에 여기서 바로 해당 스냅샷으로 되돌린다.
    return DashSummary(
        lang = LangCode.fromCode(language) ?: LangCode.EN,
        recentMinutes = recentConversationMinutes,
        recentTopic = recentConversationTopic,
        correctionAvailable = correctionAvailable,
        dueFlashcards = dueFlashcards,
        savedFlashcards = recentSavedFlashcards,
        grammarDelta = grammarScoreDelta,
        fluencyDelta = fluencyScoreDelta,
        vocabDelta = vocabularyScoreDelta,
        naturalnessDelta = naturalnessScoreDelta,
        schema = schemaVersion,
        updatedAt = updatedAt
    )
}

fun SessionSummary.toDto(): SessionSummaryDto {
    return SessionSummaryDto(
        language = lang.code,
        correctionAvailable = correctionAvailable,
        recentConversationMinutes = recentMinutes,
        recentConversationTopic = recentTopic,
        updatedAt = updatedAt
    )
}

fun SessionSummaryDto.toDomain(): SessionSummary {
    // Correction과 AI Chat 진입 판단용 최소 세션 정보만 복원한다.
    return SessionSummary(
        lang = LangCode.fromCode(language) ?: LangCode.EN,
        correctionAvailable = correctionAvailable,
        recentMinutes = recentConversationMinutes,
        recentTopic = recentConversationTopic,
        updatedAt = updatedAt
    )
}

fun FlashcardSummary.toDto(): FlashcardSummaryDto {
    return FlashcardSummaryDto(
        language = lang.code,
        dueFlashcards = dueFlashcards,
        recentSavedFlashcards = savedFlashcards,
        updatedAt = updatedAt
    )
}

fun FlashcardSummaryDto.toDomain(): FlashcardSummary {
    // 복습 카드 수만 보면 되는 화면을 위해 가벼운 요약으로 되돌린다.
    return FlashcardSummary(
        lang = LangCode.fromCode(language) ?: LangCode.EN,
        dueFlashcards = dueFlashcards,
        savedFlashcards = recentSavedFlashcards,
        updatedAt = updatedAt
    )
}
