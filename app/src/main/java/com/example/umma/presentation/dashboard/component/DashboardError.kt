package com.example.umma.presentation.dashboard.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.umma.R
import com.example.umma.core.theme.SpacingM

/**
 * Dashboard 의 복구 불가 상태 UI. (DASH-006 Phase 3 / AC 10)
 * AC 10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다.
 *
 * 트리거 조건 (ViewModel 의 observe collect 안):
 *  - userPref 는 있지만 learningLangs 가 empty
 *  - (향후) load 실패 시그널이 명시적으로 들어오는 경우
 *
 * 재시도: [onRetry] 콜백 → DashboardScreen 이 viewModel.onEnter() 재호출.
 *  - enterJob 이 active 면 skip (observe collect 이미 살아있음)
 *  - fetchJob 이 active 면 skip (in-flight sync 존재)
 *  - 둘 다 끝났으면 새 sync 시도 → repo 가 최신 _state 받아오면 observe 가 새 emit
 *    → learningLangs 채워지면 hasFatalError=false 로 자연 회복
 */
@Composable
fun DashboardError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.dashboard_err_no_learning_langs))
        Spacer(modifier = Modifier.height(SpacingM))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.dashboard_retry))
        }
    }
}