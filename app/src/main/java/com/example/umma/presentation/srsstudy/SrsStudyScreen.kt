package com.example.umma.presentation.srsstudy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.umma.core.ui.component.UmmaAppBar

/** SRS 반복학습 화면입니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsStudyScreen() {
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
            // 지금은 라우트/테마/앱바 연결만 확인하고, 카드 UI 는 SRS-002 이후 단계에서 붙인다.
            // 이 화면은 흐름 검증용 진입점이라 실제 deck 렌더링 전까지는 가벼운 플레이스홀더만 둔다.
            Text(
                text = "학습 플레이스 홀더",
                textAlign = TextAlign.Center
            )
        }
    }
}
