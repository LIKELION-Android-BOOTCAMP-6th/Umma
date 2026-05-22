package com.example.umma.presentation.statistics

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
 * Statistics 화면의 최소 진입점 컴포저블입니다.
 *
 * STI-003 책임은 실제 지표/차트 구현이 아니라, 후속 User Flow가 사용할
 * public route/screen 이름과 패키지 baseline을 Statistics로 고정하는 데 있습니다.
 * 따라서 이 화면은 아직 플레이스홀더지만, 네비게이션 경계와 패키지 경로는
 * 후속 구현이 그대로 이어받을 수 있게 정리합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
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
                text = "통계 플레이스 홀더",
                textAlign = TextAlign.Center
            )
        }
    }
}
