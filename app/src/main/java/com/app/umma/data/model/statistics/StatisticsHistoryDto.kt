package com.app.umma.data.model.statistics

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.statistics.StatisticsHistory
import kotlinx.serialization.Serializable

/**
 * StatisticsHistory를 저장소 친화적인 외부 DTO로 풀어 쓴다.
 *
 * Firestore 문서와 Room 캐시가 같은 필드 의미를 공유하되,
 * domain model은 저장 계층의 필드명에 종속되지 않도록 분리한다.
 */
@Serializable
data class StatisticsHistoryDto(
    val id: String,
    val userId: String,
    val language: String,
    val recordedAt: Long,
    val conversationBand: String? = null,
    val vocabularyLevel: String,
    val grammarAccuracy: Double,
    val expressionRange: Int,
    val fluencyScore: Double,
    val naturalnessScore: Double,
    val sourceEventId: String,
    val syncStatus: String
)

fun StatisticsHistory.toDto(): StatisticsHistoryDto {
    return StatisticsHistoryDto(
        id = id,
        userId = userId,
        language = language.code,
        recordedAt = recordedAt,
        conversationBand = conversationBand?.name,
        vocabularyLevel = vocabularyLevel.name,
        grammarAccuracy = grammarAccuracy,
        expressionRange = expressionRange,
        fluencyScore = fluencyScore,
        naturalnessScore = naturalnessScore,
        sourceEventId = sourceEventId,
        syncStatus = syncStatus.name
    )
}

fun StatisticsHistoryDto.toDomain(): StatisticsHistory {
    return StatisticsHistory(
        id = id,
        userId = userId,
        language = LangCode.fromCode(language) ?: LangCode.EN,
        recordedAt = recordedAt,
        conversationBand = conversationBand.toConversationAbilityBandOrNull(),
        vocabularyLevel = VocabLevel.valueOf(vocabularyLevel),
        grammarAccuracy = grammarAccuracy,
        expressionRange = expressionRange,
        fluencyScore = fluencyScore,
        naturalnessScore = naturalnessScore,
        sourceEventId = sourceEventId,
        syncStatus = SyncStatus.valueOf(syncStatus)
    )
}

private fun String?.toConversationAbilityBandOrNull(): ConversationAbilityBand? {
    // 새로 추가된 저장 필드는 오래된/손상된 remote 값이 섞여도 통계 화면 전체를 깨지 않게 건너뛴다.
    return this?.let { rawValue ->
        runCatching { ConversationAbilityBand.valueOf(rawValue) }.getOrNull()
    }
}

fun StatisticsHistoryDto.toFirestoreMap(): Map<String, Any> {
    return buildMap {
        put("id", id)
        put("userId", userId)
        put("language", language)
        put("recordedAt", recordedAt)
        conversationBand?.let { put("conversationBand", it) }
        put("vocabularyLevel", vocabularyLevel)
        put("grammarAccuracy", grammarAccuracy)
        put("expressionRange", expressionRange)
        put("fluencyScore", fluencyScore)
        put("naturalnessScore", naturalnessScore)
        put("sourceEventId", sourceEventId)
        put("syncStatus", syncStatus)
    }
}
