package com.app.umma.presentation.correction

/**
 * Correction 화면이 ViewModel → UI 방향으로 1회성 effect 를 전달할 때 사용하는 봉인 계약.
 *
 * SSOT: COR-007_Return_and_Sync.md (AC "완료 성공 이벤트는 한 번만 소비된다").
 *
 * 왜 별도 sealed type 인가:
 *  - `CorrectionUiState` 는 화면이 매 recomposition 마다 반복 소비하는 "상태" 의 SSOT 라
 *    "정확히 한 번만 일어나야 하는 navigation/snackbar/toast" 같은 effect 를 담기에는 부적합하다.
 *  - 본 sealed 는 [CorrectionViewModel] 이 [kotlinx.coroutines.channels.Channel] 로 emit 하고
 *    화면이 `receiveAsFlow().collect` 로 소비하는 단방향 effect 전용 계약이다.
 *  - Channel 기반이라 회전/recomposition/재진입으로 동일 이벤트가 재발화되지 않는다
 *    (각 element 는 단일 collector 에 정확히 한 번 전달된다).
 *
 * 확장 방향:
 *  - 후속 백로그(COR-006-B Retry 안내 토스트, COR-007-B sync pending 안내 등)에서 이벤트 종류가
 *    늘어나면 본 sealed 에 분기만 추가하고 화면 collect 블록 when 에 분기를 한 줄 추가하면 된다.
 */
sealed interface CorrectionEvent {

    /**
     * 완료 파이프라인 성공 직후 Dashboard 로 복귀해야 한다는 1회성 신호.
     *
     * 발화 위치: [CorrectionViewModel.launchCompletion] 의 5단계 마지막 — 완료 호출이 성공해
     * [CorrectionUiState.Phase.Done] 으로 전환된 직후 지연 없이 발화된다. 1~4단계 실패 분기(uid 미확보 / RT-003
     * context 조회 실패 / LangStateUpdateInput 조립 실패) 와 5단계 호출 실패 분기에서는 발화하지 않는다.
     *
     * 소비 위치: [CorrectionScreen] 의 LaunchedEffect collect 블록 → 상위 NavHost 가 정의한
     * Dashboard 복귀 콜백(CorrectionGraph 통째로 pop + launchSingleTop) 호출.
     */
    data object NavigateToDashboard : CorrectionEvent
}
