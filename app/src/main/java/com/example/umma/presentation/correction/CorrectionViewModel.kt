package com.example.umma.presentation.correction

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.usecase.correction.ExtractSessionCandidatesUseCase
import com.example.umma.domain.usecase.correction.GenerateSuggestionsUseCase
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.example.umma.domain.usecase.realtime.GetCorrectionContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Correction 화면의 ViewModel.
 *
 * SSOT: COR-001_Initial_State.md / COR-002_Suggestion_Generation.md
 *
 * 책임:
 *  - (COR-001-A) [GlobalLangState] 구독 → Ready / NotAvailable 분기.
 *  - (COR-002-A) Ready 첫 emit 시 사용자 추가 입력 없이 generateSuggestions 를 1회 자동 트리거.
 *    RT-003 correction context → 후보 추출 → AI 호출 → CorrectionSuggestion 목록 → Content / Error.
 *
 * 비범위:
 *  - Empty / Retry / 선택 언어 변경 재트리거 → COR-002-B.
 *  - 결과 카드 본격 UI → COR-003-A.
 *  - saveFlashcards 등 후속 단계 → COR-004 이후.
 */
@HiltViewModel
class CorrectionViewModel @Inject constructor(
    private val preloadLearningState: PreloadLearningStateUseCase,
    private val observeLearningState: ObserveLearningStateUseCase,
    private val getCorrectionContext: GetCorrectionContextUseCase,
    private val extractSessionCandidates: ExtractSessionCandidatesUseCase,
    private val generateSuggestions: GenerateSuggestionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CorrectionUiState())
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    private var enterJob: Job? = null

    /**
     * Ready 진입 시 generateSuggestions 트리거를 한 번만 실행하기 위한 가드.
     *
     * -A 범위에서는 한 번 생성이 시작된 뒤 GlobalLangState 가 다시 Ready 를 흘려보내도 무시한다.
     * 선택 언어 변경 / Retry 정책은 -B 에서 이 가드를 푸는 방향으로 확장한다.
     */
    private var generationLaunched = false

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

            preloadLearningState().exceptionOrNull()?.let { e ->
                Log.w(TAG, "preload failed — falling back to NotAvailable", e)
            }

            observeLearningState().collect { global ->
                // -A 가드: generate 가 시작된 뒤에는 LangState refresh 로 Ready 가 다시 떨어져도
                // Generating/Content/Error 를 덮어쓰지 않는다. Retry 정책은 -B 에서 다룬다.
                if (generationLaunched) {
                    return@collect
                }

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

                if (next.phase == CorrectionUiState.Phase.Ready) {
                    generationLaunched = true
                    triggerGeneration(next)
                }
            }
        }
    }

    /**
     * Ready 게이트 통과 직후의 자동 교정 흐름.
     *
     * 1) RT-003 correction context 조회 (Flow 첫 emit).
     * 2) ExtractSessionCandidatesUseCase 로 후보 추출.
     * 3) GenerateSuggestionsInput 구성 → GenerateSuggestionsUseCase.
     * 4) success → Content, failure → Error.
     */
    private fun triggerGeneration(ready: CorrectionUiState) {
        val lang = ready.selectedLearningLanguage ?: return
        val langState = ready.langStateSnapshot ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = CorrectionUiState.Phase.Generating,
                errorReason = null,
            )

            val result = runCatching {
                // RT-003 read model 은 Flow 라 추가 emit 이 흘러도 -A 범위에서는 첫 snapshot 만 본다.
                val sessionTurns = getCorrectionContext(lang).first()
                val candidates = extractSessionCandidates(
                    selectedLang = lang,
                    sessionLang = lang,
                    sessionTurns = sessionTurns,
                )
                Log.d(TAG, "Generating — candidates=${candidates.size}")

                val input = GenerateSuggestionsInput(
                    candidates = candidates,
                    langState = langState,
                )
                generateSuggestions(input).getOrThrow()
            }

            result.fold(
                onSuccess = { suggestions ->
                    Log.d(TAG, "Content — suggestions=${suggestions.size}")
                    _uiState.value = _uiState.value.copy(
                        phase = CorrectionUiState.Phase.Content,
                        suggestions = suggestions,
                        errorReason = null,
                    )
                },
                onFailure = { e ->
                    val reason = e.message ?: e.javaClass.simpleName
                    Log.w(TAG, "Error — reason=$reason", e)
                    _uiState.value = _uiState.value.copy(
                        phase = CorrectionUiState.Phase.Error,
                        suggestions = emptyList(),
                        errorReason = reason,
                    )
                }
            )
        }
    }

    private companion object {
        const val TAG = "CorrectionViewModel"
    }
}
