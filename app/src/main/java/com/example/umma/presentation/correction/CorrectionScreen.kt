package com.example.umma.presentation.correction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.umma.core.ui.component.UmmaAppBar

/**
 * 교정 화면을 구성하는 컴포저블입니다.
 *
 * SSOT: COR-001_Initial_State.md
 *
 * COR-001-A 범위에서는 [CorrectionViewModel] 이 판정한 [CorrectionUiState.Phase] 에 따라
 * 텍스트만 분기해 시각 검증을 가능하게 한다. 본 UI(교정 결과 카드 등)는 COR-002 이후에 다룬다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionScreen(
    viewModel: CorrectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입 시 1회만 Flow 셋업. ViewModel 내부에 가드가 있어 재호출되어도 안전.
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "교정",
                isCenterTitle = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // COR-001-A 시각 검증용 최소 분기. 본 UI 는 COR-002 백로그에서 교체된다.
            when (uiState.phase) {
                CorrectionUiState.Phase.Loading -> {
                    Text(text = "로딩 중…", textAlign = TextAlign.Center)
                }

                CorrectionUiState.Phase.NotAvailable -> {
                    // 결손 케이스 — 어느 필드가 비었는지는 logcat (CorrectionViewModel) 으로 추적.
                    Text(text = "아직 교정할 대화가 없어요", textAlign = TextAlign.Center)
                }

                CorrectionUiState.Phase.Ready -> {
                    // Ready 게이트 통과 — COR-002 에서 generateSuggestions 자동 호출 hook 이 붙는다.
                    Text(text = "교정 결과를 준비합니다…", textAlign = TextAlign.Center)
                    Text(
                        text = "언어=${uiState.selectedLearningLanguage?.code} · " +
                                "최근 주제=${uiState.sessionSummary?.recentTopic ?: "-"}",
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
