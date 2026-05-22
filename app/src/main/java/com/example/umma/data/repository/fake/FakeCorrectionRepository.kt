package com.example.umma.data.repository.fake

import com.example.umma.data.repository.CorrectionRepositoryImpl
import com.example.umma.data.repository.correction.CorrectionFlashcardStore
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction 화면 / ViewModel 검증용 fake repository.
 *
 * 토글 필드 한 줄로 generateSuggestions 의 Content / Empty / Error,
 * saveFlashcards 의 Success / SaveFail / PendingSync, rollback 실패를 빠르게 재현한다.
 *
 * [CorrectionRepositoryImpl] 을 상속해 토글이 비어 있을 때는 production 저장 경로
 * ([CorrectionFlashcardStore]) 로 그대로 위임한다.
 * 그래서 fake 라도 real save 로직과 같은 결과 모양을 곧장 비교할 수 있다.
 *
 * 이 클래스는 fake/테스트 용도다.
 * 실제 AI 연동 후 화면이 production 데이터만으로 검증 가능해지면
 * [com.example.umma.data.repository.correction.CorrectionSuggestionFixtures] 와 함께 통째로 삭제할 수 있다.
 */
@Singleton
class FakeCorrectionRepository @Inject constructor(
    flashcardStore: CorrectionFlashcardStore
) : CorrectionRepositoryImpl(flashcardStore) {

    /**
     * null 이면 super 위임으로 fixture builder 결과를 그대로 사용한다.
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
        // failure → override → super 순서. real 경로와 비교 가능성을 유지하기 위해 super 위임을 default 로 둔다.
        generateFailure?.let { return Result.failure(it) }
        suggestionsOverride?.let { return Result.success(it) }
        return super.generateSuggestions(input)
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
