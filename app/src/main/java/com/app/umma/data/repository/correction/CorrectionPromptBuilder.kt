package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import javax.inject.Inject

/**
 * Correction MVP 프롬프트 빌더.
 *
 * 설계서 (COR-002) 의 생성 정책을 한 문자열로 모은다.
 *  - 학습자 현재 수준(LangState snapshot) 반영
 *  - 의미 보존 + JSON 응답 + candidateId 유지 + 짧은 설명 지시
 *
 * 프롬프트 문자열을 [CorrectionRepositoryImpl] 안에 직접 두지 않고 빌더로 분리한 이유는,
 * 응답 schema 가 [CorrectionAiResponseMapper] 가 받는 DTO 와 한 글자라도 어긋나면 happy path 가 통째로 깨지기 때문이다.
 * 두 파일이 한 디렉토리에 같이 있어야 한쪽이 바뀔 때 다른 쪽을 같이 보게 된다.
 */
class CorrectionPromptBuilder @Inject constructor() {

    fun build(input: GenerateSuggestionsInput): String {
        val lang = input.langState.lang
        val external = input.langState.external

        return buildString {
            appendLine("You are a language correction assistant for a learner of ${lang.code}.")
            appendLine("Learner profile (use this to calibrate difficulty, do NOT mention numbers in the reply):")
            appendLine("- CEFR vocabulary level: ${external.vocabularyLevel.name}")
            appendLine("- Grammar accuracy: ${"%.2f".format(external.grammarAccuracy)}")
            appendLine("- Naturalness score: ${"%.2f".format(external.naturalnessScore)}")
            appendLine()
            appendLine("Task: For each candidate sentence below, return one corrected version that preserves the speaker's meaning and is natural at the learner's level.")
            appendLine()
            appendLine("Candidates:")
            input.candidates.forEach { candidate ->
                appendLine("- candidateId: ${candidate.id}")
                appendLine("  sourceText: ${candidate.sourceText}")
                val context = candidate.assistantContext?.takeIf { it.isNotBlank() }
                if (context != null) {
                    appendLine("  assistantContext: ${context.replace("\n", " ")}")
                }
            }
            appendLine()
            appendLine("Response: Return ONLY one valid JSON object, no markdown fences, no commentary. Schema:")
            appendLine("""{"suggestions":[{"candidateId":"...","nativeText":"...","afterText":"...","explanation":"..."}]}""")
            appendLine("Rules:")
            appendLine("- candidateId: COPY EXACTLY from the candidates above. Do not invent new ids.")
            appendLine("- nativeText: Korean (ko) translation of the corrected sentence.")
            appendLine("- afterText: the corrected sentence in ${lang.code}.")
            appendLine("- explanation: a short Korean tip (under 60 chars).")
            appendLine("- Emit one suggestion per candidate. Skip a candidate only if no correction is needed.")
        }
    }
}
