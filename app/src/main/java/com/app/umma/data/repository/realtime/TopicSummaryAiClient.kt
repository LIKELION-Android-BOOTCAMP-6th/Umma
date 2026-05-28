package com.app.umma.data.repository.realtime

import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.generationConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 최근 대화 세션 주제 요약 전용 단발 AI 호출 어댑터입니다.
 *
 * [com.app.umma.data.repository.correction.CorrectionAiClient] 와 동일 구조로 설계됐다.
 * - 인터페이스를 둠으로써 Repository/UseCase 단위 테스트에서 AI 없이 fake JSON 을 주입할 수 있다.
 * - 모델명·generationConfig 같은 implementation 디테일은 [GeminiTopicSummaryAiClient] 한 곳에만 모아
 *   이후 모델 교체 시 다른 계층을 건드리지 않게 한다.
 */
interface TopicSummaryAiClient {
    /**
     * 세션 텍스트 목록을 받아 각 세션의 주제 요약 JSON 을 반환합니다.
     *
     * @param prompt 세션별 turn 텍스트를 포함한 프롬프트
     * @return `{"summaries": ["...", ...]}` 형태의 JSON 문자열
     */
    suspend fun generateJson(prompt: String): String
}

/**
 * Firebase AI 기반 [TopicSummaryAiClient] 구현체입니다.
 *
 * `responseMimeType = "application/json"` 을 명시해 markdown fence 없이 순수 JSON 만 받는다.
 */
@Singleton
class GeminiTopicSummaryAiClient @Inject constructor(
    // AIModule.provideFirebaseAI 가 주입하는 Google AI 백엔드 핸들.
    private val firebaseAI: FirebaseAI
) : TopicSummaryAiClient {

    override suspend fun generateJson(prompt: String): String {
        val model = firebaseAI.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
        val response = model.generateContent(prompt)
        return response.text
            ?: throw IllegalStateException("Gemini returned null text for topic summary prompt")
    }

    private companion object {
        // 단발 텍스트 + JSON 응답이 안정적인 모델. 교체 시 이 상수만 수정한다.
        const val MODEL_NAME = "gemini-2.5-flash"
    }
}
