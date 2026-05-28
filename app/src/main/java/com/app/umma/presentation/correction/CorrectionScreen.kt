package com.app.umma.presentation.correction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.umma.core.theme.BackgroundDeactivated
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.presentation.correction.component.CorrectionResultList

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
 * COR-006-A 에서는 완료 파이프라인 성공 직후 [CorrectionUiState.Phase.Done] 으로 전환되며,
 * 카드 목록과 저장 버튼이 사라지고 안내 텍스트와 저장된 카드 수만 남는다.
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
 * COR-003-B 에서는 Loading / Ready / Generating 세 phase 를 [CorrectionLoading] 단일 컴포저블로 묶어
 * CircularProgressIndicator + 안내 텍스트를 표시한다. 세 phase 는 사용자 관점에서 모두 "카드가 등장하기 전
 * 대기 시간"이라 동일한 시각으로 통합한다(FLOW_CORRECTION.md §3 "진입 후 Loading 을 거쳐 카드 목록 확인").
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
    onNavigateToDashboard: () -> Unit,
    onNavigateToChat: () -> Unit,
    viewModel: CorrectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입 시 1회만 Flow 셋업. ViewModel 내부에 가드가 있어 재호출되어도 안전.
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    // COR-007-A: 1회성 effect 채널 collect.
    // - Channel.receiveAsFlow() 라 각 emit 은 단일 collector 에 정확히 한 번 전달된다.
    //   회전/recomposition 으로 LaunchedEffect 가 재시작되어도 이미 소비된 element 는 재발화되지 않는다.
    // - key=Unit — 화면 lifecycle 동안 단 한 번의 collect coroutine 만 유지.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CorrectionEvent.NavigateToDashboard -> onNavigateToDashboard()
            }
        }
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "교정",
                isCenterTitle = true
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
                    CorrectionResultList(
                        suggestions = uiState.suggestions,
                        selectedIds = uiState.selectedSuggestionIds,
                        onCardClicked = viewModel::toggleSuggestionSelection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    CorrectionSaveButton(
                        enabled = uiState.canSave,
                        isLoading = uiState.isSavePreparing || uiState.isCompleting,
                        onClick = viewModel::onSaveClicked,
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
                    CorrectionResultList(
                        suggestions = uiState.suggestions,
                        selectedIds = uiState.selectedSuggestionIds,
                        onCardClicked = viewModel::toggleSuggestionSelection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    CorrectionSaveButton(
                        enabled = uiState.canSave,
                        isLoading = uiState.isSavePreparing || uiState.isCompleting,
                        onClick = viewModel::onSaveClicked,
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
            // "다시 시도" 로 generate 를 재진입하거나, "AI 와 대화하기" 로 대화를 더 이어갈 수 있다.
            CorrectionUiState.Phase.EmptyResult -> {
                CorrectionEmptyResult(
                    onRetry = viewModel::onRetryClicked,
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
                CorrectionLoading(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            CorrectionUiState.Phase.Done -> {
                // COR-006-A: Done phase — 완료 파이프라인 성공 안내.
                // Dashboard 복귀는 COR-007-A 가 1회성 navigation 이벤트로 잇는다(이 화면에서 머무는 시간은 짧을 예정).
                // Content / Retry / Empty / EmptyResult / Error / Loading / Ready / Generating 은 위에서 이미 처리.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val savedCount = uiState.completionResult?.savedFlashcardIds?.size ?: 0
                    Text(text = "저장이 완료되었어요", textAlign = TextAlign.Center)
                    Text(
                        text = "${savedCount}개 카드가 학습 목록에 추가되었어요",
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * COR-003-B: 카드 화면 Loading UI.
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
 * 인디케이터 색상은 [ThemePrimary] — 다른 화면(학습 버튼 활성 토큰) 과 일관.
 * 안내 텍스트는 사용자에게 "아직 로딩 중" 임을 인지시키는 최소 안내이며, logcat 진단용
 * Ready 디버깅 정보(언어 / 최근 주제)는 이 컴포저블 표면에 노출하지 않는다.
 */
@Composable
private fun CorrectionLoading(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingM),
        ) {
            // Material3 기본 stroke 크기를 유지하고 앱 테마 색만 적용한다.
            CircularProgressIndicator(color = ThemePrimary)
            Text(
                text = "교정 결과를 준비하고 있어요",
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "Loading 상태")
@Composable
private fun CorrectionLoadingPreview() {
    CorrectionLoading()
}

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
 * CTA 두 가지를 제공한다:
 *  - Primary "다시 시도": [onRetry] 호출 → [CorrectionViewModel.onRetryClicked] →
 *    같은 Session Memory / 선택 언어 기준으로 generate 재진입.
 *  - Secondary "AI 와 대화하기": [onNavigateToChat] 호출 → Chat 탭으로 이동해 대화를 더 만든다.
 */
@Composable
private fun CorrectionEmptyResult(
    onRetry: () -> Unit,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AI 가 교정할 부분을 찾지 못했어요",
            textAlign = TextAlign.Center,
        )
        Text(
            text = "다시 시도하거나 대화를 더 이어가 보세요",
            textAlign = TextAlign.Center,
        )
        // Primary CTA: generate 재진입.
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
        // Secondary CTA: Chat 탭 이동.
        Button(
            onClick = onNavigateToChat,
            shape = RoundedCornerShape(ChipCornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
            modifier = Modifier
                .padding(top = SpacingS)
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
 * @param errorReason [CorrectionUiState.errorReason] — null 이면 사유 텍스트를 표시하지 않는다.
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
        // 사유는 디버깅 단서로만 병기. 실제 운영에서는 errorReason 이 기술적 메시지일 수 있으므로
        // UX 디자인이 확정되면 별도 포맷팅을 검토한다.
        errorReason?.let { reason ->
            Text(
                text = "사유: $reason",
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
 * 사용자 안내 문구와 raw 사유를 두 줄로 병기해 디버깅 단서를 남긴다.
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
        Text(
            text = "사유: $reason",
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
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
 * 첫 줄은 사용자 안내, 둘째 줄은 raw 진단 사유. 사용자가 같은 저장 버튼을 다시 누르면 ViewModel 이
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
        Text(
            text = "사유: $reason",
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
