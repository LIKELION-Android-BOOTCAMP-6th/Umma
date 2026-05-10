package com.example.umma.presentation.home

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeScreen(
    onNavigateToAnalytics: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToFeedbackList: () -> Unit,
    onNavigateToStudyList: () -> Unit,
    onNavigateToMyPage: () -> Unit
) {
    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()

        if (currentTime - backPressedTime < 2000) {
            (context as? Activity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, "종료하려면 다시 누르세요.", Toast.LENGTH_SHORT).show()
        }
    }

    Column {
        // 학습으로 이동 버튼
        Button(
            onClick = onNavigateToStudyList
        ) {
            Text(
                text = "학습으로 이동"
            )
        }
        // 챗으로 이동 버튼
        Button(
            onClick = onNavigateToChat
        ) {
            Text(
                text = "챗으로 이동"
            )
        }
        // 통계로 이동 버튼
        Button(
            onClick = onNavigateToAnalytics
        ) {
            Text(
                text = "통계로 이동"
            )
        }
        // 교정으로 이동 버튼
        Button(
            onClick = onNavigateToFeedbackList
        ) {
            Text(
                text = "교정으로 이동"
            )
        }
        // 마이페이지로 이동 버튼
        Button(
            onClick = onNavigateToMyPage
        ) {
            Text(
                text = "마이페이지로 이동"
            )
        }
    }
}