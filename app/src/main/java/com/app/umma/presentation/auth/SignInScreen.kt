package com.app.umma.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign

@Composable
fun SignInScreen(
    onNavigateToHome: () -> Unit
) {
    Column {
        Text(
            text = "로그인 플레이스 홀더", textAlign = TextAlign.Center,
        )
        // 홈 화면으로 이동 버튼
        Button(
            onClick = onNavigateToHome
        ) {
            Text(
                text = "홈 화면으로 이동"
            )
        }
    }
}
// git push 확인용 주석입니다