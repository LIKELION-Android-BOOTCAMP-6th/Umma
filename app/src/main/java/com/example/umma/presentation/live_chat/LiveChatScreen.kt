package com.example.umma.presentation.live_chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.umma.domain.model.AIState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(
    viewModel: LiveChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.startChat()
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Umma English", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 상태 및 자막 표시 영역
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // AI 상태 뱃지
                Surface(
                    color = when (uiState.aiState) {
                        AIState.SPEAKING -> MaterialTheme.colorScheme.primaryContainer
                        AIState.LISTENING -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = uiState.aiState.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 자막 표시
                Text(
                    text = uiState.lastTranscription.ifEmpty { "대화를 시작해 보세요!" },
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. 에러 메시지 표시
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            // 3. 말하기 제어 버튼
            Button(
                onClick = {
                    if (uiState.isChatting) {
                        viewModel.stopChat()
                    } else {
                        val permissionCheck = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        )

                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startChat()
                        } else {
                            launcher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier
                    .size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isChatting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isChatting) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
