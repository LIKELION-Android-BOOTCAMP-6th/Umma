package com.app.umma.presentation.correction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
 * COR-007-A 에서는 ViewModel 이 Done 직후 Channel 로 emit 한
 * [CorrectionEvent.NavigateToDashboard] 를 collect 해 [onNavigateToDashboard] 콜백을 호출,
 * 상위 NavHost 가 backstack 을 정리하며 Dashboard 로 복귀시킨다. Channel 기반이라 회전/recomposition
 * 으로 동일 이벤트가 재발화되지 않는다(AC: "완료 성공 이벤트는 한 번만 소비된다").
 * COR-001-B 에서는 [CorrectionUiState.Phase.Empty] (구 NotAvailable) 분기를 [CorrectionEmpty] 컴포저블로
 * 끌어올려 "AI 와 대화하기" CTA 를 노출하고, 클릭 시 [onNavigateToChat] 콜백으로 위임한다.
 * Loading / Generating / Error phase 의 사용자 노출 디자인은 후속 backlog 에서 다룬다.
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
        // 나머지 phase 는 COR-002-A 시각 검증용 텍스트 분기를 그대로 유지하며,
        // 사용자 노출 디자인은 후속 backlog 에서 다룬다.
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

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (uiState.phase) {
                        CorrectionUiState.Phase.Loading -> {
                            Text(text = "로딩 중…", textAlign = TextAlign.Center)
                        }

                        CorrectionUiState.Phase.Ready -> {
                            // Ready 진입 직후 ViewModel 이 즉시 Generating 으로 전이시키므로 이 분기는 보통 한 프레임만 보인다.
                            Text(text = "교정 결과를 준비합니다…", textAlign = TextAlign.Center)
                            Text(
                                text = "언어=${uiState.selectedLearningLanguage?.code} · " +
                                        "최근 주제=${uiState.sessionSummary?.recentTopic ?: "-"}",
                                textAlign = TextAlign.Center
                            )
                        }

                        CorrectionUiState.Phase.Generating -> {
                            Text(text = "AI 가 교정 결과를 생성 중…", textAlign = TextAlign.Center)
                        }

                        CorrectionUiState.Phase.Error -> {
                            Text(text = "교정 결과 생성에 실패했어요", textAlign = TextAlign.Center)
                            uiState.errorReason?.let { reason ->
                                Text(text = "사유: $reason", textAlign = TextAlign.Center)
                            }
                        }

                        CorrectionUiState.Phase.Done -> {
                            // COR-006-A: 완료 파이프라인 성공 안내. Dashboard 복귀 버튼은 COR-007-A 가
                            // 1회성 navigation 이벤트로 잇는다(이 화면에서 머무는 시간은 짧을 예정).
                            val savedCount = uiState.completionResult?.savedFlashcardIds?.size ?: 0
                            Text(text = "저장이 완료되었어요", textAlign = TextAlign.Center)
                            Text(
                                text = "${savedCount}개 카드가 학습 목록에 추가되었어요",
                                textAlign = TextAlign.Center,
                            )
                        }

                        // Content / Empty 분기는 위의 when 에서 이미 처리.
                        else -> Unit
                    }
                }
            }
        }
    }
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
 * 교정 카드 저장 진입 버튼.
 *
 * SSOT: COR-004_Card_Selection.md
 *
 * 활성/비활성 기준은 [CorrectionUiState.canSave] (= Content phase + 1개 이상 선택). 호출자가 그대로 전달한다.
 * 비활성 상태에서는 [BackgroundDeactivated] 회색으로 떨어지고, 활성 상태에서는 [ThemePrimary] 주황으로 표시된다.
 *
 * 비범위:
 *  - 실제 Flashcard 저장 호출 — COR-004 다음 백로그에서 ViewModel.onSaveClicked 본문을 채우는 방식으로 연결.
 */
@Composable
private fun CorrectionSaveButton(
    enabled: Boolean,
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
        Text(text = "저장")
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
