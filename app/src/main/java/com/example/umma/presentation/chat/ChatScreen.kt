package com.example.umma.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.umma.core.theme.TitleB
import com.example.umma.core.ui.component.UmmaAppBar

/**
 * 챗(대화) 화면을 구성하는 컴포저블입니다.
 *
 * AI 또는 다른 사용자와의 대화 인터페이스를 제공합니다.
 *
 * @param viewModel 채팅 관련 비즈니스 로직을 처리하는 [ChatViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "대화",
                isCenterTitle = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(
                text = "챗 Pretendard",
                textAlign = TextAlign.Center,
                style = TitleB
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "그냥",
                textAlign = TextAlign.Center,
            )
        }
    }
}
