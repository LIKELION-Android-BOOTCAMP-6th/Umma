package com.example.umma.presentation.correction

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Correction 화면의 ViewModel.
 *
 * SSOT: COR-001_Initial_State.md
 *
 * 책임 (COR-001-A — 교정 가능 상태 로드):
 *  - 화면 진입 시 [GlobalLangState] 를 구독해 현재 선택 언어 기준의
 *    SessionSummary / LangState snapshot 을 추출한다.
 *  - SessionSummary.correctionAvailable == true 이고 위 세 가지가 모두 채워지면
 *    [CorrectionUiState.Phase.Ready] 로 전이한다. Ready 자체가 COR-002 진행
 *    트리거 hook 이며, 사용자 추가 입력 없이 다음 흐름으로 이어진다.
 *  - 결손 케이스는 모두 [CorrectionUiState.Phase.NotAvailable] 로 합친다 —
 *    어느 필드가 비어 있는지는 logcat 으로만 추적한다.
 *
 * RT-003 correction context 조회 준비 상태는 별도 호출 없이
 * SessionSummary.correctionAvailable 한 줄로 갈음한다. 실제 context 조회는
 * COR-002 (generateSuggestions 호출 시점) 에서 [com.example.umma.domain.usecase.realtime.GetCorrectionContextUseCase]
 * 를 통해 수행한다.
 *
 * 비범위:
 *  - generateSuggestions / saveFlashcards 등 [CorrectionRepository] 호출은 COR-002 에서.
 *  - sync / network 실패 분기는 이 백로그 AC 에 없어 추가하지 않는다.
 */
@HiltViewModel
class CorrectionViewModel @Inject constructor(
    private val preloadLearningState: PreloadLearningStateUseCase,
    private val observeLearningState: ObserveLearningStateUseCase,
) : ViewModel() {

    // UI state 의 단일 source of truth (쓰기 가능). Screen 은 read-only 노출만 본다.
    private val _uiState = MutableStateFlow(CorrectionUiState())

    // Screen 이 collectAsStateWithLifecycle 로 구독.
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    /**
     * observeLearningState() Flow 를 collect 하는 background job 핸들.
     *
     * Dashboard 와 동일하게 화면 재진입(LaunchedEffect 재실행, 회전 등) 마다
     * onEnter() 가 다시 호출돼도 isActive 가드로 collect 셋업은 1 회만 수행한다.
     * ViewModel onCleared() 시 viewModelScope 와 함께 자동 cancel.
     */
    private var enterJob: Job? = null

    /**
     * 화면 진입 시 호출. (COR-001-A)
     *
     * 1) Local Cache preload — 실패해도 observeLearningState() 가 initial() 을
     *    흘려보내 자연스럽게 NotAvailable 로 fallthrough.
     * 2) observeLearningState() collect — 첫 emit 부터 phase 를 결정한다.
     */
    fun onEnter() {
        ensureObservation()
    }

    private fun ensureObservation() {
        if (enterJob?.isActive == true) {
            Log.d(TAG, "ensureObservation() skipped — already collecting")
            return
        }
        enterJob = viewModelScope.launch {
            Log.d(TAG, "ensureObservation() — COR-001-A preload start")

            // Local Cache preload. preload 실패는 사용자 노출 없이 logcat 만 남기고,
            // 이어지는 collect 가 initial() 을 받아 NotAvailable 분기로 진행한다.
            preloadLearningState().exceptionOrNull()?.let { e ->
                Log.w(TAG, "preload failed — falling back to NotAvailable", e)
            }

            // 분기 로직은 [GlobalLangState.toCorrectionUiState] 순수 함수에 위임.
            // ViewModel 은 collect 와 logcat 추적만 책임지고, AC 매핑은 함수 본체에서 확인한다.
            observeLearningState().collect { global ->
                val next = global.toCorrectionUiState()
                val reason = global.notAvailableReason()
                if (reason != null) {
                    Log.d(TAG, "NotAvailable — $reason")
                } else {
                    Log.d(
                        TAG,
                        "Ready — lang=${next.selectedLearningLanguage}, " +
                                "recentTopic=${next.sessionSummary?.recentTopic}, " +
                                "langState.updatedAt=${next.langStateSnapshot?.updatedAt}"
                    )
                }
                _uiState.value = next
            }
        }
    }

    private companion object {
        const val TAG = "CorrectionViewModel"
    }
}
