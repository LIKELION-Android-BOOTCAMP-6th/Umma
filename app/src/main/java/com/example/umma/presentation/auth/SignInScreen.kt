package com.example.umma.presentation.auth

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
fun SignInScreen(
    onNavigateToHome: (Unit) -> Unit
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = "로그인 플레이스 홀더",
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    onNavigateToHome
                }
            ) { 
                Text(
                    text = "홈 화면으로 이동"
                )
            }
        }
    }
}