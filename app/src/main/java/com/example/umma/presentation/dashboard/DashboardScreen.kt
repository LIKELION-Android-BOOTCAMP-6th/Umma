package com.example.umma.presentation.dashboard

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.core.ui.component.UmmaDialog
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.presentation.auth.AuthViewModel
import com.example.umma.presentation.auth.InitialSetupDialogStep
import com.example.umma.presentation.dashboard.component.DashboardEmpty
import com.example.umma.presentation.dashboard.component.DashboardError
import com.example.umma.presentation.dashboard.component.DashboardSkeleton
import com.example.umma.presentation.dashboard.component.LearningLanguageSelector

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
                    IconButton(onClick = onNavigateToMyPage) {
                        Icon(
                            imageVector = Icons.Default.Person,
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
                    onNavigateToAnalytics = onNavigateToAnalytics,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToFeedbackList = onNavigateToFeedbackList,
                    onNavigateToStudyList = onNavigateToStudyList,
                    onNavigateToMyPage = onNavigateToMyPage,
                    onChangeLearningLanguage = viewModel::onChangeLearningLanguage
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
 * 현재는 placeholder(navigate 버튼 + 임시 selector) 그대로 유지한다.
 * 후속:
 *  - DASH-002: 카드 4 개 컴포저블로 교체
 *  - DASH-006: 학습 언어 selector 컴포저블로 교체
 */
@Composable
private fun DashboardContent(
    onNavigateToAnalytics: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToFeedbackList: () -> Unit,
    onNavigateToStudyList: () -> Unit,
    onNavigateToMyPage: () -> Unit,
    onChangeLearningLanguage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(SpacingS)
            .fillMaxSize()
    ) {
        Text(text = "Dashboard placeholder (DASH-002 ~ DASH-006 본 구현 전)")

        Spacer(modifier = Modifier.height(SpacingS))

        // === DASH-002 자리: 카드 4 개 (현재는 navigate 버튼) ===
        Button(onClick = onNavigateToChat) { Text("대화") }
        Button(onClick = onNavigateToStudyList) { Text("학습") }
        Button(onClick = onNavigateToFeedbackList) { Text("교정") }
        Button(onClick = onNavigateToAnalytics) { Text("통계") }

        Spacer(modifier = Modifier.height(SpacingS))

        // === 마이페이지 ===
        Button(onClick = onNavigateToMyPage) { Text("마이페이지") }
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