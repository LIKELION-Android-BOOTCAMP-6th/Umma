package com.example.umma.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun OnBoardingScreen(
    onNavigateToSignIn: (Unit) -> Unit
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = "온보딩 플레이스 홀더",
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    onNavigateToSignIn
                }
            ) {
                Text(
                    text = "로그인 화면으로 이동"
                )
            }
        }
    }
}