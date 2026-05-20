package com.example.umma.data.repository

import com.example.umma.data.repository.correction.CorrectionFlashcardStore
import com.example.umma.data.repository.correction.CorrectionSuggestionFixtureBuilder
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.repository.CorrectionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction 결과 생성과 저장 계약의 기본 구현체입니다.
 *
 * AI 응답 생성과 Flashcard local-first 저장은 모두 data 계층 세부 구현이다.
 * domain/usecase는 이 repository interface만 바라보고, raw AI JSON이나 Firestore 문서 구조를 알지 않는다.
 *
 * 현재 교정 결과 생성은 실제 AI 연결 전까지 deterministic fixture builder를 사용한다.
 * 반면 Flashcard 저장은 [CorrectionFlashcardStore]를 통해 local-first 저장 후 Firestore sync를 시도한다.
 */
@Singleton
open class CorrectionRepositoryImpl @Inject constructor(
    private val flashcardStore: CorrectionFlashcardStore
) : CorrectionRepository {

    override suspend fun generateSuggestions(
        input: GenerateSuggestionsInput
    ): Result<List<CorrectionSuggestion>> {
        return runCatching {
            // 실제 AI 연동 전까지는 fixture builder로 교정 결과의 계약 모양만 먼저 검증한다.
            CorrectionSuggestionFixtureBuilder.buildSuggestions(input)
        }
    }

    override suspend fun saveFlashcards(
        request: CorrectionSaveRequest
    ): Result<CorrectionSaveResult> {
        return runCatching {
            // 저장 요청이 비어 있으면 완료 파이프라인으로 넘길 수 없으므로 즉시 실패시킨다.
            require(request.flashcards.isNotEmpty()) {
                "flashcards must not be empty"
            }

            // 새 Flashcard 최초 저장은 Correction 책임이다.
            // store가 local save와 Firestore sync pending 분리를 담당한다.
            flashcardStore.save(request)
        }
    }

    override suspend fun rollbackFlashcards(
        request: CorrectionSaveRequest
    ): Result<Unit> {
        return runCatching {
            // 완료 파이프라인이 중간 실패하면 같은 저장 요청으로 만든 카드만 되돌린다.
            flashcardStore.rollback(request)
        }
    }
}
