package com.app.umma.wear.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.ScrollIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.app.umma.wear.presentation.WearChatScreenMode
import com.app.umma.wear.presentation.WearChatUiState

private val WearBackground = Color(0xFFFEF4E7)
private val WearPrimary = Color(0xFFFF9717)
private val WearDanger = Color(0xFFCA1B1B)
private val WearMuted = Color(0xFFD9D9D9)
private val WearOverlay = Color(0xB3000000)
private val WearRetry = Color(0xFFD43A2F)

private val TitleStyle = TextStyle(
    color = WearPrimary,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center
)

private val MessageStyle = TextStyle(
    color = WearPrimary,
    fontSize = 12.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center
)

private val ErrorMessageStyle = MessageStyle.copy(
    color = WearDanger,
    fontSize = 11.sp,
    lineHeight = 14.sp
)

private val BannerStyle = MessageStyle.copy(
    color = WearDanger,
    fontSize = 10.sp,
    lineHeight = 12.sp
)

private val ButtonTextStyle = TextStyle(
    color = Color.White,
    fontSize = 18.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center
)

private val TargetInfoStyle = TextStyle(
    color = WearPrimary,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center
)

private val SecondaryButtonTextStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center
)

@Composable
fun WearDebugApp(
    uiState: WearChatUiState,
    onAttachClick: () -> Unit,
    onDetachClick: () -> Unit,
    onPttClick: () -> Unit,
    onRefreshTargetsClick: () -> Unit,
    onTargetChooserClick: () -> Unit,
    onTargetSelected: (String) -> Unit,
    onDismissTargetChooser: () -> Unit
) {
    MaterialTheme {
        WearDebugScreen(
            uiState = uiState,
            onAttachClick = onAttachClick,
            onDetachClick = onDetachClick,
            onPttClick = onPttClick,
            onRefreshTargetsClick = onRefreshTargetsClick,
            onTargetChooserClick = onTargetChooserClick,
            onTargetSelected = onTargetSelected,
            onDismissTargetChooser = onDismissTargetChooser
        )
    }
}

@Composable
private fun WearDebugScreen(
    uiState: WearChatUiState,
    onAttachClick: () -> Unit,
    onDetachClick: () -> Unit,
    onPttClick: () -> Unit,
    onRefreshTargetsClick: () -> Unit,
    onTargetChooserClick: () -> Unit,
    onTargetSelected: (String) -> Unit,
    onDismissTargetChooser: () -> Unit
) {
    val targetPage = remember(uiState.screenMode) {
        when (uiState.screenMode) {
            WearChatScreenMode.PRE_CONNECT,
            WearChatScreenMode.CONNECTED -> 0
            else -> 1
        }
    }
    val pagerState = rememberPagerState(initialPage = targetPage) { 2 }

    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearBackground)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                when (page) {
                    0 -> AttachPage(
                        uiState = uiState,
                        onClick = if (uiState.isAttached) onDetachClick else onAttachClick,
                        onRefreshTargetsClick = onRefreshTargetsClick,
                        onTargetChooserClick = onTargetChooserClick
                    )

                    else -> ConversationPage(
                        uiState = uiState,
                        onPttClick = onPttClick
                    )
                }
            }
        }

        HorizontalPageIndicator(
            pagerState = pagerState,
            selectedColor = WearPrimary,
            unselectedColor = Color.White,
            backgroundColor = Color.Transparent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        if (uiState.isTargetChooserVisible) {
            TargetChooserOverlay(
                uiState = uiState,
                onTargetSelected = onTargetSelected,
                onDismiss = onDismissTargetChooser
            )
        }
    }
}

@Composable
private fun AttachPage(
    uiState: WearChatUiState,
    onClick: () -> Unit,
    onRefreshTargetsClick: () -> Unit,
    onTargetChooserClick: () -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                BannerArea(message = uiState.transientBannerMessage)
            }
            item {
                MainAttachButton(
                    isAttached = uiState.isAttached,
                    enabled = !uiState.isSending,
                    onClick = onClick
                )
            }
            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
            item {
                AttachBody(
                    uiState = uiState,
                    onRefreshTargetsClick = onRefreshTargetsClick,
                    onTargetChooserClick = onTargetChooserClick
                )
            }
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        ScrollIndicator(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd),
            colors = ScrollIndicatorDefaults.colors(
                trackColor = WearPrimary
            ),
            reverseDirection = true
        )
    }
}

@Composable
private fun BannerArea(message: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.Center
    ) {
        if (message != null) {
            Text(
                text = message,
                style = BannerStyle
            )
        }
    }
}

@Composable
private fun MainAttachButton(
    isAttached: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(width = 2.dp, color = Color.White),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isAttached) WearDanger else WearPrimary
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isAttached) "휴대폰 연결 해제" else "휴대폰 연결",
                style = ButtonTextStyle
            )
        }
    }
}

@Composable
private fun AttachBody(
    uiState: WearChatUiState,
    onRefreshTargetsClick: () -> Unit,
    onTargetChooserClick: () -> Unit
) {
    if (uiState.screenMode == WearChatScreenMode.CONNECTED) {
        ConnectedPanel(uiState = uiState)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = uiState.bodyText,
            style = if (uiState.attachErrorMessage == null) MessageStyle else ErrorMessageStyle
        )

        if (uiState.showTargetControls) {
            Text(
                text = buildTargetLabel(uiState),
                style = TargetInfoStyle
            )

            if (uiState.availableTargets.size > 1) {
                Text(
                    text = "기기 변경",
                    style = TargetInfoStyle.copy(fontSize = 11.sp),
                    modifier = Modifier.clickable(onClick = onTargetChooserClick)
                )
            } else {
                RetryButton(
                    enabled = !uiState.isSending,
                    onClick = onRefreshTargetsClick
                )
            }
        }

    }
}

@Composable
private fun RetryButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(width = 2.dp, color = Color.White),
        colors = ButtonDefaults.buttonColors(containerColor = WearRetry)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row {
                Text(
                    text = "다시 찾기",
                    style = SecondaryButtonTextStyle,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = "↻",
                    style = SecondaryButtonTextStyle.copy(fontSize = 14.sp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun ConnectedPanel(uiState: WearChatUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "연결됨", style = TitleStyle)
        Text(
            text = "${uiState.selectedTargetDisplayName ?: "휴대폰"}에 연결됨",
            style = TargetInfoStyle
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = WearPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConversationPage(
    uiState: WearChatUiState,
    onPttClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = uiState.titleText,
                style = TitleStyle
            )
            PttButton(
                uiState = uiState,
                onClick = onPttClick
            )
        }
    }
}

@Composable
private fun PttButton(
    uiState: WearChatUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = !uiState.isSending && (uiState.canStartPtt || uiState.canReleasePtt)
    val backgroundColor = when (uiState.screenMode) {
        WearChatScreenMode.READY -> WearPrimary
        WearChatScreenMode.LISTENING -> WearDanger
        WearChatScreenMode.ERROR -> if (uiState.canStartPtt) WearPrimary else WearMuted
        else -> WearMuted
    }

    Box(
        modifier = modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(3.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (uiState.screenMode) {
            WearChatScreenMode.LISTENING -> {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }

            else -> MicrophoneGlyph()
        }
    }
}

@Composable
private fun MicrophoneGlyph() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 22.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(width = 4.dp, height = 12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(width = 18.dp, height = 3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun TargetChooserOverlay(
    uiState: WearChatUiState,
    onTargetSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearOverlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WearBackground)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(enabled = false, onClick = {}),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "연결할 휴대폰 선택",
                style = TargetInfoStyle.copy(fontSize = 12.sp)
            )
            uiState.availableTargets.forEach { target ->
                Button(
                    onClick = { onTargetSelected(target.nodeId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (target.nodeId == uiState.selectedTargetId) {
                            WearPrimary
                        } else {
                            WearMuted
                        }
                    )
                ) {
                    Text(
                        text = if (target.isNearby) {
                            "${target.displayName} · 근처"
                        } else {
                            target.displayName
                        },
                        style = ButtonTextStyle.copy(fontSize = 12.sp, lineHeight = 14.sp)
                    )
                }
            }
        }
    }
}

private fun buildTargetLabel(uiState: WearChatUiState): String {
    val targetName = uiState.selectedTargetDisplayName ?: "탐지된 휴대폰 없음"
    return "연결 대상: $targetName"
}
