package com.example.umma.data.repository

import com.example.umma.data.repository.correction.CorrectionRepositorySupport
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.repository.CorrectionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction 결과 생성과 저장 계약의 기본 구현체입니다.
 *
 * 현재 단계에서는 AI 응답과 local-first 저장 계약을 분리해 둘 수 있도록
 * deterministic 한 변환 규칙과 in-memory 저장 스코프를 제공한다.
 *
 * 여기서의 역할은 "실제 저장소"를 완성하는 것이 아니라,
 * domain 계약이 data 계층에서 어떻게 구체화되는지 먼저 고정하는 것이다.
 * 그래서 화면은 이 구현체를 통해 결과를 검증하고,
 * 이후 Room/Firestore가 붙더라도 같은 인터페이스를 유지할 수 있다.
 */
@Singleton
open class CorrectionRepositoryImpl @Inject constructor() : CorrectionRepository {

    private val savedFlashcardsByLang =
        mutableMapOf<LangCode, MutableMap<String, CorrectionFlashcardSaveItem>>()

    override suspend fun generateSuggestions(
        input: GenerateSuggestionsInput
    ): Result<List<CorrectionSuggestion>> {
        return runCatching {
            // 실제 AI 연동 전까지는 deterministic adapter로 교정 결과의 계약 모양만 먼저 검증한다.
            CorrectionRepositorySupport.buildSuggestions(input)
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

            // Room Entity가 아직 붙지 않았더라도, 언어별로 저장 영역을 나눠 local-first 경계를 유지한다.
            // 이 in-memory 맵은 실제 저장소를 대체하기보다 현재 설계가 중복 저장 없이 동작하는지 확인하는 역할이다.
            val langStore = savedFlashcardsByLang.getOrPut(request.lang) { linkedMapOf() }
            val savedIds = mutableListOf<String>()

            request.flashcards.forEach { flashcard ->
                // 같은 suggestionId는 동일 카드로 간주하고 중복 저장을 막는다.
                if (!langStore.containsKey(flashcard.suggestionId)) {
                    langStore[flashcard.suggestionId] = flashcard
                    savedIds += flashcard.suggestionId
                }
            }

            // local 저장에 성공한 항목과, 아직 원격 sync 가 남은 항목을 같은 결과 모델로 돌려준다.
            // 지금 단계에서는 둘의 ID 집합이 같아도, 이후 Firestore pending 상태를 표현할 수 있도록 분리해 둔다.
            CorrectionSaveResult(
                localSavedSuggestionIds = savedIds,
                pendingSyncSuggestionIds = savedIds,
                savedAt = request.requestedAt
            )
        }
    }

    override suspend fun rollbackFlashcards(
        request: CorrectionSaveRequest
    ): Result<Unit> {
        return runCatching {
            // 완료 파이프라인이 중간 실패하면 같은 저장 요청으로 만든 카드만 되돌린다.
            val langStore = savedFlashcardsByLang[request.lang] ?: return@runCatching

            request.flashcards.forEach { flashcard ->
                langStore.remove(flashcard.suggestionId)
            }

            // 언어별 저장소가 비면 map에서도 제거해 다음 local 상태가 깔끔하게 시작되도록 한다.
            if (langStore.isEmpty()) {
                savedFlashcardsByLang.remove(request.lang)
            }
        }
    }
}
