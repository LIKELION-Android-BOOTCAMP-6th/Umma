package com.app.umma.presentation.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    var selectedNativeLanguage by remember { mutableStateOf(LangCode.KO) }

    val authUiState by authViewModel.uiState.collectAsState()
    val notificationUiState by myPageViewModel.uiState.collectAsState()
    val context = LocalContext.current
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

    if (notificationUiState.showTimePicker) {
        NotificationHourPickerDialog(
            initialHour = notificationUiState.srsSettings.preferredNotificationTimeMinutes / 60,
            onDismiss = myPageViewModel::onTimePickerDismissed,
            onTimeConfirmed = { hour -> myPageViewModel.onTimeSelected(hour * 60) },
        )
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "마이페이지",
                isCenterTitle = true,
                onBackClick = if (authUiState.isLoading) null else onBackClick,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        icon = Icons.Default.Language,
                        label = "모국어 설정",
                        enabled = !authUiState.isLoading,
                        onClick = { showNativeLanguageDialog = true },
                    )
                    HorizontalDivider(color = BackgroundHighlight)
                    SettingRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "로그아웃",
                        enabled = !authUiState.isLoading,
                        onClick = { showLogoutDialog = true },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
            ) {
                SettingRow(
                    icon = Icons.Default.Warning,
                    label = "회원탈퇴",
                    enabled = !authUiState.isLoading,
                    onClick = { showDeleteAccountDialog = true },
                )
            }

            if (showNativeLanguageDialog) {
                UmmaDialog(
                    title = "모국어 선택",
                    modifier = Modifier.padding(horizontal = SpacingL),
                    onCancel = { showNativeLanguageDialog = false },
                    onConfirm = { showNativeLanguageDialog = false },
                    confirmText = "완료",
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
                        authViewModel.deleteAccount()
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

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = TextPrimary,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = BackgroundSecondary),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = BackgroundHighlight, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
        },
        headlineContent = { Text(text = label, color = tint) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = tint,
            )
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
                    Button(
                        onClick = onTimeSettingClicked,
                        enabled = !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(text = "시간 변경")
                    }
                },
            )
        }
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
            .background(color = BackgroundHighlight, shape = CircleShape),
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
                    color = if (selected) Color.White.copy(alpha = 0.24f) else ThemePrimary.copy(alpha = 0.18f),
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
                color = BackgroundHighlight,
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
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color = ThemePrimary, shape = CircleShape),
        )

        hourDialNumbers.forEach { hour12 ->
            val angleDegrees = ((hour12 % 12) * 30f) - 90f
            val angleRadians = Math.toRadians(angleDegrees.toDouble())
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
                            color = if (isSelected) Color.White.copy(alpha = 0.24f) else ThemePrimary.copy(alpha = 0.18f),
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

        val selectedAngleDegrees = ((selectedHour12 % 12) * 30f) - 90f
        val selectedAngleRadians = Math.toRadians(selectedAngleDegrees.toDouble())
        val handX = cos(selectedAngleRadians).toFloat() * (radius.value - 18f)
        val handY = sin(selectedAngleRadians).toFloat() * (radius.value - 18f)

        Box(
            modifier = Modifier
                .offset(x = handX.dp, y = handY.dp)
                .size(14.dp)
                .background(color = ThemePrimary, shape = CircleShape),
        )
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
) {
    Button(
        onClick = onClick,
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundSecondary),
        shape = RoundedCornerShape(30.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = if (isSelected) ThemePrimary else TextPrimary,
        )
    }
}

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
