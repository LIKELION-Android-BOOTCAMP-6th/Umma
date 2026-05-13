package com.example.umma.presentation.dashboard

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.umma.core.ui.component.UmmaAppBar

/**
 * 대시보드 화면을 구성하는 컴포저블입니다.
 *
 * 사용자의 학습 현황 요약을 보여주며 각 주요 기능으로 이동할 수 있는 메뉴를 제공합니다.
 *
 * @param onNavigateToAnalytics 통계 화면으로 이동하는 콜백 함수.
 * @param onNavigateToChat 챗 화면으로 이동하는 콜백 함수.
 * @param onNavigateToFeedbackList 피드백 화면으로 이동하는 콜백 함수.
 * @param onNavigateToStudyList 학습 리스트 화면으로 이동하는 콜백 함수.
 * @param onNavigateToMyPage 마이페이지 화면으로 이동하는 콜백 함수.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
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
}