package com.example.umma.data.repository.correction

import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.generationConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction 전용 단발 AI 호출 어댑터입니다.
 *
 * Gemini Live (음성 스트리밍) 와 달리 교정은 LangState snapshot + 후보 목록을 묶어
 * 한 번에 JSON 응답을 받아 와야 하므로 별도 호출 경로가 필요하다.
 *
 * 이 인터페이스를 두는 이유는 두 가지다.
 *  - Repository / ViewModel 단위 테스트에서 Gemini 네트워크 없이 raw JSON 을 직접 주입할 수 있다.
 *  - 모델명·generationConfig 같은 implementation 디테일은 [GeminiCorrectionAiClient] 한 곳에만 모아
 *    이후 모델 교체 시 다른 계층을 건드리지 않게 한다.
 */
interface CorrectionAiClient {
    suspend fun generateJson(prompt: String): String
}

/**
 * Firebase AI 기반 [CorrectionAiClient] 구현체입니다.
 *
 * `responseMimeType = "application/json"` 을 명시해 모델이 markdown fence 없이
 * 순수 JSON 텍스트만 돌려주도록 강제한다.
 * [CorrectionAiResponseMapper] 는 markdown 이 섞여 들어오면 즉시 IllegalArgumentException 으로 막아 버리므로,
 * 안정적인 happy path 를 위해서는 응답 형식 강제가 필수다.
 */
@Singleton
class GeminiCorrectionAiClient @Inject constructor(
    // AIModule.provideFirebaseAI 가 주입하는 Google AI 백엔드 핸들. 호출 시점마다 모델을 새로 만든다.
    private val firebaseAI: FirebaseAI
) : CorrectionAiClient {

    override suspend fun generateJson(prompt: String): String {
        val model = firebaseAI.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
        val response = model.generateContent(prompt)
        return response.text
            ?: throw IllegalStateException("Gemini returned null text for correction prompt")
    }

    private companion object {
        // 단발 텍스트 + JSON 응답이 안정적인 모델. 향후 교체는 이 상수만 수정한다.
        const val MODEL_NAME = "gemini-2.5-flash"
    }
}
