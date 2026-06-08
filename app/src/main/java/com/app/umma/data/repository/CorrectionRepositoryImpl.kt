package com.app.umma.data.repository

import android.util.Log
import com.app.umma.data.repository.correction.BlankExplanationException
import com.app.umma.data.repository.correction.CorrectionAiClient
import com.app.umma.data.repository.correction.CorrectionAiResponseMapper
import com.app.umma.data.repository.correction.CorrectionFlashcardStore
import com.app.umma.data.repository.correction.CorrectionOverexpansionGuard
import com.app.umma.data.repository.correction.CorrectionPromptBuilder
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.repository.CorrectionRepository
import kotlinx.serialization.SerializationException
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
    private val responseMapper: CorrectionAiResponseMapper,
    // COR-TUNE-006: 매핑 후 과확장·의미 위반 suggestion을 drop하는 런타임 가드.
    private val overexpansionGuard: CorrectionOverexpansionGuard
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
            // COR-FIX-009: AI 호출 직전 후보 목록/선택 언어/turn 정보를 남긴다.
            // unknown candidateId 가 발생했을 때 "AI 가 hallucinate 했는지" vs "이 시점의 후보 목록 자체가
            // prompt 에 박힌 ID와 달랐는지"를 mapper 진단 로그와 교차 확인할 수 있게 하는 조사용 로그다.
            logGenerationRequest(input)
            // mapper 가 candidateId 매칭과 필수 필드 검증을 require 로 막아 둔다.
            // 매칭 실패 / 누락 → IllegalArgumentException → 여기 runCatching 으로 Result.failure 변환 → 화면 Error.
            val mapped = generateAndMapWithRetry(prompt, input)
            // COR-TUNE-006: 과확장 런타임 가드. 가드 실패 시 원본 결과로 폴백해 핵심 4필드 흐름을 무손상으로 유지한다.
            runCatching { overexpansionGuard.filter(mapped, input) }.getOrDefault(mapped)
        }
    }

    /**
     * COR-FIX-008-C/D: malformed JSON과 explanation 누락을 같은 1회 재시도 예산으로 묶어 처리하는
     * repository 재시도 경계입니다.
     *
     * - [SerializationException](JSON 문법/타입 디코딩 실패)과 [BlankExplanationException]
     *   (explanation 누락/공백)은 모델의 "형식 출력 실패"에 가까워 1회 재호출할 가치가 있다.
     * - [IllegalArgumentException](unknown candidateId·공백 nativeText/afterText·언어 mismatch)은
     *   계약 위반이라 재호출해도 같은 위반일 확률이 높아 비용만 늘므로 catch하지 않는다(즉시 실패).
     *   ([SerializationException]은 [IllegalArgumentException]의 하위 타입이지만, `is` 검사 순서상
     *   먼저 매칭되므로 재시도 대상에서 빠지지 않는다)
     * - 재시도는 1회로 제한하고, 재시도 prompt에는 실패 유형에 맞는 보정 지시를 덧붙인다.
     * - 재시도 pass는 `dropBlankExplanation = true`로 호출한다 — 그래도 explanation이 비어 있으면
     *   해당 suggestion만 drop해(BlankExplanationException 재던짐 없음) 핵심 4필드 흐름을 지킨다.
     */
    private suspend fun generateAndMapWithRetry(
        prompt: String,
        input: GenerateSuggestionsInput
    ): List<CorrectionSuggestion> {
        val rawJson = aiClient.generateJson(prompt)
        return try {
            responseMapper.map(rawJson, input, dropBlankExplanation = false)
        } catch (e: Exception) {
            if (e !is SerializationException && e !is BlankExplanationException) {
                throw e
            }
            val retryRawJson = aiClient.generateJson(prompt + retryInstruction(e))
            try {
                responseMapper.map(retryRawJson, input, dropBlankExplanation = true)
            } catch (retryFailure: SerializationException) {
                retryFailure.addSuppressed(e)
                throw retryFailure
            }
        }
    }

    /**
     * COR-FIX-009: AI 호출 직전 조사용 진단 로그.
     *
     * unknown candidateId 실패 시 mapper 가 남기는 진단(받은 id + 그 시점에 알던 id 목록)과
     * 이 로그(prompt 에 실제로 실린 후보 id/turn 정보)를 나란히 보면, "prompt 에 ID 가 있었는데
     * mapper 시점엔 없었다"(상태 재사용 불일치) 와 "애초에 prompt 에 없었다"(AI hallucination)를
     * Logcat 만으로 구분할 수 있다. android.util.Log 는 JVM 단위 테스트에서 throw 하므로
     * mapper 의 logWarn 패턴과 동일하게 runCatching 으로 감싼다.
     */
    private fun logGenerationRequest(input: GenerateSuggestionsInput) {
        runCatching {
            val ids = input.candidates.map { "${it.id}(turn=${it.sourceTurnIndex})" }
            Log.d(
                TAG,
                "generateSuggestions request — lang=${input.langState.lang.code}, " +
                    "candidateCount=${input.candidates.size}, candidates=$ids"
            )
        }
    }

    /** 1차 실패 유형에 맞춰 재시도 prompt에 덧붙일 보정 지시를 고른다. */
    private fun retryInstruction(failure: Exception): String = when (failure) {
        is BlankExplanationException ->
            "\n\nYour previous response omitted the 'explanation' field for at least one suggestion. " +
                "Retry once and make sure every suggestion includes a non-empty, short explanation."
        else ->
            "\n\nYour previous response was not valid JSON. Retry once and return ONLY a syntactically valid JSON object. " +
                "Do not put any characters outside quoted JSON string values."
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

    private companion object {
        // logcat 필터 식별자. COR-FIX-009 조사용 로그가 이 태그로 모인다.
        private const val TAG = "CorrectionRepositoryImpl"
    }
}
