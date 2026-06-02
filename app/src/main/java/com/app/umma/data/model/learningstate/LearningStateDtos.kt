package com.app.umma.data.model.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearningFocus
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence
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
data class MetricEvidenceDto(
    // 해당 metric에 대해 저장 가능한 관측이 몇 번 누적됐는지.
    val observedCount: Int,
    // 저장된 근거의 신뢰도. 0.0..1.0 밖이면 domain 복원에서 drop한다.
    val confidence: Double,
    // 어떤 분석 경로에서 온 근거인지 문자열 enum으로 저장한다.
    val sourceTypes: List<String> = emptyList(),
    // metric을 올릴지/내릴지/유지할지 나타내는 문자열 enum.
    val direction: String,
    // 같은 방향 근거가 몇 번 이어졌는지. CEFR level 급변 방어에 사용한다.
    val directionCount: Int,
    // 이 evidence가 마지막으로 관측된 시각.
    val lastObservedAt: Long? = null
)

@Serializable
data class LearningFocusDto(
    // 반복 약점 유형. unknown 문자열이면 domain 복원에서 drop한다.
    val type: String,
    // 같은 focus가 관측된 횟수.
    val observedCount: Int,
    // focus 신뢰도. 0.0..1.0 밖이면 prompt에 노출하지 않도록 drop한다.
    val confidence: Double,
    // focus가 처음 발견된 시각.
    val firstObservedAt: Long,
    // focus가 마지막으로 다시 발견된 시각.
    val lastObservedAt: Long
)

@Serializable
data class LangStateAnalysisMetaDto(
    // key는 LearningMetricKey.name이다. unknown key는 복원 시 제거한다.
    val metricEvidence: Map<String, MetricEvidenceDto> = emptyMap(),
    // 최근 반복 약점 후보. profile 단계에서 상위 1~2개만 사용한다.
    val activeFocus: List<LearningFocusDto> = emptyList(),
    // 마지막 correction/user/review signal 관측 시각.
    val lastSignalAt: Long? = null
)

@Serializable
data class LangStateDto(
    val language: String,
    val internalMetrics: InternalMetricsDto,
    val externalMetrics: ExternalMetricsDto,
    val analysisMeta: LangStateAnalysisMetaDto? = null,
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
    // primaryLanguage는 학습 기준 언어이고, selectedLearningLanguage는 현재 학습 대상 언어다.
    return UserLangPrefDto(
        primaryLanguage = primaryLang.code,
        selectedLearningLanguage = selectedLang.code,
        learningLanguages = learningLangs.map { it.code },
        schemaVersion = schema,
        updatedAt = updatedAt
    )
}
fun UserLangPrefDto.toDomain(): UserLangPref {
    // primaryLanguage는 기준 언어라 learningLanguages 밖이어도 정상이다. 포함 여부로 primary를 버리지 않는다.
    val primary = LangCode.fromCode(primaryLanguage) ?: LangCode.KO
    val rawSelected = LangCode.fromCode(selectedLearningLanguage)
    val validLearningLangs = learningLanguages.mapNotNull(LangCode::fromCode)
    val selected = when {
        rawSelected != null && rawSelected in validLearningLangs -> rawSelected
        rawSelected != null && validLearningLangs.isEmpty() -> rawSelected
        validLearningLangs.isNotEmpty() -> validLearningLangs.first()
        else -> LangCode.EN
    }
    val restoredLearningLangs = validLearningLangs
        .toMutableSet()
        .apply { add(selected) }
        .toList()
    return UserLangPref(
        primaryLang = primary,
        selectedLang = selected,
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

fun MetricEvidence.toDto(): MetricEvidenceDto {
    // Domain enum은 저장 안정성을 위해 enum name 문자열로만 내보낸다.
    // 이러면 Firestore/DataStore 모두 같은 DTO를 공유할 수 있다.
    return MetricEvidenceDto(
        observedCount = observedCount,
        confidence = confidence,
        sourceTypes = sourceTypes.map { it.name },
        direction = direction.name,
        directionCount = directionCount,
        lastObservedAt = lastObservedAt
    )
}

fun MetricEvidenceDto.toDomainOrNull(): MetricEvidence? {
    // confidence 범위가 깨진 저장값은 장기 능력 근거로 사용하지 않는다.
    // clamp로 조용히 살리면 잘못된 remote payload가 profile을 움직일 수 있어 drop한다.
    if (confidence !in 0.0..1.0) return null

    // sourceTypes는 일부 unknown 값이 섞일 수 있으므로, 유효한 source만 남긴다.
    // source가 비어도 evidence 자체는 유지할 수 있다. 방향/metric이 핵심 식별자이기 때문이다.
    val restoredSources = sourceTypes.mapNotNull { source ->
        enumValueOrNull<LearningSignalSource>(source)
    }.toSet()
    // direction은 score 이동 판단의 핵심이라 unknown이면 evidence 전체를 버린다.
    val restoredDirection = enumValueOrNull<EvidenceDirection>(direction) ?: return null

    return MetricEvidence(
        // 음수 count는 의미가 없으므로 최소 0으로 보정한다.
        observedCount = observedCount.coerceAtLeast(0),
        confidence = confidence,
        sourceTypes = restoredSources,
        direction = restoredDirection,
        // directionCount도 누적 횟수라 음수는 0으로 보정한다.
        directionCount = directionCount.coerceAtLeast(0),
        lastObservedAt = lastObservedAt
    )
}

fun LearningFocus.toDto(): LearningFocusDto {
    // activeFocus도 DTO에서는 enum name만 저장한다.
    // unknown enum 문자열은 앱 업데이트/실험 중 들어올 수 있어 복원 단계에서 방어한다.
    return LearningFocusDto(
        type = type.name,
        observedCount = observedCount,
        confidence = confidence,
        firstObservedAt = firstObservedAt,
        lastObservedAt = lastObservedAt
    )
}

fun LearningFocusDto.toDomainOrNull(): LearningFocus? {
    // active focus도 confidence 범위가 깨지면 prompt에 노출될 수 있으므로 domain 복원에서 제외한다.
    if (confidence !in 0.0..1.0) return null
    // focus type을 모르면 Chat/Correction이 무엇을 도와야 하는지 알 수 없으므로 drop한다.
    val restoredType = enumValueOrNull<LearningFocusType>(type) ?: return null

    return LearningFocus(
        type = restoredType,
        // 반복 횟수는 음수일 수 없으므로 오염 값은 0으로 보정한다.
        observedCount = observedCount.coerceAtLeast(0),
        confidence = confidence,
        firstObservedAt = firstObservedAt,
        lastObservedAt = lastObservedAt
    )
}

fun LangStateAnalysisMeta.toDto(): LangStateAnalysisMetaDto {
    // map key를 enum 그대로 저장하지 않고 name 문자열로 저장해 Firestore 필드 구조를 단순하게 유지한다.
    return LangStateAnalysisMetaDto(
        metricEvidence = metricEvidence.mapKeys { (key, _) -> key.name }
            .mapValues { (_, value) -> value.toDto() },
        activeFocus = activeFocus.map { it.toDto() },
        lastSignalAt = lastSignalAt
    )
}

fun LangStateAnalysisMetaDto.toDomain(): LangStateAnalysisMeta {
    // metricEvidence는 항목별로 오염될 수 있어 전체 meta를 버리지 않고 유효한 entry만 복원한다.
    val restoredEvidence = metricEvidence.mapNotNull { (rawKey, rawEvidence) ->
        // unknown metric key는 어떤 장기 지표에도 연결할 수 없으므로 drop한다.
        val key = enumValueOrNull<LearningMetricKey>(rawKey) ?: return@mapNotNull null
        // confidence/direction/source 방어를 통과한 evidence만 domain으로 올린다.
        val evidence = rawEvidence.toDomainOrNull() ?: return@mapNotNull null
        key to evidence
    }.toMap()
    // focus도 항목별로 검증해 유효 focus만 남긴다.
    val restoredFocus = activeFocus.mapNotNull { focus ->
        focus.toDomainOrNull()
    }

    return LangStateAnalysisMeta(
        metricEvidence = restoredEvidence,
        activeFocus = restoredFocus,
        lastSignalAt = lastSignalAt
    )
}

fun LangState.toDto(): LangStateDto {
    return LangStateDto(
        language = lang.code,
        internalMetrics = internal.toDto(),
        externalMetrics = external.toDto(),
        analysisMeta = analysisMeta.toDto(),
        schemaVersion = schema,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastAnalyzedAt = lastAnalyzedAt,
        lastAnalysisEventId = lastAnalysisEventId
    )
}

fun LangStateDto.toDomain(): LangState {
    // schema v1에는 analysisMeta가 없으므로 안전한 빈 evidence/focus로 복원한다.
    // invalid enum/confidence는 DTO mapper에서 drop되어 domain 모델에 올라오지 않는다.
    val restoredAnalysisMeta = analysisMeta?.toDomain() ?: LangStateAnalysisMeta.initial()
    // 분석 상태는 최신 저장 구조가 없을 때도 안전 기본값으로 복원한다.
    // 여기의 EN은 primaryLang이 아니라 오염된 LangState key에 대한 fallback이다.
    return LangState(
        lang = LangCode.fromCode(language) ?: LangCode.EN,
        internal = internalMetrics.toDomain(),
        external = externalMetrics.toDomain(),
        analysisMeta = restoredAnalysisMeta,
        schema = schemaVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastAnalyzedAt = lastAnalyzedAt,
        lastAnalysisEventId = lastAnalysisEventId
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? {
    // Firestore/DataStore에 남은 unknown enum 문자열은 저장 모델로 복원하지 않는다.
    // enumValueOf를 직접 쓰면 한 항목의 오염이 전체 LangState 복원을 실패시킬 수 있다.
    return enumValues<T>().firstOrNull { it.name == name }
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
