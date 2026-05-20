package com.example.umma.presentation.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.TextAnalysisR
import com.example.umma.core.theme.TextLogout
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.theme.TitleB
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.core.ui.component.UmmaDialog
import com.example.umma.domain.model.user.Topic

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
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkInterestTopics()
    }



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
    if (uiState.showTopicDialog) {
        UmmaDialog(
            title = "관심 주제 선택 5개",
            modifier = Modifier.padding(horizontal = SpacingL),
            onCancel = {},
            onConfirm = { viewModel.saveInterestTopics() },
            confirmText = "완료"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Topic.entries.forEach { topic ->
                    val isSelected = uiState.selectedTopic.contains(topic)
                    TopicButton(
                        text = topic.displayName,
                        isSelected = isSelected,
                        onClick = { viewModel.toggleTopic(topic) }
                    )
                }
                uiState.topicError?.let {
                    Spacer(modifier = Modifier.height(SpacingS))
                    Text(
                        text = it,
                        color = TextLogout,
                        fontSize = TextAnalysisR.fontSize
                    )
                }
            }
        }
    }
}


@Composable
private fun TopicButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = if (isSelected) ThemePrimary else TextPrimary
        )
    }
}
