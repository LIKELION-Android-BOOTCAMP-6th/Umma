package com.example.umma.data.source.local

import com.example.umma.data.model.correction.CorrectionFlashcardDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction에서 생성한 Flashcard의 local-first 저장 경계입니다.
 *
 * 현재 프로젝트에는 Flashcard Room DAO가 아직 없으므로 in-memory 구현으로 계약을 먼저 고정한다.
 * SRS 인프라에서 Room Entity/DAO가 준비되면 이 인터페이스 뒤의 구현만 교체하면 된다.
 */
interface CorrectionFlashcardLocalDataSource {

    suspend fun saveFlashcards(flashcards: List<CorrectionFlashcardDto>): List<String>

    suspend fun rollbackFlashcards(flashcardIds: List<String>)
}

@Singleton
class InMemoryCorrectionFlashcardLocalDataSource @Inject constructor() :
    CorrectionFlashcardLocalDataSource {

    private val flashcardsById = linkedMapOf<String, CorrectionFlashcardDto>()

    override suspend fun saveFlashcards(flashcards: List<CorrectionFlashcardDto>): List<String> {
        val savedIds = mutableListOf<String>()

        flashcards.forEach { flashcard ->
            // 같은 suggestion에서 파생된 카드는 같은 Flashcard로 취급해 중복 생성을 막는다.
            if (!flashcardsById.containsKey(flashcard.id)) {
                flashcardsById[flashcard.id] = flashcard
                savedIds += flashcard.id
            }
        }

        return savedIds
    }

    override suspend fun rollbackFlashcards(flashcardIds: List<String>) {
        // CompleteCorrectionUseCase가 실패하면 이번 요청으로 저장한 카드만 되돌린다.
        flashcardIds.forEach { flashcardId ->
            flashcardsById.remove(flashcardId)
        }
    }
}
