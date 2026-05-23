package com.example.umma.presentation.statistics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.learningstate.currentLangState
import com.example.umma.domain.model.learningstate.selectedLang
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.domain.usecase.statistics.GetStatisticsOverviewUseCase
import com.example.umma.domain.usecase.statistics.GetMetricHistoryPointsUseCase
import com.example.umma.presentation.statistics.model.StatisticsMetricChartState
import com.example.umma.presentation.statistics.model.toStatisticsMetricChartState
import com.example.umma.presentation.statistics.model.toMetricSummaryItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Statistics 화면의 초기 컨텍스트를 준비하는 ViewModel이다.
 *
 * 1) local cache를 먼저 preload 한다.
 * 2) 현재 선택 언어와 LangState.external 을 조립한다.
 * 3) 재진입 중 중복 초기화가 일어나지 않도록 load job 을 1개만 유지한다.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val observeLearningStateUseCase: ObserveLearningStateUseCase,
    private val preloadLearningStateUseCase: PreloadLearningStateUseCase,
    private val getStatisticsOverviewUseCase: GetStatisticsOverviewUseCase,
    private val getMetricHistoryPointsUseCase: GetMetricHistoryPointsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    // 화면 재진입/재구성 중 중복 초기화가 겹치지 않도록 load job 을 하나만 유지한다.
    private var loadJob: Job? = null
    // chart 요청은 카드 클릭마다 새로 발생하므로 overview 로딩과 별도 job 으로 관리한다.
    private var chartJob: Job? = null
    // 현재 선택 언어/현재 언어의 updatedAt 이 바뀌면 다시 준비해야 하는지 추적한다.
    private var pendingReload: Boolean = false
    private var observeJob: Job? = null
    private var lastObservedSignature: StatisticsContextSignature? = null
    // 빠르게 여러 카드를 누르거나 dialog를 닫을 때, 오래된 응답이 최신 상태를 덮지 못하게 막는다.
    private var chartRequestVersion: Long = 0L

    init {
        // Statistics 화면은 단발성 초기화보다 "현재 선택 언어가 바뀌는 흐름"을 따라가야 하므로
        // 단순 load 1회 호출 대신 observeContext() 를 먼저 세운다.
        observeContext()
    }

    fun retry() {
        // 사용자가 다시 시도 버튼을 누르면, 현재 컨텍스트를 새로 조립한다.
        // 화면 재진입과 동일한 경로를 타게 해서 분기 수를 줄인다.
        loadOverview()
    }

    fun onMetricClick(metricType: StatisticsMetricType) {
        // STAT-003에서는 카드 클릭이 곧 chart dialog 오픈 트리거가 된다.
        // 선택 카드 하이라이트는 유지하고, chart 데이터는 별도 use case로 받아온다.
        _uiState.update {
            it.copy(
                selectedMetricType = metricType,
                metricChartState = StatisticsMetricChartState.Loading(metricType)
            )
        }
        loadMetricChart(metricType)
    }

    fun dismissMetricChart() {
        // 닫기 이후 도착하는 chart 결과는 무시해야 하므로 version을 먼저 올린다.
        chartRequestVersion += 1L
        chartJob?.cancel()
        chartJob = null
        _uiState.update {
            it.copy(metricChartState = StatisticsMetricChartState.Hidden)
        }
    }

    fun retryMetricChart() {
        // Error 상태에서는 해당 metric을, Hidden 이 아닌 이전 선택이 있으면 그 metric을 다시 조회한다.
        val metricType = _uiState.value.selectedChartMetricType
            ?: _uiState.value.selectedMetricType
            ?: return

        loadMetricChart(metricType)
    }

    private fun observeContext() {
        if (observeJob?.isActive == true) {
            return
        }

        observeJob = viewModelScope.launch {
            observeLearningStateUseCase().collect { globalState ->
                // selectedLang 이나 현재 언어의 LangState.updatedAt 이 바뀌면
                // 001 초기 컨텍스트를 다시 조립한다.
                val signature = StatisticsContextSignature(
                    selectedLanguage = globalState.selectedLang,
                    currentLangUpdatedAt = globalState.currentLangState()?.updatedAt
                )

                if (signature != lastObservedSignature) {
                    lastObservedSignature = signature
                    loadOverview()
                }
            }
        }
    }

    private fun loadOverview() {
        if (loadJob?.isActive == true) {
            Log.d(TAG, "loadOverview() skipped — already loading")
            // 이미 준비 중인 상태에서 또 들어온 요청은 끝난 뒤 한 번만 다시 수행한다.
            pendingReload = true
            return
        }

        // 이번 로딩 사이클에는 추가 reload가 필요하지 않도록 먼저 비워 둔다.
        pendingReload = false
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    isRetryable = false,
                    overview = null,
                    metricSummaryCards = emptyList()
                )
            }

            // STAT-001은 Dashboard preload 여부에 의존하지 않도록 먼저 local cache를 채운다.
            // preload 실패는 화면 전체 실패로 처리하지 않고, 아래 overview 조립 성공 여부를 본다.
            preloadLearningStateUseCase().exceptionOrNull()?.let { error ->
                Log.w(TAG, "preloadLearningStateUseCase failed", error)
            }

            // 현재 userId / selectedLanguage / LangState.external 이 모두 준비되면
            // 후속 카드와 차트가 받을 초기 입력 스냅샷을 만든다.
            getStatisticsOverviewUseCase()
                .onSuccess { overview ->
                    // overview 자체는 화면 진입의 핵심 입력이므로,
                    // 카드 데이터 변환은 여기서 한 번만 수행해 uiState에 넣는다.
                    val metricCards = overview.toMetricSummaryItems()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            overview = overview,
                            metricSummaryCards = metricCards,
                            errorMessage = null,
                            isRetryable = false
                        )
                    }
                }
                .onFailure { error ->
                    // 준비해야 할 컨텍스트가 하나라도 비어 있으면 retry 가능한 Error 상태로 보낸다.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            overview = null,
                            metricSummaryCards = emptyList(),
                            errorMessage = error.message ?: "Statistics 초기 상태를 불러오지 못했습니다.",
                            isRetryable = true
                        )
                    }
            }
        }.also { job ->
            job.invokeOnCompletion {
                loadJob = null
                if (pendingReload) {
                    pendingReload = false
                    loadOverview()
                }
            }
        }
    }

    private fun loadMetricChart(metricType: StatisticsMetricType) {
        val overview = _uiState.value.overview
        val queryState = overview?.historyQueryState

        if (queryState == null) {
            // STAT-003 chart는 STAT-001 overview가 만든 query context 없이는 조회할 수 없다.
            _uiState.update {
                it.copy(
                    metricChartState = StatisticsMetricChartState.Error(
                        metricType = metricType,
                        message = "차트를 불러올 초기 상태가 없습니다."
                    )
                )
            }
            return
        }

        // 이 요청 번호와 완료 시점의 번호가 다르면 사용자가 이미 다른 카드를 눌렀거나 닫은 상태다.
        val requestVersion = chartRequestVersion + 1L
        chartRequestVersion = requestVersion
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _uiState.update {
                it.copy(metricChartState = StatisticsMetricChartState.Loading(metricType))
            }

            getMetricHistoryPointsUseCase(queryState, metricType)
                .onSuccess { points ->
                    // 이전 요청 결과가 늦게 도착해 현재 dialog를 덮어쓰는 것을 방지한다.
                    if (requestVersion != chartRequestVersion) return@onSuccess

                    _uiState.update {
                        it.copy(metricChartState = points.toStatisticsMetricChartState(metricType))
                    }
                }
                .onFailure { error ->
                    // 실패도 오래된 요청이면 화면에 보여주지 않는다.
                    if (requestVersion != chartRequestVersion) return@onFailure

                    _uiState.update {
                        it.copy(
                            metricChartState = StatisticsMetricChartState.Error(
                                metricType = metricType,
                                message = error.message ?: "차트를 불러오지 못했습니다."
                            )
                        )
                    }
                }
        }.also { job ->
            job.invokeOnCompletion {
                if (chartJob === job) {
                    chartJob = null
                }
            }
        }
    }

    private companion object {
        const val TAG = "StatisticsViewModel"
    }

    private data class StatisticsContextSignature(
        // 현재 선택 언어가 바뀌면 Statistics 진입 상태도 다시 조립해야 한다.
        val selectedLanguage: com.example.umma.domain.model.learningstate.LangCode?,
        // 같은 언어여도 LangState.updatedAt 이 바뀌면 새 snapshot 으로 다시 준비한다.
        val currentLangUpdatedAt: Long?
    )
}
