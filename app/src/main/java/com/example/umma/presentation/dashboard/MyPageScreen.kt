package com.example.umma.presentation.dashboard

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
 * 마이페이지 화면을 구성하는 컴포저블입니다.
 *
 * 사용자 프로필 정보 및 앱 설정을 관리할 수 있는 인터페이스를 제공합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen() {
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "마이페이지",
                isCenterTitle = true,
                onBackClick = { println() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(
                text = "마이페이지 플레이스 홀더", textAlign = TextAlign.Center
            )
        }
    }
}