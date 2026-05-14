package com.example.umma.presentation.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.umma.core.ui.component.UmmaAppBar

/**
 * 학습 리스트 화면을 구성하는 컴포저블입니다.
 *
 * 사용자가 참여 중이거나 참여 가능한 학습 목록을 보여줍니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyListScreen() {
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "학습",
                isCenterTitle = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(
                text = "학습 플레이스 홀더", textAlign = TextAlign.Center
            )
        }
    }
}