package com.example.umma.presentation.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.umma.core.ui.component.UmmaAppBar

/**
 * 온보딩 화면을 구성하는 컴포저블입니다.
 *
 * 앱의 주요 기능을 소개하며 로그인 화면으로의 전환을 유도합니다.
 *
 * @param onNavigateToSignIn 로그인 화면으로 이동하는 콜백 함수.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnBoardingScreen(
    onNavigateToSignIn: () -> Unit
) {
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "Umma",
                isCenterTitle = false
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
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
}