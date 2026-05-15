package com.example.umma.presentation.dashboard

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.presentation.dashboard.component.DashboardEmpty
import com.example.umma.presentation.dashboard.component.DashboardSkeleton

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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // DASH-001: 화면 진입 시 1 회 preload + sync 트리거.
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    // 디버그용: state 변동 시 로그.
    LaunchedEffect(uiState) {
        Log.d("DashboardScreen", "uiState=$uiState")
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
            UmmaAppBar(title = "Umma", isCenterTitle = false)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // DASH-001 : Loading / Empty / Content 분기.
            //   Error 분기는 Snackbar 로 갈음 — UI 는 cache 유지가 정책.
            when {
                uiState.isLoading -> {
                    DashboardSkeleton()
                }

                uiState.isEmpty -> {
                    DashboardEmpty(
                        onStartConversation = onNavigateToChat
                    )
                }

                else -> {
                    DashboardContent(
                        onNavigateToAnalytics = onNavigateToAnalytics,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToFeedbackList = onNavigateToFeedbackList,
                        onNavigateToStudyList = onNavigateToStudyList,
                        onNavigateToMyPage = onNavigateToMyPage,
                        onChangeLearningLanguage = viewModel::onChangeLearningLanguage
                    )
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

        // === DASH-006 자리: 학습 언어 selector ===
        Button(onClick = { onChangeLearningLanguage("en") }) {
            Text("학습 언어 → EN")
        }
        Button(onClick = { onChangeLearningLanguage("ja") }) {
            Text("학습 언어 → JA")
        }

        Spacer(modifier = Modifier.height(SpacingS))

        // === 마이페이지 ===
        Button(onClick = onNavigateToMyPage) { Text("마이페이지") }
    }
}