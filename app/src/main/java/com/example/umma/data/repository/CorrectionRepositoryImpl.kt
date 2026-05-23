package com.example.umma.data.repository

import com.example.umma.data.repository.correction.CorrectionAiClient
import com.example.umma.data.repository.correction.CorrectionAiResponseMapper
import com.example.umma.data.repository.correction.CorrectionFlashcardStore
import com.example.umma.data.repository.correction.CorrectionPromptBuilder
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
 * 교정 결과 생성은 prompt builder → AI client → response mapper 의 3 단 파이프라인을 거친다.
 * 각 단계가 자기 책임만 알고 다음 단계의 표현을 모르므로, AI 모델 교체나 schema 조정도 한 파일만 손대면 끝난다.
 *
 * Flashcard 저장은 [CorrectionFlashcardStore]를 통해 local-first 저장 후 Firestore sync를 시도한다.
 */
@Singleton
open class CorrectionRepositoryImpl @Inject constructor(
    // Flashcard local-first 저장 위임처. local Room 저장 + Firestore sync pending 분리를 담당한다.
    private val flashcardStore: CorrectionFlashcardStore,
    // AI 파이프라인 1단: LangState + 후보 목록을 Gemini 호출용 프롬프트 문자열로 조립.
    private val promptBuilder: CorrectionPromptBuilder,
    // AI 파이프라인 2단: Gemini 2.5-flash 단발 JSON 호출 어댑터. 테스트에서는 fake 로 교체된다.
    private val aiClient: CorrectionAiClient,
    // AI 파이프라인 3단: raw JSON → CorrectionSuggestion 변환 + candidateId 매칭/필수 필드 검증.
    private val responseMapper: CorrectionAiResponseMapper
) : CorrectionRepository {

    override suspend fun generateSuggestions(
        input: GenerateSuggestionsInput
    ): Result<List<CorrectionSuggestion>> {
        // 후보가 없으면 AI 호출 자체가 의미가 없다. 네트워크/비용 낭비를 막고 빈 결과를 그대로 돌려준다.
        // (Empty 상태의 본격 UX 처리는 COR-002-B 범위이므로 여기서는 success(emptyList) 만 한다.)
        if (input.candidates.isEmpty()) {
            return Result.success(emptyList())
        }

        return runCatching {
            val prompt = promptBuilder.build(input)
            val rawJson = aiClient.generateJson(prompt)
            // mapper 가 candidateId 매칭과 필수 필드 검증을 require 로 막아 둔다.
            // 매칭 실패 / 누락 → IllegalArgumentException → 여기 runCatching 으로 Result.failure 변환 → 화면 Error.
            responseMapper.map(rawJson, input)
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
