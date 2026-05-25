package com.example.umma.presentation.statistics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.model.learningstate.currentLangState
import com.example.umma.domain.model.learningstate.selectedLang
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.domain.usecase.statistics.ObserveStatisticsHistoryUseCase
import com.example.umma.domain.usecase.statistics.GetStatisticsOverviewUseCase
import com.example.umma.domain.usecase.statistics.GetMetricHistoryPointsUseCase
import com.example.umma.domain.usecase.statistics.RefreshStatisticsHistoryUseCase
import com.example.umma.presentation.statistics.model.StatisticsMetricChartState
import com.example.umma.presentation.statistics.model.toStatisticsMetricChartState
import com.example.umma.presentation.statistics.model.toMetricSummaryItems
import com.example.umma.presentation.statistics.model.StatisticsSyncState
import com.example.umma.presentation.statistics.model.resolveStatisticsSyncState
import com.example.umma.presentation.statistics.model.isVisible
import com.example.umma.domain.model.statistics.toMetricPoint
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
    private val observeStatisticsHistoryUseCase: ObserveStatisticsHistoryUseCase,
    private val refreshStatisticsHistoryUseCase: RefreshStatisticsHistoryUseCase,
    private val getStatisticsOverviewUseCase: GetStatisticsOverviewUseCase,
    private val getMetricHistoryPointsUseCase: GetMetricHistoryPointsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    // 화면 재진입/재구성 중 중복 초기화가 겹치지 않도록 load job 을 하나만 유지한다.
    private var loadJob: Job? = null
    // chart 요청은 카드 클릭마다 새로 발생하므로 overview 로딩과 별도 job 으로 관리한다.
    private var chartJob: Job? = null
    // statistics history는 local observe를 계속 붙잡고 있다가 refresh 결과가 오면 chart와 sync 상태를 갱신한다.
    private var historyObserveJob: Job? = null
    // Firestore refresh는 local first 렌더링 이후에 별도로 수행한다.
    private var refreshJob: Job? = null
    // 현재 선택 언어/현재 언어의 updatedAt 이 바뀌면 다시 준비해야 하는지 추적한다.
    private var pendingReload: Boolean = false
    private var observeJob: Job? = null
    private var lastObservedSignature: StatisticsContextSignature? = null
    private var latestHistoryState: StatisticsHistoryState? = null
    private var pendingHistoryStateDuringRefresh: StatisticsHistoryState? = null
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
        // 새 language/context로 갈아타는 순간 이전 chart/history refresh 는 더 이상 유효하지 않다.
        historyObserveJob?.cancel()
        historyObserveJob = null
        refreshJob?.cancel()
        refreshJob = null
        chartJob?.cancel()
        chartJob = null
        chartRequestVersion += 1L
        latestHistoryState = null
        pendingHistoryStateDuringRefresh = null
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    isRetryable = false,
                    overview = null,
                    metricSummaryCards = emptyList(),
                    selectedMetricType = null,
                    metricChartState = StatisticsMetricChartState.Hidden,
                    syncState = StatisticsSyncState.Idle
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
                            isRetryable = false,
                            syncState = StatisticsSyncState.Idle
                        )
                    }
                    observeHistory(overview.historyQueryState)
                    refreshHistory(overview.historyQueryState)
                }
                .onFailure { error ->
                    // 준비해야 할 컨텍스트가 하나라도 비어 있으면 retry 가능한 Error 상태로 보낸다.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            overview = null,
                            metricSummaryCards = emptyList(),
                            errorMessage = error.message ?: "Statistics 초기 상태를 불러오지 못했습니다.",
                            isRetryable = true,
                            syncState = StatisticsSyncState.Idle
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

    private fun observeHistory(queryState: com.example.umma.domain.model.statistics.StatisticsHistoryQueryState) {
        // background refresh가 끝난 뒤에도 같은 observe 스트림이 계속 살아 있어야
        // local cache 변경이 화면과 chart에 자동으로 반영된다.
        historyObserveJob?.cancel()
        historyObserveJob = viewModelScope.launch {
            observeStatisticsHistoryUseCase(queryState).collect { historyState ->
                // refresh 결과와 local pending 상태를 같은 snapshot으로 취급하기 위해
                // 최신 historyState를 따로 들고 있어 refresh 성공 직후 sync 상태를 다시 계산한다.
                latestHistoryState = historyState
                if (refreshJob?.isActive == true) {
                    // Refreshing 표시 중 observe 결과를 화면에 바로 덮지는 않지만,
                    // refresh 종료 직후 최신 local snapshot으로 상태를 복구하기 위해 보관한다.
                    pendingHistoryStateDuringRefresh = historyState
                }
                updateSyncState(historyState)
                updateVisibleChart(historyState)
            }
        }
    }

    private fun refreshHistory(queryState: com.example.umma.domain.model.statistics.StatisticsHistoryQueryState) {
        // refresh는 화면을 직접 갱신하는 단계가 아니라, local cache를 보정하는 단계다.
        // 실제 UI 반영은 observeHistory()가 다시 흘려주는 snapshot을 기준으로 한다.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(syncState = StatisticsSyncState.Refreshing)
            }

            refreshStatisticsHistoryUseCase(queryState)
                .onSuccess {
                    applySyncStateFromLatestHistory()
                }
                .onFailure { error ->
                    Log.w(TAG, "refreshStatisticsHistoryUseCase failed", error)
                    // 실패 상태는 non-blocking 보조 표시로 남긴다.
                    // 기존 local snapshot으로 즉시 덮어쓰면 사용자가 refresh 실패를 알 수 없다.
                    pendingHistoryStateDuringRefresh = null
                    _uiState.update {
                        it.copy(
                            syncState = StatisticsSyncState.Error(
                                message = error.message ?: "최신 history를 불러오지 못했습니다."
                            )
                        )
                    }
                }
        }.also { job ->
            job.invokeOnCompletion {
                if (refreshJob === job) {
                    refreshJob = null
                    applyPendingHistoryStateAfterRefresh()
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

    private fun updateSyncState(historyState: StatisticsHistoryState) {
        // refresh job이 진행 중이면 UI는 별도 refreshing 상태를 유지해야 하므로
        // observe가 도착해도 여기서 덮어쓰지 않는다.
        if (refreshJob?.isActive == true) return
        val syncState = resolveStatisticsSyncState(historyState)

        _uiState.update { current ->
            if (current.syncState is StatisticsSyncState.Error && syncState is StatisticsSyncState.Error) {
                current
            } else {
                current.copy(syncState = syncState)
            }
        }
    }

    private fun applySyncStateFromLatestHistory() {
        // refresh 성공 직후에는 현재 local snapshot을 다시 읽어
        // pending/synced 여부를 한 번 더 계산해 sync 상태를 정리한다.
        val syncState = latestHistoryState?.let(::resolveStatisticsSyncState)
            ?: StatisticsSyncState.Idle

        _uiState.update { it.copy(syncState = syncState) }
    }

    private fun applyPendingHistoryStateAfterRefresh() {
        // refresh job이 active인 동안 들어온 observe 결과는 updateSyncState()에서 일부러 보류된다.
        // completion 시점에 한 번 더 반영해 Refreshing 상태에 갇히거나 최신 sync 상태를 놓치지 않게 한다.
        val pendingState = pendingHistoryStateDuringRefresh ?: return
        pendingHistoryStateDuringRefresh = null
        updateSyncState(pendingState)
    }

    private fun updateVisibleChart(historyState: StatisticsHistoryState) {
        // 차트 dialog가 열려 있을 때만 snapshot 변경을 반영한다.
        // 숨김 상태의 차트까지 계속 갱신하면 오래된 결과가 다시 보일 수 있다.
        val metricType = _uiState.value.selectedChartMetricType ?: return
        if (!_uiState.value.metricChartState.isVisible) return

        val chartState = when (historyState) {
            StatisticsHistoryState.Empty -> StatisticsMetricChartState.Empty(metricType)
            is StatisticsHistoryState.Content -> historyState.histories
                .sortedBy { it.recordedAt }
                .map { it.toMetricPoint(metricType) }
                .toStatisticsMetricChartState(metricType)
            is StatisticsHistoryState.Retry -> StatisticsMetricChartState.Error(
                metricType = metricType,
                message = historyState.cause?.message ?: "차트를 불러오지 못했습니다."
            )
            is StatisticsHistoryState.Error -> StatisticsMetricChartState.Error(
                metricType = metricType,
                message = historyState.cause?.message ?: "차트를 불러오지 못했습니다."
            )
        }

        _uiState.update { it.copy(metricChartState = chartState) }
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
