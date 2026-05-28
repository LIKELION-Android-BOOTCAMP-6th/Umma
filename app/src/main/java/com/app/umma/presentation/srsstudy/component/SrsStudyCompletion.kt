package com.app.umma.presentation.srsstudy.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.SpacingXL

/**
 * 모든 카드 평가가 끝났을 때 표시하는 완료 화면
 *
 * studiedCardCount: 학습한 카드 수 (덱 최초 로드 시점 size, Again 미포함)
 * onNavigateToDashboard: 완료 버튼 클릭 시 실행
 */
@Composable
fun SrsStudyCompletion(
    studiedCardCount: Int,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(SpacingXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        Text("학습 완료")
        Text("${studiedCardCount}장 학습 완료")
        Button(onClick = onNavigateToDashboard) {
            Text("Dashboard 로 돌아가기")
        }
    }
}