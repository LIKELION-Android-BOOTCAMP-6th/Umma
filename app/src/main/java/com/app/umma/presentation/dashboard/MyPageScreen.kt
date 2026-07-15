package com.app.umma.presentation.dashboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.app.umma.BuildConfig
import com.app.umma.R
import com.app.umma.core.theme.BackgroundClockDial
import com.app.umma.core.theme.BackgroundHighlight
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.CardElevation
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.ui.component.UmmaDialog
import com.app.umma.core.util.DeleteAccountAuthorizationResult
import com.app.umma.core.util.GoogleAuthorizationHelper
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.presentation.auth.AuthViewModel
import kotlinx.coroutines.launch
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 마이페이지 화면을 구성한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    onNavigateToOnBoarding: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    myPageViewModel: MyPageViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showNativeLanguageDialog by remember { mutableStateOf(false) }

    val authUiState by authViewModel.uiState.collectAsState()
    val notificationUiState by myPageViewModel.uiState.collectAsState()
    val profileUiState by myPageViewModel.profileState.collectAsState()
    // 로그아웃/탈퇴(auth)와 주언어 저장(profile) 중 하나라도 진행 중이면 화면을 차단한다.
    val isProcessing = authUiState.isLoading || profileUiState.isSaving
    var selectedNativeLanguage by remember(profileUiState.primaryLang) {
        mutableStateOf(profileUiState.primaryLang)
    }
    val context = LocalContext.current
    val googleAuthorizationHelper = remember(context) { GoogleAuthorizationHelper(context) }
    val coroutineScope = rememberCoroutineScope()
    val timezone = remember { TimeZone.getDefault().id }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            myPageViewModel.onNotificationPermissionGranted(timezone = timezone)
        } else {
            myPageViewModel.onNotificationPermissionDenied(timezone = timezone)
        }
    }

    val deleteAccountReauthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            authViewModel.cancelDeleteAccountReauthentication(
                message = "Google 계정 재확인이 취소되었습니다."
            )
            return@rememberLauncherForActivityResult
        }

        runCatching {
            val currentEmail = authViewModel.getCurrentUserEmail()
                ?: error("Current Google account email is missing.")
            googleAuthorizationHelper.consumeAuthorizationResolutionResult(
                data = result.data,
                email = currentEmail,
            )
        }.onSuccess { account ->
            authViewModel.beginDeleteAccountReauthentication()
            coroutineScope.launch {
                runCatching {
                    googleAuthorizationHelper.revokeDeleteAccountAccess(account)
                }.onSuccess {
                    authViewModel.deleteAccount()
                }.onFailure {
                    authViewModel.cancelDeleteAccountReauthentication(
                        message = "Google 권한 해제에 실패했습니다. 다시 시도해주세요."
                    )
                }
            }
        }.onFailure {
            authViewModel.cancelDeleteAccountReauthentication(
                message = "Google 계정 재확인에 실패했습니다. 다시 시도해주세요."
            )
        }
    }

    LaunchedEffect(Unit) {
        myPageViewModel.onScreenStarted(
            permissionGranted = hasNotificationPermission(),
            timezone = timezone,
        )
    }

    LaunchedEffect(authUiState.errorMessage) {
        authUiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            authViewModel.updateErrorMessage(null)
        }
    }

    LaunchedEffect(notificationUiState.message) {
        notificationUiState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            myPageViewModel.onMessageConsumed()
        }
    }

    LaunchedEffect(notificationUiState.testNotificationMessage) {
        notificationUiState.testNotificationMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            myPageViewModel.onTestNotificationMessageConsumed()
        }
    }

    LaunchedEffect(profileUiState.message) {
        profileUiState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            myPageViewModel.onProfileMessageConsumed()
        }
    }

    LaunchedEffect(notificationUiState.permissionRequired) {
        if (!notificationUiState.permissionRequired) return@LaunchedEffect
        if (hasNotificationPermission()) {
            myPageViewModel.onNotificationPermissionGranted(timezone = timezone)
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(authUiState.isLogoutCompleted, authUiState.isDeleteAccountCompleted) {
        if (authUiState.isLogoutCompleted || authUiState.isDeleteAccountCompleted) {
            onNavigateToOnBoarding()
        }
    }
    // 뒤로가기를 무시
    BackHandler(enabled = isProcessing) {
    }

    if (notificationUiState.showTimePicker) {
        NotificationHourPickerDialog(
            initialHour = notificationUiState.srsSettings.preferredNotificationTimeMinutes / 60,
            onDismiss = myPageViewModel::onTimePickerDismissed,
            onTimeConfirmed = { hour -> myPageViewModel.onTimeSelected(hour * 60) },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Scaffold(
            topBar = {
                UmmaAppBar(
                    title = "마이페이지",
                    isCenterTitle = true,
                    onBackClick = if (isProcessing) null else onBackClick,
                    actions = {
                        if (BuildConfig.DEBUG && BuildConfig.FLAVOR == "dev") {
                            IconButton(
                                onClick = myPageViewModel::onTestNotificationActionClicked,
                                enabled = !notificationUiState.isSendingTestNotification,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = stringResource(
                                        R.string.debug_notification_action_content_description,
                                    ),
                                )
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileCard(nickname = notificationUiState.nickname)

                NotificationSettingsCard(
                    uiState = notificationUiState,
                    onMarketingToggleChanged = { enabled ->
                        myPageViewModel.onMarketingNotificationToggleChanged(
                            enabled = enabled,
                            permissionGranted = hasNotificationPermission(),
                        )
                    },
                    onSrsToggleChanged = { enabled ->
                        myPageViewModel.onSrsNotificationToggleChanged(
                            enabled = enabled,
                            permissionGranted = hasNotificationPermission(),
                        )
                    },
                    onTimeSettingClicked = myPageViewModel::onTimeSettingClicked,
                )

                Text(
                    text = "설정",
                    fontSize = 18.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(SpacingS),
                )
// 설정 섹션: 주언어 변경 / 로그아웃
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
                    elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingRow(
                            icon = Icons.Default.Language,
                            label = "주언어 설정",
                            enabled = !isProcessing,
                            onClick = { showNativeLanguageDialog = true },
                            value = nativeLanguageOptions
                                .firstOrNull { it.first == profileUiState.primaryLang }
                                ?.second,
                        )
                        HorizontalDivider(color = BackgroundHighlight)
                        SettingRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            label = "로그아웃",
                            enabled = !isProcessing,
                            onClick = { showLogoutDialog = true },
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
                    elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
                ) {
                    SettingRow(
                        icon = Icons.Default.Warning,
                        label = "회원탈퇴",
                        enabled = !isProcessing,
                        onClick = { showDeleteAccountDialog = true },
                    )
                }

                if (showNativeLanguageDialog) {
                    UmmaDialog(
                        title = "주언어 선택",
                        modifier = Modifier.padding(horizontal = SpacingL),
                        onCancel = { showNativeLanguageDialog = false },
                        onConfirm = {
                            myPageViewModel.onPrimaryLanguageChanged(selectedNativeLanguage)
                            showNativeLanguageDialog = false
                        },
                        confirmText = "완료",
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                        ) {
                            // 다국어 미지원이라 한국어만 선택 가능. 지원 시 안내문과 enabled 조건 제거.
                            Text(
                                text = "현재는 한국어만 선택 가능합니다",
                                fontSize = 14.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = SpacingS),
                            )
                            nativeLanguageOptions.forEach { (code, label) ->
                                LanguageButton(
                                    text = label,
                                    isSelected = selectedNativeLanguage == code,
                                    enabled = code == LangCode.KO,
                                    onClick = { selectedNativeLanguage = code },
                                )
                            }
                        }
                    }
                }

                if (showLogoutDialog) {
                    UmmaDialog(
                        title = "로그아웃하시겠어요?",
                        modifier = Modifier.padding(horizontal = SpacingL),
                        confirmText = "확인",
                        onConfirm = {
                            showLogoutDialog = false
                            authViewModel.signOut()
                        },
                        onCancel = { showLogoutDialog = false },
                    ) {
                        Text(
                            text = "로그아웃 후 서비스를 이용하려면 다시 로그인해야 해요.",
                            modifier = Modifier
                                .background(
                                    color = BackgroundSecondary,
                                    shape = RoundedCornerShape(ChipCornerRadius),
                                )
                                .padding(horizontal = SpacingS, vertical = SpacingL),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (showDeleteAccountDialog) {
                    UmmaDialog(
                        title = "탈퇴하시겠어요?",
                        titleColor = TextLogout,
                        modifier = Modifier.padding(horizontal = SpacingL),
                        confirmText = "탈퇴",
                        confirmButtonColor = TextLogout,
                        onConfirm = {
                            showDeleteAccountDialog = false
                            authViewModel.beginDeleteAccountReauthentication()
                            val currentEmail = authViewModel.getCurrentUserEmail()
                            if (currentEmail.isNullOrBlank()) {
                                authViewModel.cancelDeleteAccountReauthentication(
                                    message = "현재 로그인한 Google 계정을 확인할 수 없습니다."
                                )
                                return@UmmaDialog
                            }

                            coroutineScope.launch {
                                runCatching {
                                    googleAuthorizationHelper.authorizeForDeleteAccount(currentEmail)
                                }.onSuccess { authorizationResult ->
                                    when (authorizationResult) {
                                        is DeleteAccountAuthorizationResult.Authorized -> {
                                            runCatching {
                                                googleAuthorizationHelper.revokeDeleteAccountAccess(
                                                    authorizationResult.account
                                                )
                                            }.onSuccess {
                                                authViewModel.deleteAccount()
                                            }.onFailure {
                                                authViewModel.cancelDeleteAccountReauthentication(
                                                    message = "Google 권한 해제에 실패했습니다. 다시 시도해주세요."
                                                )
                                            }
                                        }

                                        is DeleteAccountAuthorizationResult.ResolutionRequired -> {
                                            deleteAccountReauthLauncher.launch(
                                                IntentSenderRequest.Builder(
                                                    authorizationResult.intentSender
                                                ).build()
                                            )
                                        }
                                    }
                                }.onFailure {
                                    authViewModel.cancelDeleteAccountReauthentication(
                                        message = "Google 계정 확인에 실패했습니다. 다시 시도해주세요."
                                    )
                                }
                            }
                        },
                        onCancel = { showDeleteAccountDialog = false },
                    ) {
                        Text(
                            text = "회원탈퇴 후 계정과 학습 기록은 영구적으로 삭제되며 복구할 수 없어요.",
                            modifier = Modifier
                                .background(
                                    color = BackgroundSecondary,
                                    shape = RoundedCornerShape(ChipCornerRadius),
                                )
                                .padding(horizontal = SpacingS, vertical = SpacingL),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (notificationUiState.showTestNotificationDialog) {
                    UmmaDialog(
                        title = stringResource(R.string.debug_notification_dialog_title),
                        modifier = Modifier.padding(horizontal = SpacingL),
                        onCancel = myPageViewModel::onTestNotificationDialogDismissed,
                        onConfirm = myPageViewModel::onTestNotificationConfirmed,
                        confirmText = stringResource(R.string.debug_notification_send),
                        dismissText = stringResource(R.string.debug_notification_cancel),
                        confirmEnabled = !notificationUiState.isSendingTestNotification,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.debug_notification_dialog_body),
                                color = TextPrimary,
                                textAlign = TextAlign.Start,
                            )

                            Text(
                                text = stringResource(R.string.debug_notification_choice_label),
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                DebugTargetButton(
                                    text = stringResource(R.string.debug_notification_target_srs),
                                    selected = notificationUiState.testNotificationTarget ==
                                            NotificationTestTarget.SRS,
                                    enabled = !notificationUiState.isSendingTestNotification,
                                    onClick = {
                                        myPageViewModel.onTestNotificationTargetSelected(
                                            NotificationTestTarget.SRS,
                                        )
                                    },
                                )
                                DebugTargetButton(
                                    text = stringResource(R.string.debug_notification_target_marketing),
                                    selected = notificationUiState.testNotificationTarget ==
                                            NotificationTestTarget.MARKETING,
                                    enabled = !notificationUiState.isSendingTestNotification,
                                    onClick = {
                                        myPageViewModel.onTestNotificationTargetSelected(
                                            NotificationTestTarget.MARKETING,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

        }
        // 작업 중 화면 전체 입력을 막고 진행 상태를 표시
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ThemePrimary)
            }
        }
    }
}

/**
 * 상단 프로필 카드다.
 */
@Composable
private fun ProfileCard(nickname: String) {
    val displayName = nickname.ifBlank { "사용자" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingL),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(color = ThemePrimary, shape = CircleShape),
            )
            Column {
                Text(
                    text = "${displayName}님",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "오늘도 Umma와 함께 학습해요",
                    fontSize = 14.sp,
                    color = TextPrimary,
                )
            }
        }
    }
}

/**
 * 설정 목록의 공통 행(주언어 설정, 로그아웃, 회원탈퇴 등에서 재사용)
 * value를 넘기면 오른쪽 화살표 앞에 현재 값(예: "한국어) 표시
 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = TextPrimary,
    value: String? = null,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = BackgroundPrimary, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
        },
        headlineContent = { Text(text = label, color = tint) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!value.isNullOrBlank()) {
                    Text(text = value, color = tint.copy(alpha = 0.6f))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = tint,
                )
            }
        },
    )
}

/**
 * 알림 설정 카드다.
 */
@Composable
private fun NotificationSettingsCard(
    uiState: MyPageNotificationUiState,
    onMarketingToggleChanged: (Boolean) -> Unit,
    onSrsToggleChanged: (Boolean) -> Unit,
    onTimeSettingClicked: () -> Unit,
) {
    val hour = uiState.srsSettings.preferredNotificationTimeMinutes / 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
                leadingContent = {
                    NotificationLeadingIcon(
                        icon = Icons.Default.Campaign,
                        contentDescription = "마케팅 알림",
                    )
                },
                headlineContent = { Text(text = "마케팅 알림") },
                supportingContent = { Text(text = "이벤트와 새 소식을 알려드려요") },
                trailingContent = {
                    Switch(
                        checked = uiState.marketingSettings.enabled,
                        onCheckedChange = onMarketingToggleChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemePrimary,
                            checkedTrackColor = BackgroundSecondary,
                            checkedBorderColor = TextPrimary,
                        ),
                    )
                },
            )

            HorizontalDivider(color = BackgroundHighlight)

            ListItem(
                colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
                leadingContent = {
                    NotificationLeadingIcon(
                        icon = Icons.Default.Notifications,
                        contentDescription = "학습 알림",
                    )
                },
                headlineContent = { Text(text = "학습 알림") },
                supportingContent = { Text(text = "설정한 시간에 복습 알림을 보내드려요") },
                trailingContent = {
                    Switch(
                        checked = uiState.srsSettings.enabled,
                        onCheckedChange = onSrsToggleChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemePrimary,
                            checkedTrackColor = BackgroundSecondary,
                            checkedBorderColor = TextPrimary,
                        ),
                    )
                },
            )

            HorizontalDivider(color = BackgroundHighlight)

            ListItem(
                modifier = Modifier.clickable(
                    enabled = !uiState.isSaving,
                    onClick = onTimeSettingClicked,
                ),
                colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
                leadingContent = {
                    NotificationLeadingIcon(
                        icon = Icons.Default.Schedule,
                        contentDescription = "학습 알림 시간",
                    )
                },
                headlineContent = { Text(text = "학습 알림 시간") },
                supportingContent = { Text(text = formatHourLabel(hour)) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextPrimary,
                    )
                },
            )
        }
    }
}

@Composable
private fun RowScope.DebugTargetButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        border = if (selected) BorderStroke(1.5.dp, ThemePrimary) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BackgroundSecondary,
            disabledContainerColor = BackgroundSecondary,
        ),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier
            .weight(1f)
            .height(48.dp),
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> TextPrimary.copy(alpha = 0.3f)
                selected -> ThemePrimary
                else -> TextPrimary
            },
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun NotificationLeadingIcon(
    icon: ImageVector,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = BackgroundPrimary, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ThemePrimary,
        )
    }
}

/**
 * 오전/오후와 1~12 시계를 이용해 시간을 고르는 다이얼로그다.
 */
@Composable
private fun NotificationHourPickerDialog(
    initialHour: Int,
    onDismiss: () -> Unit,
    onTimeConfirmed: (Int) -> Unit,
) {
    var selectedHour by remember(initialHour) {
        mutableIntStateOf(initialHour.coerceIn(0, 23))
    }

    UmmaDialog(
        title = "학습 알림 시간 선택",
        modifier = Modifier.padding(horizontal = SpacingL),
        onCancel = onDismiss,
        onConfirm = {
            onTimeConfirmed(selectedHour)
            onDismiss()
        },
        confirmText = "저장",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PeriodToggleRow(
                selectedHour = selectedHour,
                onToggle = { isAm ->
                    val hour12 = toHour12(selectedHour)
                    selectedHour = toHour24(hour12 = hour12, isAm = isAm)
                },
            )

            ClockDial(
                selectedHour = selectedHour,
                onHourSelected = { selectedHour = it },
            )

            Text(
                text = formatHourLabel(selectedHour),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PeriodToggleRow(
    selectedHour: Int,
    onToggle: (Boolean) -> Unit,
) {
    val isAm = selectedHour < 12

    Row(
        modifier = Modifier
            .background(
                color = BackgroundHighlight,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PeriodChip(
            text = "오전",
            selected = isAm,
            onClick = { onToggle(true) },
        )
        PeriodChip(
            text = "오후",
            selected = !isAm,
            onClick = { onToggle(false) },
        )
    }
}

@Composable
private fun PeriodChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                color = if (selected) ThemePrimary else Color.Transparent,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    radius = 28.dp,
                    color = if (selected) Color.White.copy(alpha = 0.24f) else ThemePrimary.copy(
                        alpha = 0.18f
                    ),
                ),
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else TextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ClockDial(
    selectedHour: Int,
    onHourSelected: (Int) -> Unit,
    size: Dp = 260.dp,
) {
    val selectedHour12 = toHour12(selectedHour)
    val isAm = selectedHour < 12
    val radius = size * 0.36f
    var dialSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateHourFromPosition(position: Offset) {
        val hour12 = resolveHour12FromDialPosition(
            position = position,
            dialSize = dialSize,
        ) ?: return
        onHourSelected(toHour24(hour12 = hour12, isAm = isAm))
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = BackgroundClockDial,
                shape = CircleShape,
            )
            .pointerInput(isAm, dialSize) {
                detectDragGestures(
                    onDragStart = { position ->
                        updateHourFromPosition(position)
                    },
                    onDrag = { change, _ ->
                        updateHourFromPosition(change.position)
                        change.consume()
                    },
                )
            }
            .onSizeChanged { dialSize = it },
        contentAlignment = Alignment.Center,
    ) {
        // 시계 바늘 표시(시계 중심에서 선택한 시간 쪽으로 선 표시)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 시계 중심점
            val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
            // 선택한 시간이 가리키는 방향(각도)
            val angleRad = clockAngleRad(selectedHour12)
            // 끝점 = 중심 + (반지름 * 방향). cos = 가로
            val handEnd = Offset(
                x = centerOffset.x + (radius.toPx() * cos(angleRad)).toFloat(),
                y = centerOffset.y + (radius.toPx() * sin(angleRad)).toFloat(),
            )
            drawLine(
                color = ThemePrimary,
                start = centerOffset,
                end = handEnd,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color = ThemePrimary, shape = CircleShape),
        )

        hourDialNumbers.forEach { hour12 ->
            val angleRadians = clockAngleRad(hour12)
            val x = cos(angleRadians).toFloat() * radius.value
            val y = sin(angleRadians).toFloat() * radius.value
            val isSelected = selectedHour12 == hour12

            Box(
                modifier = Modifier
                    .offset(x = x.dp, y = y.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isSelected) ThemePrimary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = true,
                            radius = 21.dp,
                            color = if (isSelected) Color.White.copy(alpha = 0.24f) else ThemePrimary.copy(
                                alpha = 0.18f
                            ),
                        ),
                    ) {
                        onHourSelected(toHour24(hour12 = hour12, isAm = isAm))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = hour12.toString(),
                    color = if (isSelected) Color.White else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 리스트형 선택 버튼이다.
 */
@Composable
private fun LanguageButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BackgroundSecondary,
            disabledContainerColor = BackgroundSecondary,
        ),
        shape = RoundedCornerShape(30.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = when {
                !enabled -> TextPrimary.copy(alpha = 0.3f)
                isSelected -> ThemePrimary
                else -> TextPrimary
            },
        )
    }
}

// 시계에서 특정 시간이 가리키는 각도(라디안).
// 숫자 한 칸 = 30도(360/12), -90도로 12시를 맨 위에 맞춤(기본값은 3시가 0도).
private fun clockAngleRad(hour12: Int): Double =
    Math.toRadians(((hour12 % 12) * 30f - 90f).toDouble())

private fun toHour12(hour24: Int): Int {
    val normalized = hour24 % 12
    return if (normalized == 0) 12 else normalized
}

private fun toHour24(hour12: Int, isAm: Boolean): Int {
    val normalized = if (hour12 == 12) 0 else hour12
    return if (isAm) normalized else normalized + 12
}

private fun resolveHour12FromDialPosition(
    position: Offset,
    dialSize: IntSize,
): Int? {
    if (dialSize.width == 0 || dialSize.height == 0) return null

    val centerX = dialSize.width / 2f
    val centerY = dialSize.height / 2f
    val dx = position.x - centerX
    val dy = position.y - centerY

    if (dx == 0f && dy == 0f) return null

    var angle = Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0
    if (angle < 0) angle += 360.0

    val hourIndex = (angle / 30.0).roundToInt() % 12
    return if (hourIndex == 0) 12 else hourIndex
}

private val nativeLanguageOptions = listOf(
    LangCode.KO to "한국어",
    LangCode.EN to "English",
    LangCode.JA to "日本語",
    LangCode.DE to "Deutsch",
)

private val hourDialNumbers = (1..12).toList()

private fun formatHourLabel(hour: Int): String {
    return when {
        hour == 0 -> "오전 12시"
        hour < 12 -> "오전 ${hour}시"
        hour == 12 -> "오후 12시"
        else -> "오후 ${hour - 12}시"
    }
}
