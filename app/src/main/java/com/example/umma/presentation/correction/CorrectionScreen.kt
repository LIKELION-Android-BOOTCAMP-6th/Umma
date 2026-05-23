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
 * SSOT: COR-001_Initial_State.md / COR-002_Suggestion_Generation.md
 *
 * COR-002-A 범위에서는 [CorrectionViewModel] 이 결정한 [CorrectionUiState.Phase] 에 따라
 * 텍스트로만 분기해 흐름 진행을 시각적으로 검증한다.
 * 본격 카드 UI (Content 상태) 는 COR-003-A 에서 교체된다.
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
            // COR-002-A 시각 검증용 텍스트 분기.
            // Generating/Content/Error 의 사용자 노출 디자인은 COR-003 에서 다룬다.
            when (uiState.phase) {
                CorrectionUiState.Phase.Loading -> {
                    Text(text = "로딩 중…", textAlign = TextAlign.Center)
                }

                CorrectionUiState.Phase.NotAvailable -> {
                    // 결손 케이스 — 어느 필드가 비었는지는 logcat (CorrectionViewModel) 으로 추적.
                    Text(text = "아직 교정할 대화가 없어요", textAlign = TextAlign.Center)
                }

                CorrectionUiState.Phase.Ready -> {
                    // Ready 진입 직후 ViewModel 이 즉시 Generating 으로 전이시키므로 이 분기는 보통 한 프레임만 보인다.
                    Text(text = "교정 결과를 준비합니다…", textAlign = TextAlign.Center)
                    Text(
                        text = "언어=${uiState.selectedLearningLanguage?.code} · " +
                                "최근 주제=${uiState.sessionSummary?.recentTopic ?: "-"}",
                        textAlign = TextAlign.Center
                    )
                }

                CorrectionUiState.Phase.Generating -> {
                    Text(text = "AI 가 교정 결과를 생성 중…", textAlign = TextAlign.Center)
                }

                CorrectionUiState.Phase.Content -> {
                    // 본격 카드 UI 는 COR-003-A. 여기서는 개수와 첫 카드 요약만 텍스트로 검증.
                    Text(
                        text = "교정 결과 ${uiState.suggestions.size}건",
                        textAlign = TextAlign.Center
                    )
                    uiState.suggestions.firstOrNull()?.let { first ->
                        Text(
                            text = "예) ${first.beforeText} → ${first.afterText}",
                            textAlign = TextAlign.Center
                        )
                    }
                }

                CorrectionUiState.Phase.Error -> {
                    Text(text = "교정 결과 생성에 실패했어요", textAlign = TextAlign.Center)
                    uiState.errorReason?.let { reason ->
                        Text(text = "사유: $reason", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
