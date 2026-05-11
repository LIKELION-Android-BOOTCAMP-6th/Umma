package com.example.umma.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat.getString
import com.example.umma.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption

@Composable
fun SignInScreen(
    onNavigateToHome: () -> Unit
) {
    Column {
        Text(
            text = "로그인 플레이스 홀더", textAlign = TextAlign.Center
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