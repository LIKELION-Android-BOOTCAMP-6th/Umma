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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.SpacingXS
import com.example.umma.core.theme.TextAnalysisR
import com.example.umma.core.theme.TextCorrect
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.TextLogout
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.TextWrong
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.core.ui.component.UmmaDialog
import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.presentation.auth.AuthViewModel
import com.example.umma.presentation.auth.InitialSetupDialogStep
import com.example.umma.presentation.dashboard.component.AnalyticsCard
import com.example.umma.presentation.dashboard.component.ConversationCard
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
    onNavigateToCorrection: () -> Unit,
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
    var selectedLearningLanguage by remember { mutableStateOf<LangCode?>(null) }
    // AppBar 우측 selector(알약 버튼) 클릭 시 열리는 학습 언어 선택 다이얼로그 표시 여부.
    var isLanguageDialogOpen by remember { mutableStateOf(false) }
    // 다이얼로그 안에서의 임시 선택 lang. 다이얼로그 진입 시 selectedLearningLanguage 로
    //   초기화, 사용자가 다른 항목을 누르면 갱신, "선택" 확인 시 실제 ViewModel 에 반영.
    var dialogSelectedLang by remember { mutableStateOf<LangCode?>(null) }
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
                    // DASH-006: 학습 언어 selector.
                    //   와이어프레임 정합: ThemePrimary 알약 버튼. 클릭 시 dropdown 이 아닌
                    //   학습 언어 선택 다이얼로그가 열린다 (다이얼로그 본체는 Scaffold 하단에
                    //   isLanguageDialogOpen 으로 토글). userPref 가 아직 준비되지 않은 시점에도
                    //   selector 자체가 사라지지 않도록 selectedLang 은 LangCode.KO 로 fallback.
                    val selected = uiState.selectedLearningLanguage ?: LangCode.KO
                    LearningLanguageSelector(
                        selectedLang = selected,
                        onClick = {
                            // 다이얼로그 진입 시 현재 selectedLang 으로 임시 선택을 초기화.
                            dialogSelectedLang = selected
                            isLanguageDialogOpen = true
                        },
                        isLoading = uiState.isLoading || uiState.isChangingLanguage
                    )
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
                else -> DashboardContent(
                    summary = uiState.summary,
                    onNavigateToAnalytics = onNavigateToAnalytics,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToCorrection = onNavigateToCorrection,
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
                            onValueChange = {
                                nicknameInput = it
                                authViewModel.updateNicknameErrorMessage(null)
                            },
                            placeholder = { Text("2~10자 입력") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        authState.nicknameError?.let {
                            Spacer(
                                modifier = Modifier.height(SpacingS)
                            )
                            Text(
                                text = it,
                                color = TextLogout,
                                fontSize = TextAnalysisR.fontSize,
                            )
                        }
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
                        selectedLearningLanguage?.let {
                            authViewModel.onLanguageSelectAndSave(
                                selectedLearningLanguage = it,
                            )
                        }
                    },
                    confirmText = "완료"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {

                        learningLanguageOptions.forEach { (code, label) ->
                            val isSelected = (selectedLearningLanguage == code)
                            LanguageButton(
                                text = label,
                                isSelected = isSelected,
                                onClick = { selectedLearningLanguage = code }
                            )
                        }
                        authState.learningLanguageError?.let {
                            Spacer(
                                modifier = Modifier.height(SpacingS)
                            )
                            Text(
                                text = it,
                                color = TextLogout,
                                fontSize = TextAnalysisR.fontSize,
                            )
                        }
                    }
                }
            }
        }

        // DASH-006: AppBar selector 알약 버튼 → 학습 언어 선택 다이얼로그.
        //   와이어프레임 정합:
        //     - title "학습 언어 선택"
        //     - 4 개 LangCode 항목 (한국어 라벨)
        //     - 학습 중인 언어(uiState.learningLanguages 포함) 는 우측에 체크 아이콘
        //     - 사용자가 항목 탭 → dialogSelectedLang 갱신 (테두리로 시각 강조)
        //     - "선택" 버튼 → 임시 선택 lang 을 ViewModel 에 반영
        //   학습 안 하던 언어 선택도 동일 흐름. Repo.changeSelectedLang 가 learningLangs 를
        //   자동 확장하므로 별도 confirm 단계 불필요.
        if (isLanguageDialogOpen) {
            // docs LS-003/DASH-006 정합: "학습 중인 언어" 의 체크 표시는 실제 학습 데이터가
            //   쌓인 언어만 대상으로 한다. userPref.learningLangs (selector 클릭만으로도
            //   자동 확장됨) 대신 activeLearningLanguages (DashSummary 가 isEffectivelyEmpty=
            //   false 인 언어 집합) 를 기준으로 함 → "선택만 한 언어" 와 "정말 학습 중인 언어"
            //   를 시각적으로 분리.
            val activeLangs = uiState.activeLearningLanguages
            UmmaDialog(
                title = "학습 언어 선택",
                modifier = Modifier.padding(horizontal = SpacingL),
                onCancel = { isLanguageDialogOpen = false },
                onConfirm = {
                    dialogSelectedLang?.let { lang ->
                        // 동일 lang 재선택은 ViewModel 단에서 no-op 처리되므로 그대로 호출.
                        viewModel.onChangeLearningLanguage(lang.code)
                    }
                    isLanguageDialogOpen = false
                },
                confirmText = "선택"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    dashboardLanguageOptions.forEach { (code, label) ->
                        DashboardLanguageButton(
                            text = label,
                            isSelected = dialogSelectedLang == code,
                            isLearning = code in activeLangs,
                            onClick = { dialogSelectedLang = code }
                        )
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
    onNavigateToCorrection: () -> Unit,
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
            onNavigateToCorrection = onNavigateToCorrection,
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
 *
 * Empty 정책 (별도 Empty 화면을 두지 않고 카드 단위로 표현):
 *  - 대화 카드: 항상 ThemePrimary 유지. 데이터 없으면 우측 상단에 점(isEmpty=true) 만 표시.
 *  - 학습/교정/통계 카드: 데이터 없으면 회색(TextWrong) + 안내 토스트 후 본 화면으로 이동.
 *    각 본 화면의 자체 Empty UI 가 후속 안내를 담당한다.
 */
@Composable
private fun DashboardCardGrid(
    summary: DashSummary?,
    onNavigateToChat: () -> Unit,
    onNavigateToStudyList: () -> Unit,
    onNavigateToCorrection: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val context = LocalContext.current

    // 각 카드별 데이터 유무 판정 — 카드 내부 칩/배지 hide 조건과 동일 기준.
    val isConversationEmpty = summary?.recentTopic == null && (summary?.recentMinutes ?: 0) == 0
    val isStudyEmpty = (summary?.dueFlashcards ?: 0) == 0 && (summary?.savedFlashcards ?: 0) == 0
    val isFeedbackEmpty = summary?.correctionAvailable != true
    val isAnalyticsEmpty = (summary?.grammarDelta ?: 0) == 0 &&
            (summary?.vocabDelta ?: 0) == 0 &&
            (summary?.fluencyDelta ?: 0) == 0 &&
            (summary?.naturalnessDelta ?: 0) == 0

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
                isEmpty = isConversationEmpty,
                onClick = onNavigateToChat,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StudyCard(
                dueFlashcards = summary?.dueFlashcards ?: 0,
                savedFlashcards = summary?.savedFlashcards ?: 0,
                accentColor = if (isStudyEmpty) TextWrong else null,
                onClick = {
                    if (isStudyEmpty) {
                        Toast.makeText(context, "저장된 카드 없음", Toast.LENGTH_SHORT).show()
                    }
                    onNavigateToStudyList()
                },
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
                accentColor = if (isFeedbackEmpty) TextWrong else null,
                onClick = {
                    if (isFeedbackEmpty) {
                        Toast.makeText(context, "교정 가능 데이터 없음", Toast.LENGTH_SHORT).show()
                    }
                    onNavigateToCorrection()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            AnalyticsCard(
                grammarScoreDelta = summary?.grammarDelta ?: 0,
                vocabularyScoreDelta = summary?.vocabDelta ?: 0,
                fluencyScoreDelta = summary?.fluencyDelta ?: 0,
                naturalnessScoreDelta = summary?.naturalnessDelta ?: 0,
                accentColor = if (isAnalyticsEmpty) TextWrong else null,
                onClick = {
                    if (isAnalyticsEmpty) {
                        Toast.makeText(context, "데이터 부족", Toast.LENGTH_SHORT).show()
                    }
                    onNavigateToAnalytics()
                },
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

/**
 * DASH-006 학습 언어 선택 다이얼로그의 항목 라벨.
 *
 * 와이어프레임 정합으로 한국어 라벨 (영어 / 한국어 / 일본어 / 스페인어) 사용.
 * 닉네임/언어 설정 다이얼로그의 [learningLanguageOptions] (native script) 는
 * 다른 컨텍스트(초기 설정) 이므로 별도 매핑으로 분리.
 */
private val dashboardLanguageOptions = listOf(
    LangCode.KO to "한국어",
    LangCode.EN to "영어",
    LangCode.JA to "일본어",
    LangCode.ES to "스페인어"
)

/**
 * DASH-006 다이얼로그용 언어 버튼.
 *
 * 와이어프레임 정합:
 *  - 흰 배경 + 알약(pill) 형태 + 중앙 텍스트
 *  - 임시 선택된 항목([isSelected]) 은 ThemePrimary 보더 + 텍스트 색으로 강조
 *  - 학습 중인 항목([isLearning]) 은 텍스트 우측에 TextCorrect 색 체크 아이콘
 *  - selectedLang 이면서 학습 중인 경우 두 표시(보더 + 체크) 가 함께 노출됨 — 의도된 동작
 */
@Composable
private fun DashboardLanguageButton(
    text: String,
    isSelected: Boolean,
    isLearning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                color = if (isSelected) ThemePrimary else TextPrimary
            )
            if (isLearning) {
                Spacer(modifier = Modifier.width(SpacingS))
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "학습 중",
                    tint = TextCorrect,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
