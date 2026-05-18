package com.example.umma.presentation.correction

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
 * 교정 화면을 구성하는 컴포저블입니다.
 *
 * 사용자의 학습 결과에 대한 교정 및 피드백 목록을 제공합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionScreen() {
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "교정",
                isCenterTitle = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(
                text = "교정 플레이스 홀더", textAlign = TextAlign.Center
            )
        }
    }
}
