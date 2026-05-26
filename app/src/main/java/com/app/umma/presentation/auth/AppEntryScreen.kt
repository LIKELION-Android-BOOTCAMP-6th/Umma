package com.app.umma.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.app.umma.R
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.SpacingXXL
import com.app.umma.core.theme.ThemePrimary

@Composable
fun AppEntryScreen(
    onNavigateToOnBoarding: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        // 앱 진입 시 최초 1회 자동 로그인 세션 체크하기 위해 호출
        viewModel.checkSession()
    }


    LaunchedEffect(uiState.googleState, uiState.isSessionChecking) {
        // 조건 3개 (세션 확인 끝, 에러 없음, FAILED 아닐 때) 모두 만족 시 이동
        if (uiState.isSessionChecking) return@LaunchedEffect
        // 에러 있다면 이동 X
        if (uiState.sessionError != null) return@LaunchedEffect
        if (uiState.googleState == GoogleAuthState.FAILED) return@LaunchedEffect
        when (uiState.googleState) {
            GoogleAuthState.SUCCESS -> onNavigateToDashboard()
            GoogleAuthState.IDLE -> onNavigateToOnBoarding()
            else -> Unit
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 에러: 상태 메세지, 재시도 버튼
            uiState.sessionError != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.sessionError ?: "",
                    )
                    Spacer(modifier = Modifier.height(SpacingXXL))
                    Button(onClick = { viewModel.retryCheckSession() }) {
                        Text(text = "다시 시도")
                    }
                }
            }

            // 로그인 중 or 일반 상태 : Splash 글씨 or 로고 표시
            else -> {
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemePrimary
                )
            }
        }
    }
}