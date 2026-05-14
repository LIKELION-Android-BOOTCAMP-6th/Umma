package com.example.umma.presentation.dashboard.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 신규 사용자(아직 학습 데이터가 없는 사용자)를 위한 Dashboard Empty 상태 UI.
 *
 * SSOT: DASH-001 — "신규 사용자는 Empty Dashboard UI 가 출력된다".
 *
 * 표시 조건(ViewModel 결정):
 *  - DashSummary 가 비어있고 (null 혹은 빈 값)
 *  - selectedLearningLanguage 는 결정되어 있는 상태
 *  - isLoading == false
 *
 * CTA(Call To Action - 행동 유도): 첫 대화를 시작하도록 유도 → onStartConversation()
 *      (DashboardScreen 에서 onNavigateToChat 을 그대로 전달)
 */
@Composable
fun DashboardEmpty(
    onStartConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "👋",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Umma 에 오신 것을 환영해요",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "첫 대화를 시작하면\n학습 통계와 복습 카드가 여기에 표시돼요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartConversation) {
            Text("대화 시작하기")
        }
    }
}