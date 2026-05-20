package com.example.umma.data.repository.correction

import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 실제 AI 교정 응답을 domain 계약인 [CorrectionSuggestion] 목록으로 변환하는 data 계층 mapper 입니다.
 *
 * UI와 domain은 AI 원문 JSON 구조를 알면 안 되므로, 응답 파싱과 필수 필드 검증은 이 계층에 둔다.
 * User Flow에서 실제 AI API를 연결할 때도 Repository는 이 mapper를 거쳐 동일한 결과 계약을 반환한다.
 *
 * 파싱 흐름은 다음 순서를 따른다.
 * 1. AI가 반환한 raw JSON 문자열을 data DTO([CorrectionAiResponseDto])로 디코딩한다.
 * 2. DTO의 candidateId를 이미 추출된 [GenerateSuggestionsInput.candidates]와 다시 매칭한다.
 * 3. 후보의 원문/언어/turn 정보와 AI가 만든 nativeText, afterText, explanation을 합쳐 domain 모델을 만든다.
 * 4. 필수 필드가 비어 있거나 candidateId가 맞지 않으면 실패로 처리해 화면의 Error/Retry 흐름으로 이어지게 한다.
 */
internal class CorrectionAiResponseMapper @Inject constructor() {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun map(
        rawJson: String,
        input: GenerateSuggestionsInput
    ): List<CorrectionSuggestion> {
        // 1) 실제 AI API는 문자열 JSON을 돌려준다. Repository는 그 원문을 이 mapper에 넘긴다.
        // 여기서만 JSON 구조를 알고, domain/usecase/presentation은 raw JSON에 의존하지 않는다.
        val response = json.decodeFromString(CorrectionAiResponseDto.serializer(), rawJson)

        // 2) AI 응답의 candidateId가 어떤 원본 user turn에서 나온 결과인지 확인하기 위해
        // 후보 목록을 id 기준으로 다시 찾을 수 있게 만든다.
        val candidatesById = input.candidates.associateBy { it.id }

        return response.suggestions.map { item ->
            // 3) AI가 알 수 없는 후보 ID를 돌려주면 beforeText/sourceTurnIndex를 보장할 수 없다.
            // 출처가 깨진 교정 결과는 저장하면 안 되므로 파싱 실패로 다룬다.
            val candidate = requireNotNull(candidatesById[item.candidateId]) {
                "unknown correction candidate id: ${item.candidateId}"
            }

            // 현재 선택 언어와 후보 언어가 다르면 같은 교정 세션 결과로 사용할 수 없다.
            require(candidate.lang == input.langState.lang) {
                "candidate language must match langState language"
            }

            CorrectionSuggestion(
                // 4) domain 모델 id는 앱 내부 추적용이다. AI 응답의 candidateId를 그대로 쓰지 않고
                // Correction 결과임을 구분할 수 있도록 prefix를 붙인다.
                id = "corr-${candidate.id}",
                lang = candidate.lang,
                sourceCandidateIds = listOf(candidate.id),
                sourceTurnIndex = candidate.sourceTurnIndex,
                // beforeText는 AI가 만든 값이 아니라 후보 추출 단계의 원문을 신뢰한다.
                // 이렇게 해야 AI 응답이 원문을 변형해도 카드의 교정 전 문장이 흔들리지 않는다.
                beforeText = candidate.sourceText,
                // 아래 세 필드는 AI가 생성해야 하는 필수 결과다.
                // 하나라도 공백이면 화면 카드와 Flashcard 저장 모두 불완전해지므로 실패시킨다.
                nativeText = item.nativeText.requireFilled("nativeText"),
                afterText = item.afterText.requireFilled("afterText"),
                explanation = item.explanation.requireFilled("explanation")
            )
        }
    }

    private fun String.requireFilled(fieldName: String): String {
        // 화면 카드와 Flashcard 저장에 필요한 필수 필드는 공백만 있어도 실패로 간주한다.
        return trim().also { value ->
            require(value.isNotEmpty()) {
                "correction AI response field '$fieldName' must not be blank"
            }
        }
    }
}

/**
 * Correction AI 응답의 최상위 DTO입니다.
 *
 * 실제 prompt/schema는 User Flow 구현에서 조정하더라도, data 계층은 이 최소 구조를 기준으로
 * 응답을 검증한 뒤 domain 모델로 변환한다.
 *
 * 기대 JSON 예시는 다음과 같다.
 *
 * {
 *   "suggestions": [
 *     {
 *       "candidateId": "en-0-a",
 *       "nativeText": "나는 학교에 간다",
 *       "afterText": "I go to school.",
 *       "explanation": "Use 'go to school' instead of 'go school'."
 *     }
 *   ]
 * }
 */
@Serializable
private data class CorrectionAiResponseDto(
    @SerialName("suggestions")
    val suggestions: List<CorrectionAiSuggestionDto> = emptyList()
)

/**
 * AI가 후보 하나에 대해 반환해야 하는 최소 교정 결과 DTO입니다.
 */
@Serializable
private data class CorrectionAiSuggestionDto(
    @SerialName("candidateId")
    val candidateId: String,
    @SerialName("nativeText")
    val nativeText: String,
    @SerialName("afterText")
    val afterText: String,
    @SerialName("explanation")
    val explanation: String
)
