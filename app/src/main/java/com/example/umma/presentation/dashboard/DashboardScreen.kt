package com.example.umma.presentation.dashboard

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.umma.core.theme.BackgroundPrimary
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingXS
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.core.ui.component.UmmaDialog
import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.presentation.auth.AuthViewModel
import com.example.umma.presentation.auth.InitialSetupDialogStep
import com.example.umma.presentation.dashboard.component.AnalyticsCard
import com.example.umma.presentation.dashboard.component.ConversationCard
import com.example.umma.presentation.dashboard.component.DashboardEmpty
import com.example.umma.presentation.dashboard.component.DashboardError
import com.example.umma.presentation.dashboard.component.DashboardSkeleton
import com.example.umma.presentation.dashboard.component.FeedbackCard
import com.example.umma.presentation.dashboard.component.LearningLanguageSelector
import com.example.umma.presentation.dashboard.component.StudyCard

/**
 * 대시보드(홈) 화면.
 *
 * DASH-001 관련:
 *  - Loading 분기: [DashboardSkeleton]
 *  - Empty 분기 (신규 사용자): [DashboardEmpty]
 *  - Content 분기 (기존 placeholder 버튼): [DashboardContent]
 *  - errorMessage Snackbar: AC 7 의 시각 검증용 (cache 유지 + 메시지 노출)
 *  (DASH-001 AC 7: Summary fetch 실패 시 fallback 데이터가 사용된다.)
 *
 * JJ가 갈아끼우는 진입점:
 *  - 카드 4 개 영역(DASH-002): [DashboardContent] 내 임시 Button → 실제 카드 컴포저블로 교체
 *  - 학습 언어 selector(DASH-006): [DashboardContent] 내 임시 Button → 실제 selector 로 교체
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAnalytics: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToFeedbackList: () -> Unit,
    onNavigateToStudyList: () -> Unit,
    onNavigateToMyPage: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // 현재 Activity 컨텍스트. Toast 표시 + UiText.asString(context) 변환용.
    val context = LocalContext.current
    // 직전 뒤로가기 누른 시각 (ms). 2 초 내 두 번 누르면 앱 종료 (오발 방지 더블탭).
    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            (context as? Activity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, "종료하려면 다시 누르세요.", Toast.LENGTH_SHORT).show()
        }
    }

    // ViewModel 의 uiState 를 lifecycle 안전하게 구독한 결과.
    //   값이 바뀌면 Compose 가 recomposition.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    // Snackbar 큐 host. errorMessage 가 세팅되면 LaunchedEffect 가 여기로 showSnackbar 호출.
    val snackbarHostState = remember { SnackbarHostState() }
    var nicknameInput by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(LangCode.KO) }
    // DASH-001: 화면 진입 시 1 회 preload + sync 트리거.
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    // 디버그용: state 변동 시 로그.
    LaunchedEffect(uiState) {
        Log.d("DashboardScreen", "uiState=$uiState")
    }

    LaunchedEffect(uiState.isEmpty) {
        if (uiState.isEmpty && authState.initialSetupDialogStep == InitialSetupDialogStep.NONE) {
            authViewModel.startInitialSetupFlow()
        }
    }

    // DASH-001 AC 7: errorMessage 가 세팅되면 Snackbar 표시.
    //   cache 는 UI 그대로 유지되고 메시지만 노출.
    // (AC 7: Summary fetch 실패 시 fallback 데이터가 사용된다.)
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg.asString(context))
        }
    }

    Scaffold(
        containerColor = BackgroundPrimary,
        topBar = {
            UmmaAppBar(
                title = "Umma",
                isCenterTitle = false,
                actions = {
                    // DASH-006 Phase 1: 학습 언어 selector.
                    //   learningLanguages 가 비어있거나 selectedLang 가 null 인 동안에는
                    //   렌더하지 않는다. 초기 preload 중(isLoading=true) 자연스럽게 hidden.
                    //   selectedLang null 케이스 fallback 처리는 Phase 3 범위.

                    // selector 렌더 가드용 로컬 스냅샷. null 체크 결과를 한 번만 잡아두기 위함.
                    val selected = uiState.selectedLearningLanguage
                    if (selected != null && uiState.learningLanguages.isNotEmpty()) {
                        LearningLanguageSelector(
                            selectedLang = selected,
                            learningLangs = uiState.learningLanguages,
                            isLoading = uiState.isLoading || uiState.isChangingLanguage,
                            onLanguageSelected = { lang ->
                                viewModel.onChangeLearningLanguage(lang.code)
                            }
                        )
                    }
                    // 와이어프레임 정합: AppBar 우측 끝에 마이페이지 진입 IconButton.
                    Spacer(modifier = Modifier.width(SpacingXS))
                    IconButton(onClick = onNavigateToMyPage) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = "마이페이지",
                            tint = TextPrimary
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> DashboardSkeleton()
                uiState.hasFatalError -> DashboardError(onRetry = viewModel::onEnter)
                uiState.isEmpty -> DashboardEmpty(onStartConversation = onNavigateToChat)
                else -> DashboardContent(
                    summary = uiState.summary,
                    onNavigateToAnalytics = onNavigateToAnalytics,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToFeedbackList = onNavigateToFeedbackList,
                    onNavigateToStudyList = onNavigateToStudyList
                )
            }
        }
        // 사용자 닉네임 설정 다이얼로그
        when (authState.initialSetupDialogStep) {
            InitialSetupDialogStep.NONE -> {}
            //
            InitialSetupDialogStep.NICKNAME -> {
                UmmaDialog(
                    title = "닉네임 설정",
                    modifier = Modifier.padding(horizontal = SpacingL),
                    onCancel = {},
                    onConfirm = { authViewModel.onNicknameConfirm(nicknameInput) },
                    confirmText = "확인",
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTextField(
                            value = nicknameInput,
                            onValueChange = { nicknameInput = it },
//                            placeholder = { Text("2~10자 입력") },
                            placeholder = { Text("2~10자 입력") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    if (authState.errorMessage != null) {
                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )
                    }
                }
            }

            // 학습 언어 선택 다이얼로그
            InitialSetupDialogStep.LANGUAGE -> {

                UmmaDialog(
                    title = "학습 언어 선택",
                    modifier = Modifier.padding(horizontal = SpacingL),
                    onCancel = {},
                    onConfirm = {
                        authViewModel.onLanguageSelectAndSave(
                            selectedLang = selectedLanguage,
                        )
                    },
                    confirmText = "완료"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {

                        learningLanguageOptions.forEach { (code, label) ->
                            val isSelected = (selectedLanguage == code)
                            LanguageButton(
                                text = label,
                                isSelected = isSelected,
                                onClick = { selectedLanguage = code }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dashboard "success(content)" 상태에서 보여지는 UI.
 *
 * 와이어프레임 정합: 상단 인사 텍스트 + 2x2 카드 그리드.
 *
 * 카드 4 종은 모두 [summary] 의 필드를 매핑해 받는다 — DASH-001 의 "DashSummary
 * 만 사용한다" 정책. 카드 props 명은 카드 의미(recentConversationTopic 등) 기준이고,
 * 도메인 모델(DashSummary.recentTopic 등) → props 매핑은 여기서만 한 곳에 모아둔다.
 *
 * onClick 시그니처는 현재 () -> Unit. DASH-002~005 본 구현에서 selectedLearningLanguage
 * 전달이 요구되면, 화면 측에서 selected 를 클로저로 capture 해 navigate 람다에 실어 보낸다
 * — 카드 디자인은 그대로 유지.
 */
@Composable
private fun DashboardContent(
    summary: DashSummary?,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToFeedbackList: () -> Unit,
    onNavigateToStudyList: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = SpacingL)
            .fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(SpacingXS))

        Text(
            text = "편안한 대화, 즐거운 학습!",
            style = TextExplanationR,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(SpacingL))

        DashboardCardGrid(
            summary = summary,
            onNavigateToChat = onNavigateToChat,
            onNavigateToStudyList = onNavigateToStudyList,
            onNavigateToFeedbackList = onNavigateToFeedbackList,
            onNavigateToAnalytics = onNavigateToAnalytics
        )
    }
}

/**
 * 2x2 카드 그리드. (대화 / 학습) (교정 / 통계).
 *
 * 모든 카드는 [summary] 의 필드를 받아 표시 — null 인 경우 합리적 기본값으로 매핑.
 *  - non-null 필드(Int/Boolean): ?: 0 / ?: false 로 fallback
 *  - nullable 필드(topic): 그대로 null 전달 — 카드 내부에서 표시 분기.
 */
@Composable
private fun DashboardCardGrid(
    summary: DashSummary?,
    onNavigateToChat: () -> Unit,
    onNavigateToStudyList: () -> Unit,
    onNavigateToFeedbackList: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SpacingM)
        ) {
            ConversationCard(
                recentConversationTopic = summary?.recentTopic,
                recentConversationMinutes = summary?.recentMinutes,
                onClick = onNavigateToChat,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StudyCard(
                dueFlashcards = summary?.dueFlashcards ?: 0,
                recentSavedFlashcards = summary?.savedFlashcards ?: 0,
                onClick = onNavigateToStudyList,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(SpacingM))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SpacingM)
        ) {
            FeedbackCard(
                correctionAvailable = summary?.correctionAvailable ?: false,
                recentConversationMinutes = summary?.recentMinutes,
                onClick = onNavigateToFeedbackList,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            AnalyticsCard(
                grammarScoreDelta = summary?.grammarDelta ?: 0,
                vocabularyScoreDelta = summary?.vocabDelta ?: 0,
                fluencyScoreDelta = summary?.fluencyDelta ?: 0,
                naturalnessScoreDelta = summary?.naturalnessDelta ?: 0,
                onClick = onNavigateToAnalytics,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(SpacingL))
    }
}

/**
 * 다이얼로그에 학습 언어 리스트에 사용되는 버튼
 */
@Composable
private fun LanguageButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
    {
        Text(
            text = text,
            fontSize = 16.sp,
            color = if (isSelected) ThemePrimary else TextPrimary
        )
    }
}

private val learningLanguageOptions = listOf(
    LangCode.KO to "한국어",
    LangCode.EN to "English",
    LangCode.JA to "日本語",
    LangCode.ES to "Español"
)