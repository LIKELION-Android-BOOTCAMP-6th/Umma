package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import javax.inject.Inject

/**
 * 선택된 교정 결과를 Flashcard 저장 요청으로 정리하는 UseCase 입니다.
 */
class PrepareSaveRequestUseCase @Inject constructor() {

    operator fun invoke(
        uid: String,
        selectedSuggestions: List<CorrectionSuggestion>,
        requestedAt: Long = System.currentTimeMillis()
    ): Result<CorrectionSaveRequest> {
        return runCatching {
            // Flashcard Room 엔터티는 userId + cardId 복합 키로 중복을 막는다.
            // 따라서 화면에서 uid를 빠뜨리면 계정별 저장 경계를 만들 수 없어 여기서 먼저 차단한다.
            require(uid.isNotBlank()) {
                "uid must not be blank"
            }

            // 선택 상태에서 중복 클릭/중복 선택이 들어와도 저장 요청은 카드당 하나만 만든다.
            val normalizedSuggestions = selectedSuggestions.distinctBy { it.id }
            require(normalizedSuggestions.isNotEmpty()) {
                "selectedSuggestions must not be empty"
            }

            // 한 저장 요청 안에서는 현재 선택 언어 하나만 다뤄야 완료 파이프라인이 흔들리지 않는다.
            val lang = normalizedSuggestions.first().lang
            require(normalizedSuggestions.all { it.lang == lang }) {
                "selectedSuggestions must use the same language"
            }

            val flashcards = normalizedSuggestions.map { suggestion ->
                require(suggestion.nativeText.isNotBlank()) {
                    "nativeText must not be blank"
                }
                require(suggestion.afterText.isNotBlank()) {
                    "afterText must not be blank"
                }

                // SYS-CORRECTION-INFRA 저장 계약: 앞면은 모국어, 뒷면은 교정 문장과 설명이다.
                CorrectionFlashcardSaveItem(
                    suggestionId = suggestion.id,
                    frontText = suggestion.nativeText.trim(),
                    backText = suggestion.afterText.trim(),
                    explanation = suggestion.explanation.trim()
                )
            }

            CorrectionSaveRequest(
                uid = uid,
                lang = lang,
                flashcards = flashcards,
                requestedAt = requestedAt
            )
        }
    }
}
