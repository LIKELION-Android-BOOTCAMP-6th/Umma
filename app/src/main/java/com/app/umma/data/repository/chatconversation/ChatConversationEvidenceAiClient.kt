package com.app.umma.data.repository.chatconversation

import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.generationConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat conversation evidence 분석용 단발 AI 호출 어댑터입니다.
 *
 * OpenAI Realtime은 대화 생성만 담당하고, 세션 후 능력 근거 분석은 Gemini JSON 호출로 분리합니다.
 */
interface ChatConversationEvidenceAiClient {
    suspend fun generateJson(prompt: String): String
}

/**
 * Firebase AI 기반 [ChatConversationEvidenceAiClient] 구현체입니다.
 */
@Singleton
class GeminiChatConversationEvidenceAiClient @Inject constructor(
    private val firebaseAI: FirebaseAI
) : ChatConversationEvidenceAiClient {

    override suspend fun generateJson(prompt: String): String {
        val model = firebaseAI.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
        val response = model.generateContent(prompt)
        return response.text
            ?: throw IllegalStateException("Gemini returned null text for chat conversation evidence prompt")
    }

    private companion object {
        const val MODEL_NAME = "gemini-2.5-flash"
    }
}
