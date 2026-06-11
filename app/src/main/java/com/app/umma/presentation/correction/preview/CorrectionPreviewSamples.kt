package com.app.umma.presentation.correction.preview

import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.LangCode

/**
 * Correction 결과 카드 @Preview 전용 샘플 데이터.
 *
 * presentation 계층이 data 레이어의 fixture 에 의존하지 않도록 도메인 모델만 사용해
 * 동일한 렌더 결과를 이 경계 안에서 재현한다.
 */
internal object CorrectionPreviewSamples {

    fun sampleSuggestion(): CorrectionSuggestion = CorrectionSuggestion(
        id = "corr-en-1-def",
        lang = LangCode.EN,
        sourceCandidateIds = listOf("en-1-def"),
        sourceTurnIndex = 1,
        beforeText = "this is test",
        nativeText = "this is test",
        afterText = "This is test.",
        explanation = "LangState A1 snapshot 기준 교정 제안",
    )

    fun sampleSuggestions(): List<CorrectionSuggestion> = listOf(sampleSuggestion())
}
