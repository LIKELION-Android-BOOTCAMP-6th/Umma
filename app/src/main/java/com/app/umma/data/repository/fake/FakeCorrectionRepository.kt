package com.app.umma.data.repository.fake

import com.app.umma.data.repository.CorrectionRepositoryImpl
import com.app.umma.data.repository.correction.CorrectionAiClient
import com.app.umma.data.repository.correction.CorrectionAiResponseMapper
import com.app.umma.data.repository.correction.CorrectionFlashcardStore
import com.app.umma.data.repository.correction.CorrectionPromptBuilder
import com.app.umma.data.repository.correction.CorrectionSuggestionFixtureBuilder
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction 화면 / ViewModel 검증용 fake repository.
 *
 * 토글 필드 한 줄로 generateSuggestions 의 Content / Empty / Error,
 * saveFlashcards 의 Success / SaveFail / PendingSync, rollback 실패를 빠르게 재현한다.
 *
 * [CorrectionRepositoryImpl] 을 상속해 토글이 비어 있을 때:
 *  - generateSuggestions 는 [CorrectionSuggestionFixtureBuilder] 로 fallback. (super 가 실제 Gemini 호출이 되어 화면 검증 의도가 깨지지 않게.)
 *  - saveFlashcards / rollbackFlashcards 는 super 로 위임해 real save 경로와 같은 결과 모양을 비교한다.
 *
 * 이 클래스는 fake/테스트 용도다.
 * 실제 AI 연동 후 화면이 production 데이터만으로 검증 가능해지면
 * [com.app.umma.data.repository.correction.CorrectionSuggestionFixtures] 와 함께 통째로 삭제할 수 있다.
 */
@Singleton
class FakeCorrectionRepository @Inject constructor(
    // 아래 4개는 모두 super(CorrectionRepositoryImpl) 로 그대로 위임되는 passthrough.
    // saveFlashcards / rollbackFlashcards 처럼 fake 가 override 하지 않은 경로에서는 real 저장 흐름이 그대로 돈다.
    // Flashcard local-first 저장 위임처 (super 가 사용).
    flashcardStore: CorrectionFlashcardStore,
    // 프롬프트 빌더 (super 가 사용 — fake 의 generateSuggestions 가 override 되어 있어 실제 호출은 보통 일어나지 않음).
    promptBuilder: CorrectionPromptBuilder,
    // AI 호출 어댑터 (super 가 사용 — 위와 동일하게 fake override 가 가로채는 게 정상 동작).
    aiClient: CorrectionAiClient,
    // AI 응답 매퍼 (super 가 사용 — 위와 동일).
    responseMapper: CorrectionAiResponseMapper
) : CorrectionRepositoryImpl(flashcardStore, promptBuilder, aiClient, responseMapper) {

    /**
     * null 이면 fixture builder 결과로 fallback 한다 (실제 Gemini 호출은 fake 모드에서 의도와 어긋난다).
     * empty list 면 Empty 상태, 비어 있지 않으면 Content 상태를 재현한다.
     */
    var suggestionsOverride: List<CorrectionSuggestion>? = null

    /** 설정되면 generateSuggestions 가 즉시 Result.failure 로 종료된다 (Error 상태). */
    var generateFailure: Throwable? = null

    /** 설정되면 saveFlashcards 가 즉시 Result.failure 로 종료된다 (SaveFail 상태). */
    var saveFailure: Throwable? = null

    /**
     * 설정되면 saveFlashcards 가 real 저장을 건너뛰고 이 결과를 그대로 돌려준다.
     * pendingSyncFlashcardIds 를 채운 결과를 넣어 PendingSync 상태를 재현한다.
     */
    var saveResultOverride: CorrectionSaveResult? = null

    /** 설정되면 rollbackFlashcards 가 즉시 Result.failure 로 종료된다. */
    var rollbackFailure: Throwable? = null

    override suspend fun generateSuggestions(
        input: GenerateSuggestionsInput
    ): Result<List<CorrectionSuggestion>> {
        // failure → override → fixture fallback 순서.
        // fixture fallback 은 super(=실제 Gemini) 호출 대신 deterministic fixture builder 를 직접 부른다.
        generateFailure?.let { return Result.failure(it) }
        suggestionsOverride?.let { return Result.success(it) }
        return runCatching {
            CorrectionSuggestionFixtureBuilder.buildSuggestions(input)
        }
    }

    override suspend fun saveFlashcards(
        request: CorrectionSaveRequest
    ): Result<CorrectionSaveResult> {
        saveFailure?.let { return Result.failure(it) }
        saveResultOverride?.let { return Result.success(it) }
        return super.saveFlashcards(request)
    }

    override suspend fun rollbackFlashcards(
        request: CorrectionSaveRequest
    ): Result<Unit> {
        rollbackFailure?.let { return Result.failure(it) }
        return super.rollbackFlashcards(request)
    }
}
