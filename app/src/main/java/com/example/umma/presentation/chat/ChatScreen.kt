package com.example.umma.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.umma.core.theme.BackgroundHighlight
import com.example.umma.core.theme.TextAnalysisR
import com.example.umma.core.theme.TitleB
import com.example.umma.core.theme.TitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    Column {
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
