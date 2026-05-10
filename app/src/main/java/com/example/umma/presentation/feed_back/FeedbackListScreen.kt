package com.example.umma.presentation.feed_back

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
fun FeedbackListScreen(
    onNavigateToFeedbackDetail: () -> Unit
) {
    Column {
        Text(
            text = "교정 플레이스 홀더", textAlign = TextAlign.Center
        )
        // 교정 디테일로 이동 버튼
        Button(
            onClick = onNavigateToFeedbackDetail
        ) {
            Text(
                text = "교정 디테일로 이동"
            )
        }
    }
}