package com.app.umma.wear.presentation.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.app.umma.wear.presentation.WearChatUiState

@Composable
fun WearDebugApp(
    uiState: WearChatUiState,
    onAttachClick: () -> Unit,
    onPttClick: () -> Unit,
    onReleaseOwnerClick: () -> Unit
) {
    MaterialTheme {
        WearDebugScreen(
            uiState = uiState,
            onAttachClick = onAttachClick,
            onPttClick = onPttClick,
            onReleaseOwnerClick = onReleaseOwnerClick
        )
    }
}

@Composable
private fun WearDebugScreen(
    uiState: WearChatUiState,
    onAttachClick: () -> Unit,
    onPttClick: () -> Unit,
    onReleaseOwnerClick: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        state = listState
    ) {
        item {
            Button(
                onClick = onAttachClick,
                enabled = !uiState.isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Connect Chat")
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Button(
                onClick = onPttClick,
                enabled = !uiState.isSending && (uiState.canStartPtt || uiState.canReleasePtt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (uiState.canReleasePtt) "Release PTT" else "Press PTT")
            }
        }

        if (uiState.showDebugActions) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Button(
                    onClick = onReleaseOwnerClick,
                    enabled = !uiState.isSending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Release Phone Owner")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Text(
                text = "status=${uiState.chatStatus}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                text = "sessionId=${uiState.currentSessionId ?: "-"}\nplaying=${uiState.isPlaying}\nrecording=${uiState.isRecording}\nerror=${uiState.errorCode ?: "-"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
