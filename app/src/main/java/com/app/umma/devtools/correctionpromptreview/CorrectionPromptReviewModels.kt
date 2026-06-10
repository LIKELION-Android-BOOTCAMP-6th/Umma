package com.app.umma.devtools.correctionpromptreview

import com.app.umma.domain.model.learningstate.LangCode

/**
 * Development-only snapshot for reviewing one Correction generation result.
 *
 * This model intentionally stays outside production Correction contracts because review reports
 * are diagnostic artifacts, not learning-state or flashcard source-of-truth data.
 */
data class CorrectionPromptReviewSnapshot(
    val uid: String,
    val language: LangCode,
    val primaryLanguage: LangCode?,
    val phase: String,
    val reportNote: String?,
    val sourceKey: String,
    val suggestions: List<CorrectionPromptReviewSuggestion>,
    val selectedSuggestionIds: Set<String>,
    val contextTurns: List<CorrectionPromptReviewContextTurn>,
    val errorReason: String?,
    val saveErrorReason: String?,
    val completionErrorReason: String?,
    // COR-FIX-013: 신고 시점에 적용된 교정 성장 band. 콘솔에서 프롬프트 튜닝 효과를 band별로 추적한다.
    // band 전달 경로가 연결되지 않은 경우(캐시 복원 등)에는 null이 기록된다.
    val promptBand: String? = null,
    val reportedAt: Long = System.currentTimeMillis()
)

data class CorrectionPromptReviewSuggestion(
    val id: String,
    val sourceCandidateIds: List<String>,
    val sourceTurnIndex: Int,
    val beforeText: String,
    val nativeText: String,
    val afterText: String,
    val explanation: String,
    val sourceLang: LangCode?
)

data class CorrectionPromptReviewContextTurn(
    val speaker: String,
    val text: String
)
