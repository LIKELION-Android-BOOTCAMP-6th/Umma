package com.app.umma.domain.model.correction

import com.app.umma.domain.model.learningstate.LangStateUpdateInput

/**
 * Correction 완료 파이프라인 입력.
 *
 * 화면은 이 입력 하나만 넘기고, 저장/상태 갱신의 실제 순서는 UseCase가 책임진다.
 * ViewModel 이 Flashcard 저장, LangState 갱신, Session Memory compression 순서를 알게 되면
 * 화면 계층이 완료 정책을 소유하게 되므로 이 모델로 domain 경계에 위임한다.
 */
data class CompleteCorrectionInput(
    // 사용자가 최종 선택한 교정 결과.
    val selectedSuggestions: List<CorrectionSuggestion>,
    // LS-006 저장 정책으로 넘길 상태 업데이트 입력.
    val langStateUpdateInput: LangStateUpdateInput,
    // 완료 시점. 저장과 상태 갱신이 같은 기준 시각을 보게 한다.
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * Correction 완료 결과.
 *
 * Firestore sync 와 Session Memory compression 은 사용자 저장 완료를 막지 않는 후속 상태다.
 * 그래서 완료 결과는 성공/실패 하나로 뭉개지 않고, 저장된 카드와 pending 상태를 분리해 돌려준다.
 */
data class CompleteCorrectionResult(
    // local-first Flashcard 저장이 반영된 카드 ID.
    val savedFlashcardIds: List<String>,
    // 아직 remote sync 가 남아있는 카드 ID.
    val pendingSyncFlashcardIds: List<String>,
    // RT-003 Session Memory 후속 연결에서 참조할 수 있는 세션 키.
    val sessionMemoryKey: String,
    // RT-003 compression 이 이번 완료 흐름에서 적용되었는지 여부.
    val sessionCompressionApplied: Boolean = false,
    // compression 실패로 후속 재시도 대상이 남았는지 여부.
    val sessionCompressionPending: Boolean = false,
    // compression 실패를 사용자 흐름의 fatal error 로 키우지 않기 위한 진단 메시지.
    val sessionCompressionErrorMessage: String? = null,
    // StatisticsHistory local-first 기록이 이번 완료 흐름에서 반영되었는지 여부.
    val statisticsHistoryApplied: Boolean = false,
    // history 기록이 remote sync pending 으로 남아있는지 여부.
    val statisticsHistoryPending: Boolean = false,
    // history 기록 실패 또는 pending 사유를 사용자 흐름에서 확인할 수 있게 남기는 메시지.
    val statisticsHistoryErrorMessage: String? = null,
    // Flashcard 저장 직후 dueFlashcards / savedFlashcards 갱신이 이번 완료 흐름에서 반영되었는지 여부.
    // DashSummary 와 FlashcardSummary 를 함께 업데이트하므로 Dashboard 가 즉시 최신 카드 수를 표시한다. (#162-D)
    val flashcardSummaryApplied: Boolean = false,
    // getReviewSummary 또는 applyFlashcardSummaryUpdateUseCase 실패 시 pending 으로만 남기고 Done 진행.
    // Flashcard 저장과 LangState 는 이미 commit 상태이므로 보상 없이 후속 재시도 대상으로 처리한다.
    val flashcardSummaryPending: Boolean = false,
    // 최근 5개 세션 주제 AI 요약이 이번 완료 흐름에서 Session Memory 에 저장되었는지 여부. (#162-C)
    val topicSummariesApplied: Boolean = false,
    // AI 요약 실패 시 pending 으로만 남기고 Done 진행. 기존 topicSummaries 는 변경하지 않는다.
    val topicSummariesPending: Boolean = false,
    // 완료 시각.
    val completedAt: Long
)
