package com.example.umma.domain.model.correction

import com.example.umma.domain.model.learningstate.LangStateUpdateInput

/**
 * Correction 완료 파이프라인 입력.
 *
 * 화면은 이 입력 하나만 넘기고, 저장/상태 갱신의 실제 순서는 UseCase가 책임진다.
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
 */
data class CompleteCorrectionResult(
    // local-first Flashcard 저장이 반영된 카드 ID.
    val savedFlashcardIds: List<String>,
    // 아직 remote sync 가 남아있는 카드 ID.
    val pendingSyncFlashcardIds: List<String>,
    // RT-003 Session Memory 후속 연결에서 참조할 수 있는 세션 키.
    val sessionMemoryKey: String,
    // 완료 시각.
    val completedAt: Long
)
