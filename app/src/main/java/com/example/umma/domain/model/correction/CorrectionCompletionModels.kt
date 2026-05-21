package com.example.umma.domain.model.correction

import com.example.umma.domain.model.learningstate.LangStateUpdateInput

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
    // 완료 시각.
    val completedAt: Long
)
