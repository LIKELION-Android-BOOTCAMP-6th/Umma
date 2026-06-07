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
 * Gemini가 추천한 band는 [ChatConversationEvidence.debugRecommendedBand]에만 보관한다.
 * 실제 Chat band는 LangState에 저장된 summary를 domain policy가 다시 해석해 계산한다.
 */
class ChatConversationEvidenceResponseMapper @Inject constructor() {
    fun map(
        rawJson: String,
        selectedLang: LangCode,
        sourceSessionId: String
    ): ChatConversationEvidence {
        val dto = decodeDto(rawJson)
        return ChatConversationEvidence(
            selectedLang = selectedLang,
            targetLanguageComprehension = enumValue(dto.targetLanguageComprehension),
            targetLanguageProduction = enumValue(dto.targetLanguageProduction),
            supportLanguageDependence = enumValue(dto.supportLanguageDependence),
            aiScaffoldingDependence = enumValue(dto.aiScaffoldingDependence),
            conversationSustainability = enumValue(dto.conversationSustainability),
            consistency = enumValue(dto.consistency),
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

    private fun decodeDto(rawJson: String): ChatConversationEvidenceResponseDto {
        return runCatching {
            json.decodeFromString(ChatConversationEvidenceResponseDto.serializer(), rawJson)
        }.getOrElse { firstError ->
            // Gemini occasionally returns invalid JSON only because reasonSummary contains unescaped quotes
            // around transcript examples. The summary is debug text, so normalize that one field instead of
            // dropping the whole ability analysis and losing the LangState update.
            val sanitizedJson = sanitizeReasonSummaryValue(rawJson) ?: throw firstError
            json.decodeFromString(ChatConversationEvidenceResponseDto.serializer(), sanitizedJson)
        }
    }

    private fun sanitizeReasonSummaryValue(rawJson: String): String? {
        val keyIndex = rawJson.indexOf(REASON_SUMMARY_KEY)
        if (keyIndex < 0) return null

        val colonIndex = rawJson.indexOf(':', startIndex = keyIndex + REASON_SUMMARY_KEY.length)
        if (colonIndex < 0) return null

        val valueStartQuote = rawJson.indexOf('"', startIndex = colonIndex + 1)
        if (valueStartQuote < 0) return null

        val nextFieldMatch = NEXT_DEBUG_BAND_FIELD.find(rawJson, startIndex = valueStartQuote + 1)
            ?: return null

        val rawValueWithPossibleClosingQuote = rawJson
            .substring(valueStartQuote + 1, nextFieldMatch.range.first)
            .trimEnd()
            .removeSuffix(",")
            .trimEnd()
        val rawValue = rawValueWithPossibleClosingQuote
            .removeSuffix("\"")
            .trim()

        val escapedValue = rawValue
            .replace("\\", "\\\\")
            .replace("\"", "")
            .replace("\n", " ")
            .replace("\r", " ")
            .take(MAX_REASON_LENGTH)

        return buildString {
            append(rawJson.substring(0, valueStartQuote + 1))
            append(escapedValue)
            append('"')
            append(rawJson.substring(nextFieldMatch.range.first))
        }
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
        const val REASON_SUMMARY_KEY = "\"reasonSummary\""
        val NEXT_DEBUG_BAND_FIELD = Regex(",\\s*\"debugRecommendedBand\"\\s*:")
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

@Serializable
private data class ChatConversationEvidenceResponseDto(
    val targetLanguageComprehension: String,
    val targetLanguageProduction: String,
    val supportLanguageDependence: String,
    val aiScaffoldingDependence: String,
    val conversationSustainability: String,
    val consistency: String,
    val responseDifficultyFit: String,
    val confidence: String,
    val reasonSummary: String = "",
    val debugRecommendedBand: String? = null
)
