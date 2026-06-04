package com.app.umma.presentation.srsstudy

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.CardCornerRadius
import com.app.umma.core.theme.CardElevation
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextAnalysisR
import com.app.umma.core.theme.TextCorrect
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.TextSecondaryR
import com.app.umma.core.theme.TextWrong
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.presentation.correction.component.CorrectionSelectAllBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsCardListScreen(
    onBack: () -> Unit,
    viewModel: SrsCardListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.onEnter() }

    // 메시지(Toast) 한 번 표시 후 소비
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onMessageConsumed()
        }
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "카드 리스트",
                isCenterTitle = true,
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.hasLoadError ->
                    Text(
                        "불러오지 못했습니다.",
                        style = TextSecondaryR,
                        color = TextPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )

                uiState.cards.isEmpty() ->
                    Text(
                        "저장된 카드가 없습니다.",
                        style = TextSecondaryR,
                        color = TextPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )

                else ->
                    SrsCardListContent(
                        uiState = uiState,
                        onToggleSelectAll = viewModel::toggleSelectAll,
                        onToggleSelection = viewModel::toggleSelection,
                        onDeleteClick = viewModel::deleteSelected
                    )
            }
        }
    }
}

@Composable
private fun SrsCardListContent(
    uiState: SrsCardListUiState,
    onToggleSelectAll: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 상단: 전체 선택 바
        CorrectionSelectAllBar(
            totalCount = uiState.cards.size,
            selectedCount = uiState.selectedCount,
            allSelected = uiState.areAllSelected,
            onToggleSelectAll = onToggleSelectAll
        )

        // 카드 목록
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = SpacingL),
            verticalArrangement = Arrangement.spacedBy(SpacingS)
        ) {
            items(uiState.cards) { card ->
                SrsCardListItem(
                    card = card,
                    isSelected = card.id in uiState.selectedIds,
                    onClick = { onToggleSelection(card.id) }
                )
            }
        }

        // 하단: 삭제 버튼 (선택된 카드가 있을 때만 활성)
        Button(
            onClick = onDeleteClick,
            enabled = uiState.hasSelection && !uiState.isDeleting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingL),
            colors = ButtonDefaults.buttonColors(containerColor = TextLogout)
        ) {
            Text(text = "삭제 (${uiState.selectedCount})")
        }
    }
}

@Composable
private fun SrsCardListItem(
    card: Flashcard,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
        // 선택 시 border로 강조
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null
    ) {
        Column(modifier = Modifier.padding(SpacingM)) {
            // 학습언어 정답
            Text(
                text = card.backText,
                style = TextSecondaryR,
                color = TextCorrect
            )
            // 모국어
            Text(
                text = card.frontText,
                style = TextAnalysisR,
                color = TextWrong
            )
        }
    }
}
