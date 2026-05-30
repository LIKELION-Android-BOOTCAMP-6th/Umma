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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
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
    val configuration = LocalConfiguration.current
    var hasRequestedMicPermission by rememberSaveable { mutableStateOf(false) }
    val topicListMaxHeight = when {
        configuration.screenHeightDp < 420 -> 160.dp
        configuration.screenHeightDp < 600 -> 220.dp
        else -> 360.dp
    }

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

    DisposableEffect(activity) {
        onDispose {
            // 화면 회전은 같은 ChatViewModel을 재사용하는 configuration change 이므로
            // 세션과 자막 상태를 유지한다. 실제 navigation 이탈처럼 Activity 재구성이 아닌
            // dispose 에서만 기존 Sprint2 정책대로 녹음/재생/Live transport 를 정리한다.
            if (activity?.isChangingConfigurations != true) {
                viewModel.stopChat()
            }
        }
    }

    val showMainChat = uiState.entryStage == ChatEntryStage.READY &&
        (uiState.sessionState == SessionState.READY ||
            uiState.sessionState == SessionState.RECONNECTING)

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
        // 관심주제 저장 정책은 정확히 5개 선택이므로 UI도 같은 기준으로 버튼/안내를 제어한다.
        val hasRequiredTopicCount = uiState.selectedTopic.size == REQUIRED_TOPIC_COUNT
        // 5개 미만일 때는 저장 시도 없이도 사용자가 부족한 조건을 알 수 있어야 한다.
        val topicGuideText = when {
            // 완료 버튼이 비활성화되어도 사용자가 왜 완료할 수 없는지 즉시 알 수 있어야 한다.
            !hasRequiredTopicCount -> "주제를 정확히 5개 선택해 주세요."
            // 5개를 채운 뒤에는 선택 안내를 숨기되, 저장 실패 같은 실제 오류는 그대로 보여준다.
            else -> uiState.topicError
        }

        UmmaDialog(
            title = "관심 주제 5개 선택",
            modifier = Modifier.padding(horizontal = SpacingL),
            // 필수 선택 다이얼로그라 취소 콜백은 호출되지 않도록 UI/dismiss 를 모두 막는다.
            onCancel = {},
            onConfirm = { viewModel.saveInterestTopics() },
            confirmText = "완료",
            // 닫기 아이콘을 숨겨 사용자가 필수 설정 단계를 시각적으로 우회할 수 없게 한다.
            showCancelButton = false,
            // 시스템 뒤로가기로 다이얼로그만 닫히는 경로를 차단한다.
            dismissOnBackPress = false,
            // 외부 터치로 다이얼로그만 닫히는 경로를 차단한다.
            dismissOnClickOutside = false,
            // 5개 선택 전 또는 저장 중에는 완료 버튼을 눌러 저장 요청을 만들 수 없다.
            confirmEnabled = hasRequiredTopicCount && !uiState.isTopicSaving
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 가로 화면에서는 버튼 영역을 남기고 목록만 스크롤되도록 높이를 제한한다.
                    .heightIn(max = topicListMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // 선택 목록은 ViewModel 의 selectedTopic 상태만 보고 렌더링해 회전 후 상태 복제를 피한다.
                Topic.entries.forEach { topic ->
                    // 현재 topic 이 선택 목록에 포함되어 있으면 버튼을 selected 스타일로 표시한다.
                    val isSelected = uiState.selectedTopic.contains(topic)
                    TopicButton(
                        text = topic.displayName,
                        isSelected = isSelected,
                        // 저장 요청 중에는 선택 변경을 막아 저장 입력과 화면 상태가 어긋나지 않게 한다.
                        enabled = !uiState.isTopicSaving,
                        onClick = { viewModel.toggleTopic(topic) }
                    )
                }
                // 5개 미만 안내 또는 저장 실패 오류를 같은 위치에 표시한다.
                topicGuideText?.let {
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
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

private const val REQUIRED_TOPIC_COUNT = 5
