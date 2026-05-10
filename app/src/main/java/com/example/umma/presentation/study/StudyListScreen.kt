package com.example.umma.presentation.study

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign


@Composable
fun StudyListScreen(
    onNavigateToStudyDetail: () -> Unit
) {
    Column {
        Text(
            text = "학습 플레이스 홀더", textAlign = TextAlign.Center
        )
        // 학습 상세로 이동 버튼
        Button(
            onClick = onNavigateToStudyDetail
        ) {
            Text(
                text = "학습 상세로 이동"
            )
        }
    }
}