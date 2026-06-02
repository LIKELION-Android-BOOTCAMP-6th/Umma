package com.app.umma.presentation.dashboard

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.app.umma.R
import com.app.umma.core.theme.BackgroundHighlight
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.ui.component.UmmaDialog
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.presentation.auth.AuthViewModel
import java.util.TimeZone

/**
 * 마이페이지 화면을 구성하는 컴포저블입니다.
 *
 * 사용자 프로필 정보 및 앱 설정을 관리할 수 있는 인터페이스를 제공합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    onNavigateToOnBoarding: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    myPageViewModel: MyPageViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showNativeLanguageDialog by remember { mutableStateOf(false) }
    var selectedNativeLanguage by remember { mutableStateOf(LangCode.KO) }

    val authUiState by authViewModel.uiState.collectAsState()
    val notificationUiState by myPageViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val timezone = remember { TimeZone.getDefault().id }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            myPageViewModel.onNotificationPermissionGranted(timezone = timezone)
        } else {
            myPageViewModel.onNotificationPermissionDenied(timezone = timezone)
        }
    }

    LaunchedEffect(Unit) {
        myPageViewModel.onScreenStarted(
            permissionGranted = hasNotificationPermission(),
            timezone = timezone
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
    // 알림 권한 재귀
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
    // 타임피커 노출
    if (notificationUiState.showTimePicker) {
        NotificationTimePickerDialog(
            initialMinutes = notificationUiState.settings.preferredNotificationTimeMinutes,
            onDismiss = myPageViewModel::onTimePickerDismissed,
            onTimeConfirmed = myPageViewModel::onTimeSelected
        )
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "마이페이지",
                isCenterTitle = true,
                onBackClick = if (authUiState.isLoading) null else onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 상단 프로필 카드 (아바타 원 + 닉네임)
            ProfileCard(nickname = notificationUiState.nickname)
            // 학습 알림 카드
            NotificationSettingsCard(
                uiState = notificationUiState,
                onToggleChanged = { enabled ->
                    myPageViewModel.onNotificationToggleChanged(
                        enabled = enabled,
                        permissionGranted = hasNotificationPermission()
                    )
                },
                onTimeSettingClicked = myPageViewModel::onTimeSettingClicked
            )
            // 설정 섹션 헤더
            Text(
                text = "설정",
                fontSize = 18.sp,
                color = TextPrimary,
                modifier = Modifier.padding(SpacingS)
            )
            // 설정 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        icon = Icons.Default.Language,
                        label = "모국어 설정",
                        enabled = !authUiState.isLoading,
                        onClick = { showNativeLanguageDialog = true }
                    )
                    HorizontalDivider(color = BackgroundHighlight)
                    SettingRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "로그아웃",
                        enabled = !authUiState.isLoading,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }
            // 회원탈퇴
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
            ) {
                SettingRow(
                    icon = Icons.Default.Warning,
                    label = "회원탈퇴",
                    enabled = !authUiState.isLoading,
                    onClick = { showDeleteAccountDialog = true }
                )
            }

            if (showNativeLanguageDialog) {
                UmmaDialog(
                    title = "모국어 선택",
                    modifier = Modifier.padding(horizontal = SpacingL),
                    onCancel = { showNativeLanguageDialog = false },
                    onConfirm = { showNativeLanguageDialog = false },
                    confirmText = "완료"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {

                        nativeLanguageOptions.forEach { (code, label) ->
                            LanguageButton(
                                text = label,
                                isSelected = selectedNativeLanguage == code,
                                onClick = { selectedNativeLanguage = code }
                            )
                        }
                    }
                }
            }
            // Dialog 로그아웃
            if (showLogoutDialog) {
                UmmaDialog(
                    title = "로그아웃하시겠어요?",
                    modifier = Modifier.padding(horizontal = SpacingL),
                    confirmText = "확인",
                    onConfirm = {
                        showLogoutDialog = false
                        authViewModel.signOut()
                    },
                    onCancel = { showLogoutDialog = false }) {
                    Text(
                        text = "로그아웃 시 서비스 이용을 위해 다시 로그인해야 해요.",
                        modifier = Modifier
                            .background(
                                color = BackgroundSecondary,
                                shape = RoundedCornerShape(ChipCornerRadius)
                            )
                            .border(
                                color = ThemePrimary, width = 2.dp, shape = RoundedCornerShape(
                                    ChipCornerRadius
                                )
                            )
                            .padding(horizontal = SpacingS, vertical = SpacingL),
                        textAlign = TextAlign.Center
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
                        authViewModel.deleteAccount()
                    },
                    onCancel = { showDeleteAccountDialog = false }
                ) {
                    Text(
                        text = "회원탈퇴 시 회원님의 계정 및 학습 기록이 영구적으로 삭제되며, 복구가 불가능해져요.",
                        modifier = Modifier
                            .background(
                                color = BackgroundSecondary,
                                shape = RoundedCornerShape(ChipCornerRadius)
                            )
                            .border(
                                color = ThemePrimary, width = 2.dp, shape = RoundedCornerShape(
                                    ChipCornerRadius
                                )
                            )
                            .padding(horizontal = SpacingS, vertical = SpacingL),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 상단 프로필 카드
 */
@Composable
private fun ProfileCard(nickname: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingL)
        ) {
            // 이미지 원
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(color = ThemePrimary, shape = CircleShape)
            )
            Column {
                Text(
                    text = "${nickname}님",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "오늘도 Umma와 함께 학습해요 🧡",
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }
        }
    }
}

// 아이콘, 레이블, 화살표
@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = TextPrimary
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = BackgroundHighlight, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
        },
        headlineContent = { Text(text = label, color = tint) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = tint
            )
        }
    )
}

/**
 * Card for SRS notification settings.
 */
@Composable
private fun NotificationSettingsCard(
    uiState: MyPageNotificationUiState,
    onToggleChanged: (Boolean) -> Unit,
    onTimeSettingClicked: () -> Unit
) {
    val hour = uiState.settings.preferredNotificationTimeMinutes / 60
    val minute = uiState.settings.preferredNotificationTimeMinutes % 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = BackgroundHighlight, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = ThemePrimary
                        )
                    }
                },
                headlineContent = { Text(text = "학습 알림") },
                supportingContent = { Text(text = "하루 한번 정하신 시간에 학습 알림을 보내드려요.") },
                trailingContent = {
                    Switch(
                        checked = uiState.settings.enabled,
                        onCheckedChange = onToggleChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemePrimary,
                            checkedTrackColor = BackgroundSecondary,
                            checkedBorderColor = TextPrimary
                        )
                    )
                }
            )

            ListItem(
                colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = BackgroundHighlight, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = ThemePrimary
                        )

                    }
                },
                headlineContent = { Text(text = "알림 시간") },
                supportingContent = { Text(text = String.format("%02d:%02d", hour, minute)) },
                trailingContent = {
                    Button(
                        onClick = onTimeSettingClicked,
                        enabled = !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(text = "시간 변경")
                    }
                }
            )
        }
    }
}

/**
 * Wrapper that shows the platform time picker dialog.
 */
@Composable
private fun NotificationTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onTimeConfirmed: (Int) -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(initialMinutes, context) {
        val themedContext = ContextThemeWrapper(context, R.style.CustomTimePickerTheme)

        val dialog = TimePickerDialog(
            themedContext,
            { _, hourOfDay, minute ->
                onTimeConfirmed(hourOfDay * 60 + minute)
            },
            initialMinutes / 60,
            initialMinutes % 60,
            true
        )
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.time_picker_bg)
        }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()

        onDispose {
            dialog.dismiss()
        }
    }
}

/**
 * 다이얼로그에 학습 언어 리스트에 사용되는 버튼
 */
@Composable
private fun LanguageButton(
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
        shape = RoundedCornerShape(30.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 4.dp)
    )
    {
        Text(
            text = text,
            fontSize = 16.sp,
            color = if (isSelected) ThemePrimary else TextPrimary
        )
    }
}

private val nativeLanguageOptions = listOf(
    LangCode.KO to "한국어",
    LangCode.EN to "English",
    LangCode.JA to "日本語",
    LangCode.DE to "Deutsch"
)
