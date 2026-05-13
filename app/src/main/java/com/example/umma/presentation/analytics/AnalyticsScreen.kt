package com.example.umma.presentation.analytics

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
 * 분석 화면을 구성하는 컴포저블입니다.
 *
 * 사용자의 학습 데이터를 시각화하여 학습 진척도와 성과를 분석한 정보를 제공합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "통계",
                isCenterTitle = true,
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(
                text = "통계 플레이스 홀더", textAlign = TextAlign.Center
            )
        }
    }
}