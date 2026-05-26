package com.app.umma.data.repository.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState

/**
 * Correction 화면 / ViewModel / Fake repository 테스트가 공유하는 샘플 데이터 진입점이다.
 *
 * 이 파일은 fake/테스트 용도로만 사용한다.
 * 실제 AI 연동과 화면 검증이 production 경로로 자체 진행될 수 있게 되면
 * [com.app.umma.data.repository.fake.FakeCorrectionRepository] 와 함께 통째로 삭제할 수 있다.
 *
 * 모든 시드는 deterministic 하며, 같은 입력에 대해 항상 같은 카드/요청/결과를 만든다.
 * production invariants (예: candidate.lang == langState.lang, flashcards.isNotEmpty()) 를
 * 시드 단계에서 자동으로 만족시키도록 helper 만 노출한다.
 */
object CorrectionSuggestionFixtures {

    /**
     * PendingSync / Success 결과 시드의 기본 시각.
     * 실제 시각을 직접 비교하는 테스트가 흔들리지 않도록 결정적 값으로 고정한다.
     */
    const val DEFAULT_SAVED_AT: Long = 1_700_000_000_000L

    /**
     * Flashcard 저장 fixture 의 기본 사용자 식별자.
     * 실제 uid 가 필요한 테스트에서만 override 한다.
     */
    const val DEFAULT_UID: String = "uid-fixture"

    /**
     * 같은 언어 컨텍스트의 후보 한 건을 만든다.
     * suffix 로 id 만 분리해 여러 카드를 만들고 싶을 때 사용한다.
     */
    fun sampleCandidate(
        lang: LangCode = LangCode.EN,
        suffix: String = "1-def",
        sourceText: String = "this is test",
        sourceTurnIndex: Int = 1,
        assistantContext: String? = null
    ): CorrectionCandidate {
        return CorrectionCandidate(
            id = "${lang.name.lowercase()}-$suffix",
            lang = lang,
            sourceTurnIndex = sourceTurnIndex,
            sourceText = sourceText,
            assistantContext = assistantContext
        )
    }

    /**
     * Correction 결과 생성을 위한 입력 시드.
     *
     * candidate.lang 과 langState.lang 이 항상 같은 언어로 묶이도록 같은 helper 안에서 구성한다.
     * 이 invariant 가 깨지면 [CorrectionSuggestionFixtureBuilder] 가 require 로 막아 화면 검증이 불가능해진다.
     */
    fun sampleGenerateInput(
        lang: LangCode = LangCode.EN,
        candidates: List<CorrectionCandidate> = listOf(sampleCandidate(lang = lang))
    ): GenerateSuggestionsInput {
        return GenerateSuggestionsInput(
            candidates = candidates,
            langState = LangState.initial(lang)
        )
    }

    /**
     * Content 상태 시드.
     * real impl 과 같은 모양을 유지하기 위해 [CorrectionSuggestionFixtureBuilder] 에 위임한다.
     */
    fun contentSuggestions(lang: LangCode = LangCode.EN): List<CorrectionSuggestion> {
        return CorrectionSuggestionFixtureBuilder.buildSuggestions(sampleGenerateInput(lang))
    }

    /** Empty 상태 시드. */
    fun emptySuggestions(): List<CorrectionSuggestion> = emptyList()

    /**
     * 저장 요청 시드.
     * suggestions 가 주어지지 않으면 [contentSuggestions] 결과를 그대로 저장 항목으로 매핑한다.
     */
    fun sampleSaveRequest(
        uid: String = DEFAULT_UID,
        lang: LangCode = LangCode.EN,
        suggestions: List<CorrectionSuggestion> = contentSuggestions(lang),
        requestedAt: Long = DEFAULT_SAVED_AT
    ): CorrectionSaveRequest {
        return CorrectionSaveRequest(
            uid = uid,
            lang = lang,
            flashcards = suggestions.map { it.toSaveItem() },
            requestedAt = requestedAt
        )
    }

    /**
     * 저장 성공(모두 sync 완료) 결과 시드.
     * pendingSyncFlashcardIds 는 비어 있다.
     */
    fun successSaveResult(
        savedIds: List<String>,
        savedAt: Long = DEFAULT_SAVED_AT
    ): CorrectionSaveResult {
        return CorrectionSaveResult(
            localSavedFlashcardIds = savedIds,
            pendingSyncFlashcardIds = emptyList(),
            savedAt = savedAt
        )
    }

    /**
     * PendingSync 결과 시드.
     *
     * local-first 계약상 local 저장은 항상 성공이고 remote sync 만 pending 으로 남는다.
     * 따라서 localSavedFlashcardIds 와 pendingSyncFlashcardIds 가 동일한 ID 집합을 가지도록 만든다.
     */
    fun pendingSyncSaveResult(
        savedIds: List<String>,
        savedAt: Long = DEFAULT_SAVED_AT
    ): CorrectionSaveResult {
        return CorrectionSaveResult(
            localSavedFlashcardIds = savedIds,
            pendingSyncFlashcardIds = savedIds,
            savedAt = savedAt
        )
    }

    /** generate Error 상태 시드. */
    fun generateFailure(message: String = "generate failed"): Throwable = IllegalStateException(message)

    /** save Error 상태 시드. */
    fun saveFailure(message: String = "save failed"): Throwable = IllegalStateException(message)

    private fun CorrectionSuggestion.toSaveItem(): CorrectionFlashcardSaveItem {
        return CorrectionFlashcardSaveItem(
            suggestionId = id,
            frontText = nativeText,
            backText = afterText,
            explanation = explanation
        )
    }
}
