package com.example.umma.presentation.dashboard

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.ui.component.UmmaAppBar

/**
 * 대시보드(홈) 화면.
 *
 * 본 파일은 **DASH-001/002/006 본 구현 전 임시 placeholder 상태**이다.
 * 시각 디자인은 의도적으로 비워 두고, 본격 화면은 다음 작업자(DASH-002~005)가 구현한다.
 *
 * 현재 파일이 담는 책임:
 *  - DASH-001: 진입 시 preload 트리거(현재는 ViewModel.onEnter() 호출로 hook 만 마련, 실제 데이터 흐름은 후속)
 *  - DASH-002: 카드 데이터 렌더링 자리(현재는 4 개 네비게이션 버튼으로 대체)
 *  - DASH-006: selectedLearningLanguage 변경 hook(현재는 임시 버튼으로 대체)
 *
 * 후속 작업자가 갈아끼우는 진입점:
 *  - 카드 4 개 영역(대화 / 학습 / 교정 / 통계) — 현재는 단순 Button
 *  - 학습 언어 selector — 현재는 임시 Button 으로 onChangeLearningLanguage 콜백 호출
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

    // DASH-001: 화면 진입 시 1회 preload 트리거.
    //   현재는 ViewModel.onEnter() 가 로그만 찍는 상태이며,
    //   실제 UserLangPref / DashSummary preload 호출은 후속 Phase 에서 채운다.
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    // 디버그용: state 변동 시 로그 출력.
    //   DASH-001/006 진행 중 데이터 흐름을 logcat 으로 추적하기 위한 임시 장치.
    LaunchedEffect(uiState) {
        Log.d("DashboardScreen", "uiState=$uiState")
    }

    Scaffold(
        topBar = {
            UmmaAppBar(title = "Umma", isCenterTitle = false)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(SpacingS)
                .fillMaxSize()
        ) {
            Text(text = "Dashboard placeholder (DASH-001 ~ DASH-006 본 구현 전)")

            Spacer(modifier = Modifier.height(SpacingS))

            // === DASH-002 자리: 카드 4 개 (현재는 navigate 버튼) ===
            Button(onClick = onNavigateToChat) { Text("대화") }
            Button(onClick = onNavigateToStudyList) { Text("학습") }
            Button(onClick = onNavigateToFeedbackList) { Text("교정") }
            Button(onClick = onNavigateToAnalytics) { Text("통계") }

            Spacer(modifier = Modifier.height(SpacingS))

            // === DASH-006 자리: 학습 언어 selector ===
            //   본 구현 전까지 임시 버튼으로 ViewModel hook 만 검증.
            //   selectedLearningLanguage 가 바뀌면 ViewModel 이 새 언어 기준으로 데이터 재 fetch 해야 함.
            Button(onClick = { viewModel.onChangeLearningLanguage("en") }) {
                Text("학습 언어 → EN")
            }
            Button(onClick = { viewModel.onChangeLearningLanguage("ja") }) {
                Text("학습 언어 → JA")
            }

            Spacer(modifier = Modifier.height(SpacingS))

            // === 마이페이지 ===
            Button(onClick = onNavigateToMyPage) { Text("마이페이지") }
        }
    }
}