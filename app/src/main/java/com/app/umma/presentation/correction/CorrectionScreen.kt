package com.app.umma.presentation.correction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.umma.core.theme.BackgroundDeactivated
import com.app.umma.core.theme.CardElevation
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextSecondaryR
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.TitleScreenSB
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.ui.component.UmmaDialog
import com.app.umma.domain.model.correction.CorrectionEmptyResultReason
import com.app.umma.presentation.correction.component.CorrectionResultList
import com.app.umma.presentation.correction.component.CorrectionSelectAllBar
import kotlin.math.floor
import kotlinx.coroutines.delay

/**
 * 교정 화면을 구성하는 컴포저블입니다.
 *
 * SSOT: COR-001_Initial_State.md / COR-002_Suggestion_Generation.md /
 *       COR-003_Result_Cards.md / COR-004_Card_Selection.md / COR-006_Completion_Pipeline.md /
 *       COR-007_Return_and_Sync.md
 *
 * COR-002-A 범위에서는 [CorrectionViewModel] 이 결정한 [CorrectionUiState.Phase] 에 따라
 * 텍스트로만 분기해 흐름 진행을 시각적으로 검증한다.
 * COR-003-A 에서 Content 상태는 [CorrectionResultList] 카드 UI 로 교체되었다.
 * COR-004 에서는 Content 상태에서 카드 목록 아래에 [CorrectionSaveButton] 을 띄워
 * 선택 상태 → 저장 진입점을 연결한다.
 * COR-006-A 에서는 완료 파이프라인 성공 직후 [CorrectionUiState.Phase.Done] 으로 전환되지만,
 * COR-007 복귀 이벤트가 지연 없이 발화되므로 별도 완료 안내 UI 를 사용자에게 보여주지 않는다.
 * COR-006-B 에서는 로컬 완료 실패 시 [CorrectionUiState.Phase.Retry] 로 전환되어
 * 카드 목록과 저장 버튼은 그대로 유지된 채 상단에 [CorrectionCompletionRetryBanner] 가
 * 실패 사유와 함께 노출된다. 사용자가 같은 저장 버튼을 다시 누르면 ViewModel 의
 * [CorrectionViewModel.onSaveClicked] → [CorrectionViewModel.launchCompletion] 이 같은 saveRequest 로 재진입한다.
 * COR-007-A 에서는 ViewModel 이 Done 직후 Channel 로 emit 한
 * [CorrectionEvent.NavigateToDashboard] 를 collect 해 [onNavigateToDashboard] 콜백을 호출,
 * 상위 NavHost 가 backstack 을 정리하며 Dashboard 로 복귀시킨다. Channel 기반이라 회전/recomposition
 * 으로 동일 이벤트가 재발화되지 않는다(AC: "완료 성공 이벤트는 한 번만 소비된다").
 * COR-001-B 에서는 [CorrectionUiState.Phase.Empty] (구 NotAvailable) 분기를 [CorrectionEmpty] 컴포저블로
 * 끌어올려 "AI 와 대화하기" CTA 를 노출하고, 클릭 시 [onNavigateToChat] 콜백으로 위임한다.
 * COR-002-B 에서는 AI 응답 0건([CorrectionUiState.Phase.EmptyResult]) 을 [CorrectionEmptyResult] 로,
 * 호출/파싱/필수 필드 실패([CorrectionUiState.Phase.Error]) 를 [CorrectionError] + Retry 버튼으로 연결한다.
 * COR-003-B 에서는 Loading / Ready / Generating 세 phase 를 [CorrectionLoading] 단일 컴포저블로 묶되,
 * 5단계 안내 문구와 점 애니메이션으로 준비 흐름이 계속 진행 중임을 표시한다.
 *
 * @param onNavigateToDashboard 완료 파이프라인 성공 후 Dashboard 로 복귀시켜야 할 때 호출되는 1회성 콜백.
 *  실제 navigate 와 backstack 정리(popUpTo<CorrectionGraph> inclusive=true + launchSingleTop) 책임은
 *  상위 NavHost 가 가진다.
 * @param onNavigateToChat COR-001-B: Empty 상태 CTA 가 클릭됐을 때 호출되는 콜백. AI Chat 으로 이동시키며,
 *  탭 전환 정책(popUpTo<Dashboard>{saveState=true} + launchSingleTop + restoreState)은 상위 NavHost 가
 *  결정한다. ViewModel 이 결정하는 1회성 effect 가 아니라 사용자 직접 의도라서 콜백 직결 흐름이다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionScreen(
    onNavigateToDashboard: (String) -> Unit,
    onNavigateToChat: () -> Unit,
    viewModel: CorrectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val correctionReviewReportConfirmDialogState = rememberSaveable { mutableStateOf(false) }
    var correctionReviewReportNote by rememberSaveable { mutableStateOf("") }
    // 선택 0개로 저장 버튼 클릭 시 노출. 확인 → onSkipSaveAndExit, 취소 → 화면 유지.
    val skipSaveDialogVisible = rememberSaveable { mutableStateOf(false) }
    // 선택 1개 이상으로 저장 버튼 클릭 시 노출. 확인 → onSaveClicked, 취소 → 화면 유지.
    val confirmSaveDialogVisible = rememberSaveable { mutableStateOf(false) }

    // 화면 진입 시 1회만 Flow 셋업. ViewModel 내부에 가드가 있어 재호출되어도 안전.
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    // COR-FIX-014: onCleared() 만으로는 홈 버튼 백그라운드 진입 시 TTS 가 즉시 멈추지 않는다 —
    // ViewModel 은 화면 dispose 시점에야 clear 되기 때문이다. SRS 화면과 동일하게 화면 이탈
    // (ON_STOP / onDispose) 시 stopPronunciation() 을 명시적으로 호출해 재생을 끊는다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopPronunciation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPronunciation()
        }
    }

    // COR-007-A: 1회성 effect 채널 collect.
    // - Channel.receiveAsFlow() 라 각 emit 은 단일 collector 에 정확히 한 번 전달된다.
    //   회전/recomposition 으로 LaunchedEffect 가 재시작되어도 이미 소비된 element 는 재발화되지 않는다.
    // - key=Unit — 화면 lifecycle 동안 단 한 번의 collect coroutine 만 유지.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CorrectionEvent.NavigateToDashboard -> onNavigateToDashboard(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "교정",
                isCenterTitle = true,
                leadingActions = {
                    if (uiState.showCorrectionReviewReportButton) {
                        Button(
                            onClick = { correctionReviewReportConfirmDialogState.value = true },
                            enabled = !uiState.isCorrectionReviewReporting &&
                                !uiState.hasCorrectionReviewReported,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .height(32.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (uiState.hasCorrectionReviewReported) {
                                    ThemePrimary.copy(alpha = 0.36f)
                                } else {
                                    ThemePrimary
                                }
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.hasCorrectionReviewReported) {
                                    ThemePrimary.copy(alpha = 0.16f)
                                } else {
                                    ThemePrimary
                                },
                                contentColor = Color.White,
                                disabledContainerColor = ThemePrimary.copy(alpha = 0.14f),
                                disabledContentColor = ThemePrimary.copy(alpha = 0.72f),
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Text(
                                text = if (uiState.hasCorrectionReviewReported) "접수됨" else "신고",
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        // COR-003-A: Content phase 는 카드 목록(LazyColumn)을 전체 영역에 그린다.
        // COR-004:   Content 분기에서는 카드 목록(weight=1f) 아래에 저장 버튼을 화면 하단에 고정한다.
        //            Scaffold.bottomBar 슬롯이 아닌 content 내부에 두는 이유 — 다른 phase 에서는
        //            저장 버튼 자체가 의미가 없어 bottomBar 가 빈 영역으로 남는 문제를 피한다.
        // COR-003-B: Loading / Ready / Generating 은 [CorrectionLoading] 단일 분기로 묶어
        //            CircularProgressIndicator 를 노출한다. 세 phase 에서 카드 목록이 호출되지 않음을
        //            when 분기 완전성으로 컴파일 타임에 보장한다(엣지: "Loading 중 중복 카드 노출" 차단).
        when (uiState.phase) {
            CorrectionUiState.Phase.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // COR-005-B: 변환 실패가 있으면 카드 목록 위에 빨간 배너로 노출한다.
                    // Column 의 첫 자식이라 스크롤 영역(weight=1f) 위에 자연스럽게 고정된다.
                    uiState.saveErrorReason?.let { reason ->
                        CorrectionSaveErrorBanner(
                            reason = reason,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CorrectionSelectAllBar(
                        totalCount = uiState.suggestions.size,
                        selectedCount = uiState.selectedSuggestionIds.size,
                        allSelected = uiState.areAllSuggestionsSelected,
                        onToggleSelectAll = viewModel::toggleSelectAll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CorrectionResultList(
                        suggestions = uiState.suggestions,
                        speakingSuggestionId = uiState.speakingSuggestionId,
                        selectedIds = uiState.selectedSuggestionIds,
                        onCardClicked = viewModel::toggleSuggestionSelection,
                        onSpeak = viewModel::onPlaySuggestionAudio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    // COR-UX-007: enabled 는 in-flight 여부만 가드(canOpenSaveDialog).
                    // 선택 수와 무관하게 버튼이 활성되어, 클릭 시 0개/N개 분기 다이얼로그로 진입한다.
                    CorrectionSaveButton(
                        enabled = uiState.canOpenSaveDialog,
                        isLoading = uiState.isSavePreparing || uiState.isCompleting,
                        onClick = {
                            if (uiState.selectedSuggestionIds.isEmpty()) {
                                skipSaveDialogVisible.value = true
                            } else {
                                confirmSaveDialogVisible.value = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpacingL, vertical = SpacingM)
                    )
                }
            }

            // COR-006-B: 로컬 완료 실패 → Retry 분기. 화면 구조는 Content 와 동일하게 카드 목록 + 저장 버튼을
            // 그대로 유지해 사용자가 같은 입력으로 다시 저장 버튼을 누를 수 있게 한다.
            // 카드 목록 위에 실패 사유를 알리는 [CorrectionCompletionRetryBanner] 를 추가로 띄운다.
            // saveErrorReason 과 completionErrorReason 은 의미가 다르므로(=변환 단계 vs 완료 단계) 동시에
            // 채워질 일은 정상 흐름에서 없지만, 방어적으로 둘 다 노출 가능한 구조로 둔다.
            CorrectionUiState.Phase.Retry -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    uiState.completionErrorReason?.let { reason ->
                        CorrectionCompletionRetryBanner(
                            reason = reason,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    uiState.saveErrorReason?.let { reason ->
                        CorrectionSaveErrorBanner(
                            reason = reason,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CorrectionSelectAllBar(
                        totalCount = uiState.suggestions.size,
                        selectedCount = uiState.selectedSuggestionIds.size,
                        allSelected = uiState.areAllSuggestionsSelected,
                        onToggleSelectAll = viewModel::toggleSelectAll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CorrectionResultList(
                        suggestions = uiState.suggestions,
                        speakingSuggestionId = uiState.speakingSuggestionId,
                        selectedIds = uiState.selectedSuggestionIds,
                        onCardClicked = viewModel::toggleSuggestionSelection,
                        onSpeak = viewModel::onPlaySuggestionAudio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    // COR-UX-007: Content 분기와 동일한 정책. Retry phase 에서도 0개/N개 분기 다이얼로그로 진입.
                    CorrectionSaveButton(
                        enabled = uiState.canOpenSaveDialog,
                        isLoading = uiState.isSavePreparing || uiState.isCompleting,
                        onClick = {
                            if (uiState.selectedSuggestionIds.isEmpty()) {
                                skipSaveDialogVisible.value = true
                            } else {
                                confirmSaveDialogVisible.value = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpacingL, vertical = SpacingM)
                    )
                }
            }

            // COR-001-B: 결손 케이스(언어/세션/LangState 없음, correctionAvailable=false)는 단일 Empty 분기로
            // 묶고, 짧은 안내 + AI Chat 이동 CTA 를 함께 노출한다. 어느 필드가 비었는지는 ViewModel 의
            // logcat (notAvailableReason) 으로만 추적하며, UiState 표면에 별도 reason 필드는 두지 않는다.
            CorrectionUiState.Phase.Empty -> {
                CorrectionEmpty(
                    onNavigateToChat = onNavigateToChat,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            // COR-002-B: AI 응답이 0건인 경우. Ready 게이트 미통과([Phase.Empty])와 다른 의미.
            // 유일한 CTA "AI 와 대화하기" 로 대화를 더 이어갈 수 있다.
            CorrectionUiState.Phase.EmptyResult -> {
                CorrectionEmptyResult(
                    reason = uiState.emptyResultReason,
                    onNavigateToChat = onNavigateToChat,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            // COR-002-B: AI 호출/파싱/필수 필드 누락 실패. Retry 버튼으로 같은 Session Memory 기준 재시도.
            CorrectionUiState.Phase.Error -> {
                CorrectionError(
                    errorReason = uiState.errorReason,
                    onRetry = viewModel::onRetryClicked,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            // COR-003-B: 카드가 등장하기 전 대기 구간을 단일 Loading UI 로 표시한다.
            // - Loading: preload 대기 (첫 emit 전)
            // - Ready  : ViewModel 이 즉시 Generating 으로 전이시키므로 보통 한 프레임만 보인다.
            // - Generating: AI 호출 in-flight.
            // 세 phase 모두 사용자 관점에서 동일한 "준비 중" 의미이므로 같은 컴포저블로 통합한다.
            CorrectionUiState.Phase.Loading,
            CorrectionUiState.Phase.Ready,
            CorrectionUiState.Phase.Generating -> {
                // COR-UX-001: 단계 인덱스/복습 카드 목록은 ViewModel(triggerGeneration)이 구동하는
                // 단일 진실([CorrectionUiState.loadingStep]/[CorrectionUiState.loadingFlashcards])이다.
                // 화면은 그대로 받아 렌더링만 한다.
                CorrectionLoading(
                    currentStep = uiState.loadingStep,
                    flashcards = uiState.loadingFlashcards,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            CorrectionUiState.Phase.Restoring,
            CorrectionUiState.Phase.Done -> {
                // COR backlog: 완료 성공 직후 Dashboard 이벤트가 즉시 발화되므로 별도 완료 안내 UI 를 노출하지 않는다.
                // navigation 콜백 처리 전 아주 짧은 프레임이 생겨도 완료 문구나 카드 수는 보여주지 않는다.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ThemePrimary)
                }
            }
        }
    }

    if (correctionReviewReportConfirmDialogState.value) {
        UmmaDialog(
            title = "교정 신고",
            titleColor = ThemePrimary,
            modifier = Modifier.padding(horizontal = SpacingL),
            onCancel = { correctionReviewReportConfirmDialogState.value = false },
            onConfirm = {
                correctionReviewReportConfirmDialogState.value = false
                viewModel.reportCorrectionPromptReview(reportNote = correctionReviewReportNote)
            },
            confirmText = "신고",
            dismissText = "취소",
            showCancelButton = false,
            confirmButtonColor = ThemePrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "이 교정 결과를 리뷰용으로 남길까요?\n교정 카드와 입력 맥락이 저장됩니다.",
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = correctionReviewReportNote,
                    onValueChange = { value ->
                        correctionReviewReportNote =
                            value.take(CORRECTION_REVIEW_REPORT_NOTE_MAX_LENGTH)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpacingL),
                    minLines = 3,
                    maxLines = 5,
                    label = {
                        Text(text = "이상했던 점")
                    },
                    placeholder = {
                        Text(text = "예: 뜻이 바뀌었거나 설명이 너무 어려웠어요.")
                    },
                    supportingText = {
                        Text(
                            text = "${correctionReviewReportNote.length}/$CORRECTION_REVIEW_REPORT_NOTE_MAX_LENGTH"
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = ThemePrimary,
                        unfocusedIndicatorColor = Color.Black.copy(alpha = 0.18f),
                        focusedLabelColor = ThemePrimary,
                    )
                )
            }
        }
    }

    // COR-UX-007: 선택 카드가 0개일 때 노출. 확인 → 캐시 정리 + Dashboard 복귀, 취소 → 화면 유지.
    if (skipSaveDialogVisible.value) {
        UmmaDialog(
            title = "저장할 카드가 없어요",
            modifier = Modifier.padding(horizontal = SpacingL),
            onCancel = { skipSaveDialogVisible.value = false },
            onConfirm = {
                skipSaveDialogVisible.value = false
                viewModel.onSkipSaveAndExit()
            },
            confirmText = "확인",
            dismissText = "취소",
            showCancelButton = false,
        ) {
            Text(
                text = "선택하신 카드가 없습니다.\n이후 교정을 위해서는 새로운 대화를 진행하세요.",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingL),
            )
        }
    }

    // COR-UX-007: 선택 카드가 1개 이상일 때 노출. 확인 → 기존 저장 흐름 진입, 취소 → 선택 상태 유지.
    if (confirmSaveDialogVisible.value) {
        UmmaDialog(
            title = "학습 카드 저장",
            modifier = Modifier.padding(horizontal = SpacingL),
            onCancel = { confirmSaveDialogVisible.value = false },
            onConfirm = {
                confirmSaveDialogVisible.value = false
                viewModel.onSaveClicked()
            },
            confirmText = "확인",
            dismissText = "취소",
            showCancelButton = false,
        ) {
            Text(
                text = "카드 ${uiState.selectedSuggestionIds.size}개가 학습 카드로 저장됩니다.",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingL),
            )
        }
    }
}

/**
 * COR-003-B / COR-UX-001: 카드 화면 Loading UI.
 *
 * 노출 조건 (세 phase 를 사용자 관점에서 단일 시각으로 통합):
 *  - [CorrectionUiState.Phase.Loading]    : preload 대기 — 첫 GlobalLangState emit 전.
 *  - [CorrectionUiState.Phase.Ready]      : Ready 게이트 통과 직후. ViewModel 이 즉시 Generating 으로
 *                                           전이시키므로 보통 한 프레임만 보인다.
 *  - [CorrectionUiState.Phase.Generating] : AI 호출 in-flight.
 *
 * 설계 근거: FLOW_CORRECTION.md §3 "사용자는 진입 후 Loading 을 거쳐 교정 결과 카드 목록을 확인한다."
 * 에서 카드 화면의 Loading 은 이 세 phase 를 하나의 "준비 중" 구간으로 표기한다.
 *
 * COR-UX-001 레이아웃 변경: 5칸 바 인디케이터 + 안내 문구를 화면 중앙에서 **하단**으로 옮기고,
 * 비게 된 상단/중앙 영역은 [CorrectionLoadingFlashcards] 복습 카드 자동 재생 영역으로 채운다 —
 * 세로 Column 한 줄로 [카드 영역(weight 1f)] → [안내 문구] → [바 인디케이터] 순서로 쌓는다.
 *
 * 단계 진행([currentStep])은 더 이상 이 컴포저블이 자체 타이머로 구동하지 않는다. "표현을 다듬는 중"
 * 문구가 실제 AI 호출과 어긋나던 문제를 없애기 위해, 맥락 조회 → 후보 추출 → 적응 반영 → AI 호출
 * (시간의 대부분) → 결과 정리라는 실제 파이프라인 진행에 맞춰 [CorrectionViewModel.triggerGeneration]
 * 이 [CorrectionUiState.loadingStep] 을 직접 구동한다 — 본 컴포저블은 그 값을 그대로 렌더링만 한다
 * (SSOT: ViewModel).
 *
 * 인디케이터 색상은 [ThemePrimary] — 다른 화면(학습 버튼 활성 토큰) 과 일관.
 * 안내 텍스트는 사용자에게 "아직 로딩 중" 임을 인지시키는 최소 안내이며, logcat 진단용
 * Ready 디버깅 정보(언어 / 최근 주제)는 이 컴포저블 표면에 노출하지 않는다.
 *
 * @param currentStep [CorrectionUiState.loadingStep] 그대로(0~4). ViewModel 이 실제 진행에 맞춰
 *  구동하므로 4단계(인덱스 3)는 실제 AI 호출이 끝날 때까지 유지된다. 방어적으로 단계 수 범위에 clamp.
 * @param flashcards [CorrectionUiState.loadingFlashcards] 그대로 — 비어 있으면
 *  [CorrectionLoadingFlashcards] 가 안내 카드로 폴백해 같은 형식으로 반복 재생한다.
 */
@Composable
private fun CorrectionLoading(
    currentStep: Int,
    flashcards: List<CorrectionLoadingCard>,
    modifier: Modifier = Modifier,
) {
    val steps = remember { CorrectionLoadingGuideStep.entries }
    val stepIndex = currentStep.coerceIn(0, steps.lastIndex)
    val currentLabel = steps[stepIndex].label

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // COR-UX-001: 비어 있던 상단/중앙 대기 영역을 복습용 플래시카드 자동 재생으로 채운다 —
        // 기다리는 시간이 곧 복습 시간이 되도록.
        CorrectionLoadingFlashcards(
            cards = flashcards,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        CorrectionLoadingGuideText(label = currentLabel)
        Spacer(modifier = Modifier.height(SpacingS))
        CorrectionLoadingStepIndicator(
            currentStep = stepIndex,
            stepCount = steps.size,
        )
        Spacer(modifier = Modifier.height(SpacingL))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "Loading 상태")
@Composable
private fun CorrectionLoadingPreview() {
    CorrectionLoading(currentStep = 1, flashcards = emptyList())
}

/**
 * COR-UX-001: 교정 준비 흐름의 진행 위치를 5개의 가로 막대로 시각화하는 단계 인디케이터.
 * 화면 **하단**에 배치되며, 바로 위 [CorrectionLoadingGuideText] 안내 문구와 한 묶음으로 움직인다.
 *
 * [currentStep] 은 [CorrectionUiState.loadingStep] 을 그대로 받는다 — [CorrectionViewModel.triggerGeneration]
 * 이 실제 파이프라인 진행(맥락 조회 → 후보 추출 → 적응 반영 → AI 호출 → 결과 정리)에 맞춰 구동하므로,
 * 더 이상 "예측 불가능한 AI 대기"를 가리기 위한 순수 연출이 아니라 실제 진행 표시다.
 * 현재 단계까지는 [ThemePrimary]로 채우고, 이후 단계는 [BackgroundDeactivated]로 표시한다.
 *
 * @param currentStep 현재 진행 중인 단계 인덱스 (0-based).
 * @param stepCount 전체 단계 수.
 */
@Composable
private fun CorrectionLoadingStepIndicator(
    currentStep: Int,
    stepCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(stepCount) { i ->
            val color = if (i <= currentStep) ThemePrimary else BackgroundDeactivated
            Box(
                modifier = Modifier
                    .width(LOADING_STEP_BAR_WIDTH)
                    .height(LOADING_STEP_BAR_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

/**
 * COR-UX-001: 교정 파이프라인의 실제 작업 경계에 맞춘 5단계 안내 문구.
 *
 * [CorrectionViewModel.triggerGeneration] 이 [CorrectionUiState.loadingStep] 으로 구동하는 실제
 * 진행과 1:1 대응한다 — 맥락 조회 → candidate 추출 → 적응 프로파일 반영 → AI 호출(시간의 대부분을
 * 차지) → 결과 정리 순. 화면은 이 라벨을 그대로 노출하고 말줄임표만 짧게 반복해 진행감을 더할 뿐,
 * 더 이상 시간 기반 자체 진행을 갖지 않는다(SSOT: ViewModel).
 */
private enum class CorrectionLoadingGuideStep(val label: String) {
    LoadingConversation("최근 대화를 불러오고 있어요"),
    SelectingContent("교정할 내용을 고르고 있어요"),
    ReflectingLearnerLevel("이용자의 학습 수준을 반영하고 있어요"),
    RefiningExpression("자연스러운 표현으로 다듬고 있어요"),
    FinalizingContent("교정된 내용을 정리하고 있어요"),
}

@Composable
private fun CorrectionLoadingGuideText(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = animatedEllipsis(),
            modifier = Modifier.width(LOADING_ELLIPSIS_WIDTH),
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun animatedEllipsis(): String {
    var dotCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(LOADING_ELLIPSIS_INTERVAL_MS)
            dotCount = (dotCount + 1) % (LOADING_ELLIPSIS_MAX_DOTS + 1)
        }
    }

    return ".".repeat(dotCount)
}

// COR-UX-001: 자체 단계 타이머 상수(LOADING_GUIDE_STEP_INTERVAL_MS)는 제거됐다 — 단계 진행은
// CorrectionViewModel.triggerGeneration 이 loadingStep 으로 직접 구동한다(화면은 렌더링만).
// 말줄임표 증감 속도만 더 빠르게 조정한다(600ms → 300ms).
private const val LOADING_ELLIPSIS_INTERVAL_MS = 300L
private const val LOADING_ELLIPSIS_MAX_DOTS = 3
private val LOADING_ELLIPSIS_WIDTH = 18.dp
private val LOADING_STEP_BAR_WIDTH = 24.dp
private val LOADING_STEP_BAR_HEIGHT = 4.dp

// COR-UX-001: 로딩 복습 카드 — 한 장당 앞/뒷면 노출 시간(총 8초/장)과 뒤집기 애니메이션 길이.
// 리파인먼트 2차 조정(사용자 에뮬레이터 확인 후 요청): 앞면은 짧게 훑고 뒷면(교정문)은 더 오래
// 머무르도록 면별로 다른 노출 시간을 둔다 — 앞 3.5초 / 뒤 4.5초. 카드 높이는 1차 조정의 300dp 가
// 다소 커 보인다는 피드백에 맞춰 270dp 로 살짝 줄였다(원래의 230dp 보다는 여전히 크다).
private const val LOADING_CARD_FRONT_FACE_MS = 3_500L
private const val LOADING_CARD_BACK_FACE_MS = 4_500L
private const val LOADING_CARD_FLIP_DURATION_MS = 500
private val LOADING_CARD_HEIGHT = 270.dp

/**
 * COR-UX-001: 로딩 대기 시간을 채우는 복습용 플래시카드 자동 재생 영역.
 *
 * [CorrectionUiState.loadingFlashcards](현재 학습 언어의 로컬 `Flashcard` 를 오래된 순으로 옮겨 담은
 * 경량 표시 모델 목록)를 한 장씩 순환 재생한다 — 앞면(원어 문장) [LOADING_CARD_FRONT_FACE_MS](3.5초)
 * → 뒤집기 애니메이션 → 뒷면(교정문) [LOADING_CARD_BACK_FACE_MS](4.5초) → 다음 카드(총 8초/장),
 * 마지막 카드 다음에는 처음부터 반복한다. "기다리는 시간이 곧 복습 시간"이 되도록 하기 위함이다.
 * 면별 노출 시간은 사용자 확인을 거쳐 두 차례 조정했다 — 1차: 둘 다 3초 → 4.5초로 동일하게 확대,
 * 2차(현재): 앞면은 짧게 훑고 뒷면(정답)에 더 머물도록 앞 3.5초 / 뒤 4.5초로 비대칭화.
 *
 * SRS 학습 카드(`SrsCardFront`/`SrsCardBack`, `SrsStudyScreen.kt`)와 달리 평가 버튼·스피커 아이콘·
 * GrammarNote·탭 힌트를 모두 제외한 표시 전용 축소판이다. SRS 카드는 단순 조건부 스왑이라 뒤집기
 * 애니메이션이 없으므로, 본 컴포저블은 [graphicsLayer] 의 `rotationY` 로 3D 뒤집기를 새로 구현한다.
 *
 * ### 순환 상태 — "단조 증가 누적 각도" 1개로 표현(리파인먼트)
 * 1차 구현은 `cardIndex`(현재 카드 번호)와 `showBack`(앞/뒷면 여부) 두 상태를 따로 두고
 * 회전 애니메이션과 별개로 갱신했는데, `showBack` 이 `false` 로 돌아오는 순간 `cardIndex` 를
 * 먼저 올려버려 "회전이 채 끝나기 전(여전히 90도 너머)에는 화면에 다음 카드의 뒷면이 잠깐
 * 비치는" 글리치가 있었다 — 상태 갱신 타이밍과 회전 애니메이션 진행이 어긋난 탓이다.
 *
 * 리파인먼트는 이 둘을 [targetAngle] 단 하나의 단조 증가 각도로 합쳤다. [LOADING_CARD_FRONT_FACE_MS]
 * (앞면 노출 후) 또는 [LOADING_CARD_BACK_FACE_MS](뒷면 노출 후) 만큼 번갈아 기다린 뒤 180도씩
 * 더하기만 하고, 화면에 무엇을 그릴지는 **현재 애니메이션 각도의 순수 함수**로 파생한다(아래
 * `faceIndex`/`isBack`/`card`) — 두 지연 시간이 서로 달라도 "각도가 곧 화면"이라는 불변식은
 * 그대로 유지된다. 한 면이 180도를 차지하므로 면이 바뀌는 경계(각도 = 90, 270, 450 ...)는 정확히
 * 카드가 옆모습이 되어 "보이지 않는" 모서리 지점과 일치한다 — 즉 콘텐츠 전환이 항상 카드가 안
 * 보이는 순간에만 일어나 글리치가 구조적으로 사라진다.
 *
 * @param cards 노출할 카드 목록(오래된 순). 비어 있으면 [LOADING_FALLBACK_CARD] 단일 카드를 같은
 *  형식으로 반복 재생해 "추후 학습 카드 저장 시 교정 로딩 창에 표시됩니다!" 를 안내한다 — 카드 한
 *  장뿐인 경우도 동일한 순환 로직으로 자연스럽게 반복된다.
 */
@Composable
private fun CorrectionLoadingFlashcards(
    cards: List<CorrectionLoadingCard>,
    modifier: Modifier = Modifier,
) {
    // 빈 목록(미저장/조회 실패)이면 안내 카드 한 장을 같은 형식으로 반복 재생한다 — 폴백도
    // 별도 분기 없이 동일한 순환 로직을 타므로 "카드가 1장뿐인 경우" 처리와 자연히 통일된다.
    val playableCards = remember(cards) { cards.ifEmpty { listOf(LOADING_FALLBACK_CARD) } }

    // 단조 증가 누적 목표 각도 — 매번 반 바퀴(180도)씩만 더한다. 앞면 노출 뒤에는
    // LOADING_CARD_FRONT_FACE_MS, 뒷면 노출 뒤에는 LOADING_CARD_BACK_FACE_MS 만큼 번갈아 기다려
    // 면별로 다른 노출 시간을 준다(짧게 훑는 앞면 vs 더 오래 머무는 뒷면/정답).
    // 360도 = 0도와 시각적으로 동일하므로 값이 계속 커져도 무해하다(자세한 이유는 클래스 KDoc 참고).
    var targetAngle by remember(playableCards) { mutableFloatStateOf(0f) }

    LaunchedEffect(playableCards) {
        targetAngle = 0f
        while (true) {
            // 앞면 노출 → 뒷면으로 뒤집기
            delay(LOADING_CARD_FRONT_FACE_MS)
            targetAngle += FLIP_ROTATION_BACK
            // 뒷면 노출 → 다음 카드 앞면으로 뒤집기
            delay(LOADING_CARD_BACK_FACE_MS)
            targetAngle += FLIP_ROTATION_BACK
        }
    }

    val angle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = tween(durationMillis = LOADING_CARD_FLIP_DURATION_MS),
        label = "correctionLoadingCardFlip",
    )

    // 표시 콘텐츠를 현재 회전각의 순수 함수로 파생한다 — 면 하나가 180도를 차지하므로
    // (각도 + 90) / 180 의 정수부가 "몇 번째 면을 보는 중인가"(faceIndex)를 가리킨다.
    // 짝수 번째 면 = 앞면, 홀수 번째 면 = 뒷면이고, 면 번호를 2로 나눈 몫이 카드 순번이다.
    val faceIndex = floor((angle + FLIP_ROTATION_SWAP_THRESHOLD) / FLIP_ROTATION_BACK).toInt()
    val isBack = faceIndex % 2 == 1
    val card = playableCards[(faceIndex / 2) % playableCards.size]

    Box(
        modifier = modifier.padding(horizontal = SpacingL),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(LOADING_CARD_HEIGHT)
                .graphicsLayer {
                    rotationY = angle
                    cameraDistance = FLIP_CAMERA_DISTANCE * density
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
        ) {
            // 뒷면은 바깥 카드가 이미 180도 가까이 돌아간 상태이므로 rotationY=180 으로 한 번 더
            // 반전해 글자가 거꾸로 보이지 않도록 보정한다(앞면은 보정이 필요 없다).
            CorrectionLoadingCardFace(
                text = if (isBack) card.back else card.front,
                emphasized = isBack,
                modifier = if (isBack) {
                    Modifier.graphicsLayer { rotationY = FLIP_ROTATION_BACK }
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * COR-UX-001: 로딩 복습 카드의 단일 면 — 문장 하나만 중앙 정렬해 보여준다.
 *
 * 앞면(원어 문장, [emphasized]=false)은 SRS 카드 앞면(`SrsCardFront`)과 같은 보조 톤
 * ([TextSecondaryR] + 회색)으로, 뒷면(교정문, [emphasized]=true)은 SRS 카드 뒷면의 정답 문장과 같은
 * 강조 톤([TitleScreenSB])으로 그려 "질문 → 정답"의 체감을 살린다. 라벨/아이콘/설명/평가 버튼 등
 * SRS 의 부가 요소는 모두 생략한 표시 전용 축소판이다.
 *
 * ### 가독성 보정(리파인먼트)
 * 에뮬레이터 확인 후 "문장이 어절 중간에서 줄바꿈돼 읽기 어렵다"는 피드백을 반영해 두 가지를
 * [androidx.compose.ui.text.TextStyle.copy] 로만 보강한다(공유 토큰 [TextSecondaryR]/[TitleScreenSB]
 * 원본은 다른 화면도 함께 쓰므로 절대 변경하지 않는다 — 로컬 `.copy()` 로 이 컴포저블 안에서만 적용):
 * - [LineBreak.Paragraph] — 글자 단위가 아닌 어절(단어) 경계를 우선해 줄을 바꿔 "문장이 끊겨 보이는"
 *   문제를 직접 완화한다.
 * - `lineHeight` 확대 — 줄 간격을 넓혀 여러 줄일 때도 눈으로 따라가기 쉽게 한다.
 * 또한 사용자 결정에 따라 앞면 글자 크기를 16 → 18sp 로 키우되(스크롤 없이 읽기 쉽도록) 앞(작고
 * 옅은 색)·뒤(크고 굵음)의 "질문 → 정답" 대비는 그대로 유지한다 — 카드 높이([LOADING_CARD_HEIGHT])
 * 확대와 함께 적용해 커진 글자도 스크롤 없이 한 화면에 담기게 한다.
 */
@Composable
private fun CorrectionLoadingCardFace(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingL),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (emphasized) {
                TitleScreenSB.copy(lineHeight = LOADING_CARD_BACK_LINE_HEIGHT, lineBreak = LineBreak.Paragraph)
            } else {
                TextSecondaryR.copy(
                    fontSize = LOADING_CARD_FRONT_FONT_SIZE,
                    lineHeight = LOADING_CARD_FRONT_LINE_HEIGHT,
                    lineBreak = LineBreak.Paragraph,
                )
            },
            color = if (emphasized) Color.Unspecified else Color(0xFF777777),
            textAlign = TextAlign.Center,
        )
    }
}

// COR-UX-001: 학습 언어에 저장된 플래시카드가 0개(또는 조회 실패)일 때 반복 재생할 안내 카드.
// 앞면은 교정 결과 선택, 뒷면은 학습 카드 저장을 안내해 빈 상태에서도 다음 학습 흐름을 자연스럽게 보여준다.
private val LOADING_FALLBACK_CARD = CorrectionLoadingCard(
    front = "교정 결과를 선택해서",
    back = "학습 카드로 저장해보세요",
)

// graphicsLayer.rotationY 단위(도) — 한 면이 차지하는 회전폭(180, "정면 ↔ 뒤집힌 정면")과
// 그 절반인 90("옆모습 = 보이지 않는 모서리", 면 전환 경계이자 누적 각도 → faceIndex 변환 기준).
private const val FLIP_ROTATION_BACK = 180f
private const val FLIP_ROTATION_SWAP_THRESHOLD = 90f
private const val CORRECTION_REVIEW_REPORT_NOTE_MAX_LENGTH = 500

// 3D 카드 뒤집기의 원근감(Z축 거리) 보정 계수 — density 를 곱해 화면 밀도에 무관하게 일관된 깊이감을 낸다.
private const val FLIP_CAMERA_DISTANCE = 12f

// COR-UX-001 리파인먼트: 카드 면 가독성 보정값. 공유 토큰([TextSecondaryR] 16sp / [TitleScreenSB] 24sp)
// 원본은 그대로 두고 [CorrectionLoadingCardFace] 에서 .copy() 로만 적용한다 — 앞면은 18sp 로 살짝
// 키우고(원어 문장이 잘 읽히도록), 뒷면은 글자 크기는 유지한 채 줄 간격만 넓혀 "질문(작게) → 정답
// (크게)" 대비를 지킨다. 두 값 모두 어절 단위 줄바꿈([LineBreak.Paragraph])과 함께 적용된다.
private val LOADING_CARD_FRONT_FONT_SIZE = 18.sp
private val LOADING_CARD_FRONT_LINE_HEIGHT = 26.sp
private val LOADING_CARD_BACK_LINE_HEIGHT = 34.sp

/**
 * COR-001-B: 결손 케이스 Empty UI.
 *
 * 노출 조건:
 *  - selectedLearningLanguage 가 없음
 *  - 선택 언어 기준 SessionSummary 가 없음
 *  - 선택 언어 기준 LangState snapshot 이 없음
 *  - SessionSummary.correctionAvailable == false
 *
 * 위 4종 중 어느 분기로 떨어졌는지는 화면에 보여주지 않는다 — 사용자 관점에서는 모두 "지금은 교정할 대화가 없다"
 * 한 가지 의미라 분기 노출이 의미 없고, 실 디버깅은 [CorrectionViewModel] 의 logcat ([com.app.umma.presentation.correction.notAvailableReason])
 * 가 단일 SSOT 다.
 *
 * CTA "AI 와 대화하기" 는 사용자가 교정 가능한 대화를 만들 수 있는 진입점으로 안내한다. 클릭 시 상위 NavHost 가
 * 탭 전환 패턴(`UmmaBottomAppBar` 와 동일: `popUpTo<Dashboard>{saveState=true} + launchSingleTop + restoreState`)
 * 으로 Chat 그래프로 이동시킨다. 사용자가 다시 교정 탭으로 돌아오면 backstack 이 복원되어 자연스러운 재진입을 보장한다.
 */
@Composable
private fun CorrectionEmpty(
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "아직 교정할 대화가 없어요",
            textAlign = TextAlign.Center,
        )
        Text(
            text = "AI 와 대화를 시작해 보세요",
            textAlign = TextAlign.Center,
        )
        Button(
            // BottomBar Chat 탭 라벨("대화") 와 일관된 라벨링. "AI Chat 이동" 문구는 설계 문서 표기.
            onClick = onNavigateToChat,
            shape = RoundedCornerShape(ChipCornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
            modifier = Modifier
                .padding(top = SpacingL)
                .padding(horizontal = SpacingL, vertical = SpacingS),
        ) {
            Text(text = "AI 와 대화하기")
        }
    }
}

/**
 * COR-002-B: AI 응답 0건([CorrectionUiState.Phase.EmptyResult]) UI.
 *
 * 노출 조건:
 *  - Ready 게이트는 통과했으나 AI 가 교정할 부분이 없다고 판단해 빈 목록을 돌려준 경우.
 *  - [CorrectionUiState.Phase.Empty] (Ready 게이트 미통과) 와 다른 의미.
 *
 * AC: "결과가 비어 있으면 Empty 상태를 반환한다" ([COR-002_Suggestion_Generation.md]).
 *
 * CTA: "AI 와 대화하기" — [onNavigateToChat] 호출 → Chat 탭으로 이동해 대화를 더 만든다.
 * "다시 시도"는 [CorrectionViewModel.onRetryClicked] 가 [Phase.Error] 에서만 동작하므로 제공하지 않는다.
 */
@Composable
private fun CorrectionEmptyResult(
    reason: CorrectionEmptyResultReason?,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (reason == CorrectionEmptyResultReason.SAFETY_BLOCKED) {
                "이번 교정 결과에는 저장 가능한 학습 문장이 없어요"
            } else {
                "AI 가 교정할 부분을 찾지 못했어요"
            },
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (reason == CorrectionEmptyResultReason.SAFETY_BLOCKED) {
                "AI 대화를 이어가 보세요"
            } else {
                "대화를 더 이어가 보세요"
            },
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onNavigateToChat,
            shape = RoundedCornerShape(ChipCornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
            modifier = Modifier
                .padding(top = SpacingL)
                .padding(horizontal = SpacingL, vertical = SpacingS),
        ) {
            Text(text = "AI 와 대화하기")
        }
    }
}

/**
 * COR-002-B: AI 호출/파싱/필수 필드 누락 실패([CorrectionUiState.Phase.Error]) UI.
 *
 * 노출 조건:
 *  - AI 요청 자체 실패(네트워크 포함)
 *  - AI 응답 JSON 파싱 실패
 *  - candidateId 매칭 실패
 *  - 필수 필드(nativeText / afterText / explanation) 누락
 *
 * AC: "AI 요청 실패, 응답 파싱 실패, 필수 필드 누락 시 Error 상태로 전환하고 Retry 액션을 제공한다"
 * ([COR-002_Suggestion_Generation.md]).
 *
 * Retry 는 [CorrectionViewModel.onRetryClicked] 를 통해 같은 Session Memory / 현재 선택 언어 기준으로
 * [CorrectionViewModel.triggerGeneration] 을 재진입한다.
 *
 * @param errorReason [CorrectionUiState.errorReason] — raw 진단 사유. 화면에는 직접 노출하지 않는다.
 */
@Composable
private fun CorrectionError(
    errorReason: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "교정 결과 생성에 실패했어요",
            textAlign = TextAlign.Center,
        )
        // COR-UX-002 / COR-FIX-009: raw parser exception / 내부 candidateId 는 UI에 직접 노출하지 않는다.
        if (errorReason != null) {
            Text(
                text = "잠시 후 다시 시도해 주세요.",
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(ChipCornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
            modifier = Modifier
                .padding(top = SpacingL)
                .padding(horizontal = SpacingL, vertical = SpacingS),
        ) {
            Text(text = "다시 시도")
        }
    }
}

/**
 * 교정 카드 저장 진입 버튼.
 *
 * SSOT: COR-004_Card_Selection.md
 *
 * 활성/비활성 기준은 [CorrectionUiState.canSave] (= Content phase + 1개 이상 선택). 호출자가 그대로 전달한다.
 * 비활성 상태에서는 [BackgroundDeactivated] 회색으로 떨어지고, 활성 상태에서는 [ThemePrimary] 주황으로 표시된다.
 * 저장 진행 중([isLoading]=true)에는 CircularProgressIndicator 와 "저장 중…" 텍스트를 함께 표시한다.
 */
@Composable
private fun CorrectionSaveButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(ChipCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = ThemePrimary,
            // disabled 시 회색 배경 — 기존 학습 버튼 비활성 색 토큰 재사용.
            disabledContainerColor = BackgroundDeactivated,
        ),
        modifier = modifier,
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Text(text = "저장 중…")
            }
        } else {
            Text(text = "저장")
        }
    }
}

/**
 * 저장 요청 변환 실패 배너.
 *
 * SSOT: COR-005_Save_Request.md (AC "저장 요청 변환 실패 시 Error 상태를 표시한다").
 *
 * Content phase 카드 목록 위에 깔리며, 색상은 Material3 의 errorContainer / onErrorContainer
 * 슬롯을 그대로 쓴다(별도 디자인 토큰 추가 보류 — 다른 화면도 동일 슬롯을 쓰면 일괄 갱신 가능).
 * raw 사유는 state/logcat 에만 남기고, 사용자 표면은 고정 안내 문구만 노출한다.
 */
@Composable
private fun CorrectionSaveErrorBanner(
    reason: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = SpacingL, vertical = SpacingM),
    ) {
        Text(
            text = "저장 요청을 만들지 못했어요",
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (reason.isNotEmpty()) {
            Text(
                text = "잠시 후 다시 시도해 주세요.",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * 완료 파이프라인 실패 → Retry 안내 배너.
 *
 * SSOT: COR-006_Completion_Pipeline.md (AC "로컬 완료 실패 결과를 받으면 Retry 상태로 남긴다").
 *
 * [CorrectionUiState.Phase.Retry] 진입 시 카드 목록 위에 깔리며, [CorrectionSaveErrorBanner] 와 동일한
 * errorContainer 색상 토큰을 그대로 쓴다. 두 배너는 의미가 다르다 — saveErrorReason 은 저장 요청 변환
 * 단계 실패, 본 배너는 완료 파이프라인(Flashcard 저장 / LangState 갱신 등) 단계 실패.
 *
 * raw 진단 사유는 state/logcat 에만 남긴다. 사용자가 같은 저장 버튼을 다시 누르면 ViewModel 이
 * 같은 saveRequest 로 [CorrectionViewModel.launchCompletion] 에 재진입한다.
 */
@Composable
private fun CorrectionCompletionRetryBanner(
    reason: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = SpacingL, vertical = SpacingM),
    ) {
        Text(
            text = "저장에 실패했어요. 다시 시도해 주세요",
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (reason.isNotEmpty()) {
            Text(
                text = "잠시 후 다시 시도해 주세요.",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
