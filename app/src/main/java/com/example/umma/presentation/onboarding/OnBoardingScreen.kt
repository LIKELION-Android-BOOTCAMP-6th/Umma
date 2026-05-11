package com.example.umma.presentation.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign

@Composable
fun OnBoardingScreen(
    onNavigateToSignIn: () -> Unit
) {
    Column {
        Text(
            text = "온보딩 플레이스 홀더", textAlign = TextAlign.Center
        )
        // 로그인으로 이동 버튼
        Button(
            onClick = onNavigateToSignIn
        ) {
            Text(
                text = "로그인 화면으로 이동"
            )
        }
    }
}