package com.app.umma.wear.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.app.umma.wear.presentation.WearChatUiState

@Composable
fun WearDebugApp(
    uiState: WearChatUiState,
    onAttachClick: () -> Unit,
    onReleaseOwnerClick: () -> Unit
) {
    MaterialTheme {
        WearDebugScreen(
            uiState = uiState,
            onAttachClick = onAttachClick,
            onReleaseOwnerClick = onReleaseOwnerClick
        )
    }
}

@Composable
private fun WearDebugScreen(
    uiState: WearChatUiState,
    onAttachClick: () -> Unit,
    onReleaseOwnerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onAttachClick,
            enabled = !uiState.isSending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Debug Attach")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onReleaseOwnerClick,
            enabled = !uiState.isSending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Release Phone Owner")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
