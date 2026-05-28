package com.app.umma.presentation.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.app.umma.R
import com.app.umma.core.theme.BackgroundDeactivated
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextAnalysisR
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.ui.component.UmmaDialog
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.user.Topic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    var hasRequestedMicPermission by rememberSaveable { mutableStateOf(false) }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startUserTurn(hasRecordAudioPermission = true)
        } else {
            val permanentlyDenied = activity != null &&
                hasRequestedMicPermission &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                )
            viewModel.onMicPermissionDenied(permanently = permanentlyDenied)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkInterestTopics()
        viewModel.enterChat()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopChat()
        }
    }

    val showMainChat = uiState.entryStage == ChatEntryStage.READY &&
        uiState.sessionState == SessionState.READY

    if (!showMainChat) {
        ChatEntryGuardScreen(
            uiState = uiState,
            onRetry = { viewModel.enterChat() }
        )
        return
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "대화",
                isCenterTitle = true,
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleSubtitle() },
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.showSubtitle) {
                                    R.drawable.outline_closed_caption_24
                                } else {
                                    R.drawable.outline_closed_caption_disabled_24
                                }
                            ),
                            modifier = Modifier.size(50.dp),
                            contentDescription = if (uiState.showSubtitle) {
                                "자막 끄기"
                            } else {
                                "자막 보기"
                            },
                            tint = if (uiState.showSubtitle) ThemePrimary else BackgroundDeactivated
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = SpacingL)
        ) {
            ChatCenterVisual(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
            )

            if (uiState.showSubtitle) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.CenterStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(
                                    width = 1.5.dp,
                                    color = ThemePrimary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Umma Tutor",
                                style = TextAnalysisR.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 22.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(BackgroundSecondary)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = uiState.lastFinalAITranscript.ifBlank { "-" },
                                style = TextAnalysisR,
                                color = Color.Black,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .wrapContentSize(Alignment.CenterEnd)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 22.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ThemePrimary)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = uiState.lastFinalUserTranscript.ifBlank { "-" },
                                style = TextAnalysisR,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ThemePrimary)
                                .border(
                                    width = 2.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = uiState.userNickname.ifBlank { "User" },
                                style = TextAnalysisR.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            val micEnabled = uiState.canEndUserTurn || uiState.canStartUserTurn
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
                    .size(64.dp)
                    .border(width = 4.dp, color = Color.White, shape = CircleShape)
                    .shadow(8.dp, CircleShape)
                    .background(
                        color = if (micEnabled) ThemePrimary else BackgroundDeactivated,
                        shape = CircleShape
                    )
                    .clickable(enabled = micEnabled) {
                        when {
                            uiState.canEndUserTurn -> viewModel.endUserTurn()
                            hasRecordAudioPermission() -> {
                                viewModel.startUserTurn(hasRecordAudioPermission = true)
                            }
                            uiState.microphonePermissionPermanentlyDenied -> openAppSettings()
                            else -> {
                                hasRequestedMicPermission = true
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_mic_24),
                    contentDescription = if (uiState.isRecording) "Stop talking" else "Start talking",
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.microphonePermissionDenied) {
                    Text(
                        text = if (uiState.microphonePermissionPermanentlyDenied) {
                            "마이크 권한이 영구 거부되었습니다. 설정에서 권한을 확인해주세요."
                        } else {
                            "마이크 권한이 필요합니다."
                        },
                        color = TextLogout,
                        style = TextAnalysisR,
                        textAlign = TextAlign.Center
                    )
                }

                if (uiState.microphonePermissionPermanentlyDenied) {
                    Button(
                        onClick = { openAppSettings() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SpacingS)
                    ) {
                        Text(text = "Open Settings")
                    }
                }

                if (uiState.entryStage == ChatEntryStage.BLOCKED_NETWORK) {
                    Text(
                        text = uiState.errorMessage ?: "네트워크 연결이 필요합니다.",
                        color = TextLogout,
                        style = TextAnalysisR,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.enterChat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SpacingS)
                    ) {
                        Text(text = "재시도")
                    }
                }

                if (uiState.isRecoverableError) {
                    Text(
                        text = buildStatusText(uiState),
                        textAlign = TextAlign.Center,
                        style = TextAnalysisR,
                        modifier = Modifier.padding(top = SpacingS)
                    )
                    Button(
                        onClick = { viewModel.enterChat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SpacingS)
                    ) {
                        Text(text = "재연결 시도")
                    }
                }

                uiState.fallbackMessage?.let { message ->
                    Text(
                        text = message,
                        textAlign = TextAlign.Center,
                        style = TextAnalysisR,
                        modifier = Modifier.padding(top = SpacingS)
                    )
                }
            }
        }
    }

    if (uiState.showTopicDialog) {
        UmmaDialog(
            title = "관심 주제 5개 선택",
            modifier = Modifier.padding(horizontal = SpacingL),
            onCancel = {},
            onConfirm = { viewModel.saveInterestTopics() },
            confirmText = "완료"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
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
                    Text(
                        text = it,
                        color = TextLogout,
                        fontSize = TextAnalysisR.fontSize,
                        modifier = Modifier.padding(top = SpacingS)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatCenterVisual(
    uiState: ChatUiState,
    modifier: Modifier = Modifier
) {
    val inputSignal = uiState.inputLevel.coerceIn(0f, 1f)
    val outputSignal = uiState.outputLevel.coerceIn(0f, 1f)
    val innerHaloScale by animateFloatAsState(
        targetValue = if (inputSignal > 0.01f) 1f + (inputSignal * 0.16f) else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
        label = "chat-inner-halo-scale"
    )
    val innerHaloAlpha by animateFloatAsState(
        targetValue = if (inputSignal > 0.01f) (0.18f + inputSignal * 0.34f).coerceAtMost(0.56f) else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 220f),
        label = "chat-inner-halo-alpha"
    )
    val outerHaloScale by animateFloatAsState(
        targetValue = if (outputSignal > 0.01f) 1f + (outputSignal * 0.2f) else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 180f),
        label = "chat-outer-halo-scale"
    )
    val outerHaloAlpha by animateFloatAsState(
        targetValue = if (outputSignal > 0.01f) (0.10f + outputSignal * 0.26f).coerceAtMost(0.42f) else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 180f),
        label = "chat-outer-halo-alpha"
    )

    Box(
        modifier = modifier.size(320.dp),
        contentAlignment = Alignment.Center
    ) {
        if (outerHaloAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .scale(outerHaloScale)
                    .background(ThemePrimary.copy(alpha = outerHaloAlpha), CircleShape)
            )
        }

        if (innerHaloAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(256.dp)
                    .scale(innerHaloScale)
                    .background(ThemePrimary.copy(alpha = innerHaloAlpha), CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(192.dp)
                .border(width = 8.dp, color = Color.White, shape = CircleShape)
                .shadow(12.dp, CircleShape)
                .background(ThemePrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_record_voice_over_24),
                contentDescription = "Central visual",
                tint = Color.White,
                modifier = Modifier.size(86.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatEntryGuardScreen(
    uiState: ChatUiState,
    onRetry: () -> Unit
) {
    val canRetryFromGuard = uiState.entryStage == ChatEntryStage.BLOCKED_NETWORK ||
        uiState.entryStage == ChatEntryStage.ERROR ||
        uiState.isRecoverableError ||
        uiState.sessionState == SessionState.ERROR ||
        uiState.aiState == AIState.ERROR

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "대화",
                isCenterTitle = true
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundPrimary)
                .padding(horizontal = SpacingL),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpacingS)
            ) {
                CircularProgressIndicator(color = ThemePrimary)

                Text(
                    text = buildStatusText(uiState).ifBlank { "Preparing session..." },
                    textAlign = TextAlign.Center
                )

                if (canRetryFromGuard) {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(ChipCornerRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
                        modifier = Modifier
                            .padding(top = SpacingL)
                            .padding(horizontal = SpacingL, vertical = SpacingS)
                    ) {
                        Text(text = "다시 시도")
                    }
                }
            }
        }
    }
}

internal fun buildStatusText(uiState: ChatUiState): String {
    uiState.errorMessage?.let { return it }
    uiState.entryMessageOverride?.let { return it }

    return when {
        uiState.entryStage == ChatEntryStage.GUARDING -> "요구사항 확인중.."
        uiState.entryStage == ChatEntryStage.RESTORING -> "이전 대화 복구중.."
        uiState.entryStage == ChatEntryStage.STARTING_NEW -> "새로운 세션 시작중.."
        uiState.entryStage == ChatEntryStage.BLOCKED_NETWORK ->
            "네트워크에 연결할 수 없습니다.\n wifi 또는 모바일 데이터를 확인해주세요."
        uiState.sessionState == SessionState.LOADING -> "세션 준비중..."
        uiState.sessionState == SessionState.RECONNECTING ->
            "Reconnecting ${uiState.reconnectAttempt}/${uiState.maxReconnectAttempts}"
        uiState.aiState == AIState.RECONNECTING -> "Response was interrupted."
        uiState.aiState == AIState.THINKING -> "AI is thinking..."
        uiState.aiState == AIState.SPEAKING -> "AI is speaking..."
        uiState.isRecording -> "Listening..."
        uiState.sessionState == SessionState.READY -> "Ready."
        else -> ""
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

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
