package com.example.umma.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
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
import com.example.umma.R
import com.example.umma.core.theme.BackgroundPrimary
import com.example.umma.core.theme.ThemePrimary

/**
 * 임시로 텍스트 넣어둔 상태
 */
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

    LaunchedEffect(uiState.googleState) {
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
        Text(
            text = stringResource(id = R.string.app_name),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            color = ThemePrimary
        )
    }
}