package com.app.umma.data.repository.chatconversation

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.learningstate.LangCode
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gemini JSON 응답을 Chat conversation evidence domain 모델로 변환합니다.
 *
 * Gemini가 추천한 band는 [ChatConversationEvidence.debugRecommendedBand]에만 보관하고,
 * 실제 band 계산은 [com.app.umma.domain.usecase.chat.ApplyChatConversationEvidenceUseCase]가 담당합니다.
 */
class ChatConversationEvidenceResponseMapper @Inject constructor() {
    fun map(
        rawJson: String,
        selectedLang: LangCode,
        sourceSessionId: String
    ): ChatConversationEvidence {
        val dto = json.decodeFromString(ChatConversationEvidenceResponseDto.serializer(), rawJson)
        return ChatConversationEvidence(
            selectedLang = selectedLang,
            conversationSustainability = enumValue(dto.conversationSustainability),
            supportRequiredToContinue = enumValue(dto.supportRequiredToContinue),
            userContributionLevel = enumValue(dto.userContributionLevel),
            responseDifficultyFit = enumValue(dto.responseDifficultyFit),
            confidence = enumValue(dto.confidence),
            source = ChatConversationEvidenceSource.GeminiConversationAnalysis,
            sourceSessionId = sourceSessionId,
            reasonSummary = dto.reasonSummary.trim().take(MAX_REASON_LENGTH),
            debugRecommendedBand = enumValueOrNull(dto.debugRecommendedBand),
            updatedAt = System.currentTimeMillis(),
            expiresAt = null
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String): T {
        return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown ${T::class.simpleName}: $raw")
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String?): T? {
        if (raw.isNullOrBlank()) return null
        return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }

    private companion object {
        const val MAX_REASON_LENGTH = 240
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

@Serializable
private data class ChatConversationEvidenceResponseDto(
    val conversationSustainability: String,
    val supportRequiredToContinue: String,
    val userContributionLevel: String,
    val responseDifficultyFit: String,
    val confidence: String,
    val reasonSummary: String = "",
    val debugRecommendedBand: String? = null
)
