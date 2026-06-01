package com.app.umma.presentation.srsstudy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.TextSecondaryR
import com.app.umma.core.theme.TextWrong
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.domain.model.flashcard.Flashcard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsCardListScreen(
    onBack: () -> Unit,
    viewModel: SrsCardListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEnter() }

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
                    SrsCardList(cards = uiState.cards)
            }
        }
    }
}

// 카드들을 세로로 나열
@Composable
private fun SrsCardList(cards: List<Flashcard>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(SpacingL),
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        items(cards) { card ->
            SrsCardListItem(card = card)
        }
    }
}

@Composable
private fun SrsCardListItem(card: Flashcard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
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