package com.app.umma.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextAnalysisR
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.TitleB
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.ui.component.UmmaDialog
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.user.Topic

/**
 * 채팅 화면을 구성하는 컴포저블입니다.
 *
 * @param viewModel 채팅 비즈니스 로직을 처리하는 [ChatViewModel]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startUserTurn(hasRecordAudioPermission = true)
        } else {
            viewModel.startUserTurn(hasRecordAudioPermission = false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkInterestTopics()
        viewModel.startChat()
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
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
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = SpacingL)
        ) {
            Text(
                text = "대화 상태: ${uiState.sessionState.name}",
                textAlign = TextAlign.Center,
                style = TitleB
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = buildStatusText(uiState),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(SpacingL))
            Button(
                onClick = { viewModel.toggleSubtitle() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (uiState.showSubtitle) "자막 숨기기" else "자막 보기",
                    fontSize = 16.sp
                )
            }
            if (uiState.showSubtitle) {
                Text(
                    text = "나: ${uiState.lastFinalUserTranscript.ifBlank { "-" }}",
                    style = TextAnalysisR
                )
                Spacer(modifier = Modifier.height(SpacingS))
                Text(
                    text = "AI: ${uiState.lastFinalAITranscript.ifBlank { "-" }}",
                    style = TextAnalysisR
                )
            }
            Spacer(modifier = Modifier.height(SpacingL))
            Button(
                onClick = {
                    when {
                        uiState.canEndUserTurn -> viewModel.endUserTurn()
                        hasRecordAudioPermission() -> viewModel.startUserTurn(hasRecordAudioPermission = true)
                        else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = uiState.canEndUserTurn || uiState.canStartUserTurn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (uiState.isRecording) "내 말하기 끝내기" else "내 말하기 시작",
                    fontSize = 16.sp
                )
            }
            if (uiState.isRecoverableError) {
                Spacer(modifier = Modifier.height(SpacingS))
                Button(
                    onClick = { viewModel.retryConnection() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "다시 연결")
                }
            }
            if (uiState.microphonePermissionDenied) {
                Spacer(modifier = Modifier.height(SpacingS))
                Text(
                    text = "마이크 권한이 필요합니다.",
                    color = TextLogout,
                    style = TextAnalysisR
                )
            }
            // fallbackMessage != null이면
            uiState.fallbackMessage?.let { message ->
                Spacer(modifier = Modifier.height(SpacingS))
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    style = TextAnalysisR
                )
            }
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

/**
 * 채팅 화면 상단 상태 문구를 생성합니다.
 *
 * @param uiState 현재 채팅 UI 상태
 * @return 사용자에게 표시할 상태 문구
 */
internal fun buildStatusText(uiState: ChatUiState): String {
    uiState.errorMessage?.let { return it }

    return when {
        uiState.sessionState == SessionState.LOADING ->
            "세션 준비 중입니다."

        uiState.sessionState == SessionState.RECONNECTING ->
            "재연결 중 ${uiState.reconnectAttempt}/${uiState.maxReconnectAttempts}"

        uiState.aiState == AIState.RECONNECTING ->
            "응답이 중단되었습니다."

        uiState.aiState == AIState.THINKING ->
            "AI가 응답을 준비 중입니다."

        uiState.aiState == AIState.SPEAKING ->
            "AI가 응답 중입니다."

        uiState.isRecording ->
            "듣고 있습니다."

        uiState.sessionState == SessionState.READY ->
            "준비되었습니다."

        else ->
            ""
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
