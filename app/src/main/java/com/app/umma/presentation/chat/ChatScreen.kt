package com.app.umma.presentation.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.app.umma.R
import com.app.umma.core.theme.BackgroundDeactivated
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.SpacingXL
import com.app.umma.core.theme.TextAnalysisR
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.ui.component.UmmaDialog
import com.app.umma.domain.model.chat.AiContentReportReasonCategory
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.user.Topic
import com.app.umma.presentation.chat.component.AiContentReportDialog
import com.app.umma.presentation.chat.component.ChatReportTopActions
import com.app.umma.presentation.chat.component.PromptReviewReportDialog
import com.app.umma.presentation.chat.component.VoiceInteractionCharacter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val localView = LocalView.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    var hasRequestedMicPermission by rememberSaveable { mutableStateOf(false) }
    // Chat 라우트가 숨겨질 때 ON_STOP 과 onDispose 가 연달아 들어와도 stop 작업이 중복 실행되지 않게 한다.
    val routeHiddenHandled = remember { mutableStateOf(false) }
    // 신고 버튼은 실제 Firestore report index를 만들기 때문에, 실수 클릭 방지를 위해 확인 다이얼로그를 거친다.
    val promptReviewReportConfirmDialogState = rememberSaveable { mutableStateOf(false) }
    // 신고 메모는 개발용 리뷰 자료에만 저장되며, 실제 대화/자막/SessionMemory 상태와 분리한다.
    val promptReviewReportNoteState = rememberSaveable { mutableStateOf("") }
    // 운영용 AI 콘텐츠 신고는 Google Play 대응 기능이므로 dev prompt review와 별도 다이얼로그로 관리한다.
    val aiContentReportDialogState = rememberSaveable { mutableStateOf(false) }
    // 신고 사유는 필수값이다. null이면 확인 버튼을 비활성화해 불완전한 운영 신고 문서를 막는다.
    val selectedAiContentReportReasonState = rememberSaveable {
        mutableStateOf<AiContentReportReasonCategory?>(null)
    }
    // 상세 메모는 선택값이다. 사용자가 민감정보를 더 쓰지 않아도 신고 사유만으로 접수 가능해야 한다.
    val aiContentReportNoteState = rememberSaveable { mutableStateOf("") }
    // screenHeightDp 대신 실제 Compose window container 높이를 사용한다.
    // 이렇게 해야 회전, multi-window, split-screen 에서 다이얼로그/자막 높이 계산이 실제 화면과 맞는다.
    val containerHeightDp = with(density) {
        windowInfo.containerSize.height.toDp().value.toInt()
    }
    val topicListMaxHeight = when {
        containerHeightDp < 420 -> 160.dp
        containerHeightDp < 600 -> 220.dp
        else -> 360.dp
    }
    val subtitleMaxHeight = calculateSubtitleMaxHeight(containerHeightDp)

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

    fun dismissKeyboard() {
        // 일부 MIUI/구형 IME는 Compose keyboardController.hide()만으로 키보드를 내리지 않는다.
        // focus 강제 해제, Compose controller, Android InputMethodManager를 모두 호출해 제조사 IME 차이를 줄인다.
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = activity?.currentFocus?.windowToken ?: localView.windowToken
        inputMethodManager?.hideSoftInputFromWindow(token, 0)
        // 키보드가 실제로 내려가지 않는 기기에서도 포커스가 다시 TextField로 돌아가지 않게 View focus도 정리한다.
        activity?.currentFocus?.clearFocus()
        localView.clearFocus()
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
        viewModel.setChatRouteVisible(true)
        viewModel.enterChat()
    }

    DisposableEffect(activity, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (activity?.isChangingConfigurations == true) return@LifecycleEventObserver

            when (event) {
                Lifecycle.Event.ON_START -> {
                    // hidden 상태에서 돌아올 때는 저장된 UI를 재사용하지 말고 새 세션 경계를 다시 연다.
                    routeHiddenHandled.value = false
                    viewModel.setChatRouteVisible(true)
                    // 홈/탭 전환 후 재진입 시에도 관심주제 필수 다이얼로그를 다시 확인한다.
                    // 이 검사가 없으면 세션 복구(enterChat) 경로로 주제 선택을 우회할 수 있다.
                    viewModel.checkInterestTopics()
                    viewModel.enterChat()
                }

                Lifecycle.Event.ON_STOP -> {
                    // 탭 전환이나 앱 백그라운드 진입 시점에 transport 를 먼저 닫아 stale session 재사용을 막는다.
                    viewModel.setChatRouteVisible(false)
                    if (!routeHiddenHandled.value) {
                        routeHiddenHandled.value = true
                        viewModel.onChatRouteHidden()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 일부 navigation 경로에서는 onStop 전에 onDispose 가 먼저 들어올 수 있다.
            // routeHiddenHandled 로 한 번만 정리되도록 보장하고, 여기서는 누락 방지용 마지막 정리만 남긴다.
            if (activity?.isChangingConfigurations != true && !routeHiddenHandled.value) {
                viewModel.setChatRouteVisible(false)
                routeHiddenHandled.value = true
                viewModel.onChatRouteHidden()
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
                leadingActions = {
                    ChatReportTopActions(
                        canReportAiContent = uiState.canReportAiContent,
                        isAiContentReporting = uiState.isAiContentReporting,
                        hasReportedCurrentAiContent = uiState.hasReportedCurrentAiContent,
                        showPromptReviewReportButton = uiState.showPromptReviewReportButton,
                        isPromptReviewReporting = uiState.isPromptReviewReporting,
                        hasPromptReviewReported = uiState.hasPromptReviewReported,
                        onAiContentReportClick = {
                            // 신고 draft는 다이얼로그를 열 때 초기화해 이전 입력이 다음 신고에 남지 않게 한다.
                            selectedAiContentReportReasonState.value = null
                            aiContentReportNoteState.value = ""
                            aiContentReportDialogState.value = true
                        },
                        onPromptReviewReportClick = {
                            promptReviewReportConfirmDialogState.value = true
                        }
                    )
                },
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
            if (uiState.watchAttached) {
                WatchAttachedBanner(
                    onSwitchToPhoneClick = viewModel::switchWatchToPhone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                )
            }

            VoiceInteractionCharacter(
                inputLevel = uiState.inputLevel,
                outputLevel = uiState.outputLevel,
                isRecording = uiState.isRecording,
                isAwaitingUserTranscript = uiState.isAwaitingUserTranscript,
                aiState = uiState.aiState,
                isAudioOutputPlaying = uiState.isAudioOutputPlaying,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (uiState.watchAttached) 112.dp else 28.dp)
            )

            if (uiState.showSubtitle) {
                ChatSubtitleConversation(
                    items = uiState.subtitleItems,
                    userName = uiState.userNickname.ifBlank { "User" },
                    maxHeight = subtitleMaxHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = SubtitleBottomPadding)
                )
            }

            // 마이크 버튼은 ChatScreen 에서 session/AI 상태를 다시 조합하지 않고,
            // ViewModel 이 만든 UiState 파생값만 읽는다. 그래야 회전/이벤트 지연 상황에서도
            // 화면과 입력 방어 기준이 같은 상태값을 바라본다.
            val micControlState = uiState.micControlState
            // DISABLED 상태에서는 버튼 모양은 남기되 클릭만 막아, 다음 행동이 마이크 입력임을 유지한다.
            val micEnabled = micControlState != ChatMicControlState.DISABLED
            // 녹음 중 STOP 상태는 위험/정지 의미가 분명해야 하므로 기존 로그아웃 계열 강조색을 재사용한다.
            val micBackgroundColor = when (micControlState) {
                ChatMicControlState.START -> ThemePrimary
                ChatMicControlState.STOP -> TextLogout
                ChatMicControlState.DISABLED -> BackgroundDeactivated
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
                    .size(64.dp)
                    .border(width = 4.dp, color = Color.White, shape = CircleShape)
                    .shadow(8.dp, CircleShape)
                    .background(
                        color = micBackgroundColor,
                        shape = CircleShape
                    )
                    .clickable(enabled = micEnabled) {
                        // 클릭 처리도 UiState 의 canStart/canEnd 정책을 다시 사용한다.
                        // icon state 와 실제 동작 조건이 어긋나면 사용자가 같은 버튼을 눌렀는데
                        // 다른 결과를 경험할 수 있기 때문이다.
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
                    // 버튼의 실제 동작은 UiState 의 파생 상태가 결정한다.
                    // 녹음 중에는 명확한 정지 아이콘을 보여 사용자가 두 번째 클릭의 의미를 알 수 있게 한다.
                    imageVector = when (micControlState) {
                        ChatMicControlState.STOP -> Icons.Filled.Stop
                        ChatMicControlState.START,
                        ChatMicControlState.DISABLED -> Icons.Filled.Mic
                    },
                    contentDescription = when (micControlState) {
                        ChatMicControlState.STOP -> "Stop talking"
                        ChatMicControlState.START -> "Start talking"
                        ChatMicControlState.DISABLED -> "Voice input unavailable"
                    },
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 하단 안내는 bottom edge에 붙으면 시스템 제스처/내비게이션과 시각적으로 충돌한다.
                    // 마이크 버튼 아래에 남기되, 화면 바닥에서는 충분히 띄워 현재 상태 안내로 읽히게 한다.
                    .padding(bottom = 28.dp),
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

                uiState.micStatusMessage?.let { message ->
                    // 이 문구는 저장되는 subtitle 이 아니라 현재 마이크/AI 처리 상태를 설명하는
                    // 화면 전용 보조 정보다. 회전 후에도 ViewModel 상태가 유지되면 같은 문구가 다시 그려진다.
                    // 중앙 캐릭터 내부의 중복 문구를 제거했으므로, 이 하단 문구가 유일한 행동 안내 source다.
                    Text(
                        text = message,
                        color = TextPrimary,
                        style = TextAnalysisR.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = SpacingS)
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

                uiState.aiContentReportErrorMessage?.let { message ->
                    // 운영 신고 실패는 대화 실패가 아니므로 하단 상태에만 짧게 보여주고,
                    // sessionState/errorMessage를 바꾸지 않는다.
                    Text(
                        text = message,
                        textAlign = TextAlign.Center,
                        color = TextLogout,
                        style = TextAnalysisR,
                        modifier = Modifier.padding(top = SpacingS)
                    )
                }
            }
        }
    }

    if (aiContentReportDialogState.value) {
        AiContentReportDialog(
            selectedReasonState = selectedAiContentReportReasonState,
            noteState = aiContentReportNoteState,
            isReporting = uiState.isAiContentReporting,
            onDismissKeyboard = ::dismissKeyboard,
            onCancel = {
                aiContentReportDialogState.value = false
            },
            onConfirm = { reason, note ->
                // 확인 버튼에서만 실제 운영 신고를 저장한다.
                // 다이얼로그 열기/닫기는 Firestore write를 만들지 않는다.
                viewModel.reportLatestAiContent(
                    reasonCategory = reason,
                    detailNote = note
                )
                aiContentReportDialogState.value = false
            }
        )
    }

    if (promptReviewReportConfirmDialogState.value) {
        PromptReviewReportDialog(
            reportNoteState = promptReviewReportNoteState,
            onDismissKeyboard = ::dismissKeyboard,
            onCancel = { promptReviewReportConfirmDialogState.value = false },
            onConfirm = {
                // 확인 이후에만 실제 신고를 실행한다. 버튼 클릭 자체는 Firestore write를 만들지 않는다.
                promptReviewReportConfirmDialogState.value = false
                viewModel.reportPromptReviewSession(reportNote = promptReviewReportNoteState.value)
            }
        )
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
                    .padding(horizontal = SpacingXL)
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
private fun WatchAttachedBanner(
    onSwitchToPhoneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundSecondary,
                        BackgroundPrimary
                    )
                )
            )
            .border(
                border = BorderStroke(1.dp, ThemePrimary.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "워치에서 대화 중입니다",
                color = TextPrimary,
                style = TextAnalysisR.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
            Text(
                text = "음성 입력과 음성 응답이 워치로 전환되었습니다",
                color = TextPrimary,
                style = TextAnalysisR,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Button(
                onClick = onSwitchToPhoneClick,
                modifier = Modifier.padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemePrimary,
                    contentColor = Color.White
                )
            ) {
                Text(text = "휴대폰으로 전환하기")
            }
        }
    }
}

@Composable
private fun ChatSubtitleConversation(
    items: List<ChatSubtitleItem>,
    userName: String,
    maxHeight: Dp,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val hasScrollableSubtitle = scrollState.maxValue > 0

    // 새 final 자막이 들어오거나 긴 텍스트로 스크롤 범위가 생기면 최신 말풍선 쪽을 우선 보여준다.
    LaunchedEffect(items, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = modifier
            // 긴 USER/AI final 자막이 중앙 음성 visual 영역까지 밀고 올라가지 않도록,
            // 넘치는 텍스트는 제한된 자막 영역 안에서만 스크롤하게 한다.
            .heightIn(max = maxHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(SpacingS)
        ) {
            items.forEach { item ->
                // 이번 화면은 전체 대화 로그가 아니라 최신 USER/AI final 자막만 보여준다.
                // 다만 각 말풍선의 텍스트는 생략하지 않고, 길면 자막 영역 안에서 스크롤로 확인한다.
                ChatSubtitleBubble(
                    item = item,
                    userName = userName
                )
            }
        }

        if (hasScrollableSubtitle) {
            ChatSubtitleScrollHint(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ChatSubtitleScrollHint(
    modifier: Modifier = Modifier
) {
    // 실제 blur 대신 상단 fade 를 올려 긴 자막이 위쪽으로 더 이어진다는 신호를 준다.
    // blur 효과보다 안정적이고 비용이 낮아 Chat 화면의 음성 애니메이션과 충돌이 적다.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SubtitleScrollHintHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundPrimary.copy(alpha = 0.96f),
                        BackgroundPrimary.copy(alpha = 0.72f),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Scroll subtitles up",
            tint = TextPrimary.copy(alpha = 0.42f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
    }
}

@Composable
private fun ChatSubtitleBubble(
    item: ChatSubtitleItem,
    userName: String,
    modifier: Modifier = Modifier
) {
    val isUser = item.role == TurnSpeaker.USER
    val bubbleAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val label = if (isUser) userName else "Umma"
    val bubbleColor = if (isUser) ThemePrimary else BackgroundSecondary
    val textColor = if (isUser) Color.White else Color.Black
    val labelBackground = if (isUser) ThemePrimary else Color.White
    val labelTextColor = if (isUser) Color.White else Color.Black

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(bubbleAlignment)
    ) {
        Column(
            // 사용자/AI 말풍선은 저장 순서 그대로 그리되 정렬과 색상으로 역할을 구분한다.
            // 화면 표시용 UI라 SessionMemory 저장 순서나 correction 신호에는 영향을 주지 않는다.
            modifier = Modifier.fillMaxWidth(0.84f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(labelBackground)
                    .border(
                        width = if (isUser) 1.5.dp else 1.dp,
                        color = if (isUser) Color.White else ThemePrimary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = label,
                    style = TextAnalysisR.copy(fontWeight = FontWeight.Bold),
                    color = labelTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = item.text,
                    style = TextAnalysisR,
                    color = textColor
                )
            }
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
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

private fun calculateSubtitleMaxHeight(screenHeightDp: Int): Dp {
    // 자막은 말줄임 없이 보여주되 중앙 음성 visual을 덮으면 안 되므로,
    // 화면 높이에 따라 스크롤 영역의 최대 높이만 조절한다.
    return (screenHeightDp - SubtitleReservedVerticalSpaceDp)
        .coerceIn(
            minimumValue = SubtitleMinHeightDp,
            maximumValue = SubtitleMaxHeightDp
        )
        .dp
}

private const val REQUIRED_TOPIC_COUNT = 5
private const val SubtitleReservedVerticalSpaceDp = 500
private const val SubtitleMinHeightDp = 120
private const val SubtitleMaxHeightDp = 220
private val SubtitleBottomPadding = 140.dp
private val SubtitleScrollHintHeight = 34.dp
