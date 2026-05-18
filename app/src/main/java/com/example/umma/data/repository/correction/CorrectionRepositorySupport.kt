package com.example.umma.data.repository.correction

import com.example.umma.domain.model.correction.CorrectionCandidate
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.correction.GenerateSuggestionsInput

/**
 * Correction Repository 구현체들이 공유하는 deterministic 변환 규칙입니다.
 *
 * 이 객체는 실제 AI 응답을 대신하는 "더미 변환기"가 아니다.
 * 지금 단계에서는 교정 결과의 계약 모양을 먼저 고정해 두고,
 * repository 구현체가 같은 입력에 대해 같은 출력 형태를 내도록 맞추기 위한 보조 계층이다.
 *
 * 즉, 화면과 저장소가 기대하는 결과 구조를 안정적으로 검증하기 위한 내부 공통 규칙 모음이다.
 */
internal object CorrectionRepositorySupport {

    fun buildSuggestions(input: GenerateSuggestionsInput): List<CorrectionSuggestion> {
        // 후보가 없으면 결과도 비어 있어야 화면이 Empty 상태로 자연스럽게 이어진다.
        if (input.candidates.isEmpty()) return emptyList()

        return input.candidates.map { candidate ->
            // 현재 선택 언어와 LangState snapshot 언어가 다르면, 같은 교정 세션으로 묶지 않는다.
            // 이 검사를 통과해야만 화면 카드와 저장 계약이 한 언어 컨텍스트 안에서 일관되게 유지된다.
            require(candidate.lang == input.langState.lang) {
                "candidate language must match langState language"
            }

            buildSuggestion(candidate, input)
        }
    }

    private fun buildSuggestion(
        candidate: CorrectionCandidate,
        input: GenerateSuggestionsInput
    ): CorrectionSuggestion {
        // 원문은 카드의 beforeText로 남기고, afterText는 현재 단계에서 간단한 정규화만 수행한다.
        // 실제 AI 교정 문장은 이후 단계에서 이 자리를 대체하게 된다.
        val afterText = normalizeText(candidate.sourceText)

        // 설명은 LangState snapshot 을 보고 생성한다.
        // 그래서 카드 생성 결과가 "현재 언어 상태 기준"이라는 계약을 계속 유지할 수 있다.
        val explanation = buildExplanation(candidate, input)

        return CorrectionSuggestion(
            id = "corr-${candidate.id}",
            lang = candidate.lang,
            sourceCandidateIds = listOf(candidate.id),
            sourceTurnIndex = candidate.sourceTurnIndex,
            beforeText = candidate.sourceText,
            // 지금은 의미 보조용 문장을 그대로 전달해 계약을 맞춘다.
            // 실제 번역/의미 생성은 AI 단계에서 이 필드를 더 정교하게 채울 수 있다.
            nativeText = buildNativeText(candidate),
            afterText = afterText,
            explanation = explanation
        )
    }

    private fun buildExplanation(
        candidate: CorrectionCandidate,
        input: GenerateSuggestionsInput
    ): String {
        val level = input.langState.external.vocabularyLevel.name
        val contextHint = candidate.assistantContext
            ?.takeIf { it.isNotBlank() }
            ?.let { " / context-aware" }
            ?: ""

        return "LangState $level snapshot 기준 교정 제안$contextHint"
    }

    private fun buildNativeText(candidate: CorrectionCandidate): String {
        // 실제 번역/의미 생성은 AI 응답으로 대체될 영역이다.
        // 현재는 Flashcard 저장 계약이 앞면/뒷면 구조를 가질 수 있도록 원문을 그대로 전달한다.
        return candidate.sourceText.trim()
    }

    private fun normalizeText(text: String): String {
        // 카드의 afterText가 너무 들쭉날쭉하지 않도록 최소한의 문장형만 맞춘다.
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed

        val capitalized = trimmed.replaceFirstChar { firstChar ->
            if (firstChar.isLowerCase()) firstChar.titlecase() else firstChar.toString()
        }

        return when (capitalized.last()) {
            '.', '!', '?' -> capitalized
            else -> "$capitalized."
        }
    }
}
