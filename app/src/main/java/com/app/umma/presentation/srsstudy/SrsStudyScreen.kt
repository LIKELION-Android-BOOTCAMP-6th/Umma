package com.app.umma.presentation.srsstudy

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.umma.core.theme.BackgroundDeactivated
import com.app.umma.core.theme.ButtonScreenB
import com.app.umma.core.theme.PercentageDialogB
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.SpacingXL
import com.app.umma.core.theme.TextAnalysisR
import com.app.umma.core.theme.TextCardR
import com.app.umma.core.theme.TextCorrect
import com.app.umma.core.theme.TextCorrectionSB
import com.app.umma.core.theme.TextExplanationR
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.TitleDialogSB
import com.app.umma.core.ui.component.UmmaAppBar
import com.app.umma.core.ui.modifier.attentionBorder
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.presentation.srsstudy.component.SrsStudyCompletion

/** SRS 반복학습 화면입니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsStudyScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToCardList: () -> Unit,
    viewModel: SrsStudyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 학습 언어 세팅, 초기 로딩 시작
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    // 저장 실패 시 Snackbar 표시 + 재시도
    LaunchedEffect(uiState.hasSaveError) {
        if (uiState.hasSaveError) {
            val result = snackbarHostState.showSnackbar(
                message = "저장에 실패했습니다",
                actionLabel = "재시도",
                duration = SnackbarDuration.Long
            )
            // 재시도 버튼 클릭
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onConfirmRating()
            }
            viewModel.onClearSaveError()
        }
    }

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "학습",
                isCenterTitle = true,
                actions = {
                    IconButton(onClick = onNavigateToCardList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "저장된 카드 목록"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
        {
            when {
                uiState.isLoading -> SrsLoadingContent(Modifier.align(Alignment.Center))
                uiState.hasInitError -> SrsErrorContent(Modifier.align(Alignment.Center)) { viewModel.onRetry() }
                uiState.isDone -> SrsStudyCompletion(
                    studiedCardCount = uiState.studiedCardCount,
                    onNavigateToDashboard = onNavigateToDashboard,
                    modifier = Modifier.align(Alignment.Center)
                )

                uiState.cards.isEmpty() -> SrsEmptyContent(Modifier.align(Alignment.Center))
                else -> SrsStudyContent(
                    uiState = uiState,
                    onCardFlip = { viewModel.onCardFlip() },
                    onRatingSelected = { viewModel.onRatingSelected(it) },
                    onConfirmRating = { viewModel.onConfirmRating() },
                    onSpeak = { viewModel.onPlayPronunciation() }
                )
            }
        }
    }
}

//----- 로딩 완료 후 카드 진행 상황, 플래시 카드, 평가 버튼
@Composable
private fun SrsStudyContent(
    uiState: SrsStudyUiState,
    onCardFlip: () -> Unit,
    onRatingSelected: (ReviewRating) -> Unit,
    onConfirmRating: () -> Unit,
    onSpeak: () -> Unit
) {
    val card = uiState.currentCard ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SpacingL, vertical = SpacingL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(SpacingS))
            // 현재 카드 개수 / 총 카드 개수
            Text(text = "${uiState.currentCardIndex + 1}/${uiState.cards.size}")
            // 클릭 시 플래시카드 뒤집기
            SrsFlashCard(
                card = card,
                isFlipped = uiState.isCardFlipped,
                isSpeaking = uiState.isSpeaking,
                onFlip = onCardFlip,
                onSpeak = onSpeak
            )
            Spacer(modifier = Modifier.height(SpacingXL))
            SrsRatingButtons(
                isFlipped = uiState.isCardFlipped,
                selectedRating = uiState.selectedRating,
                onRatingSelected = onRatingSelected
            )
        }

        // 카드 뒷면일 때
        if (uiState.isCardFlipped) {
            FloatingActionButton(
                onClick = { onConfirmRating() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor =
                    if (uiState.selectedRating != null) ThemePrimary
                    else TextCorrect,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "다음 카드",
                    tint = Color.White
                )
            }
        }
    }
}

//----- 플래시 카드 클릭 시 앞 뒤 전환
@Composable
private fun SrsFlashCard(
    card: Flashcard,
    isFlipped: Boolean,
    isSpeaking: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            // 카드가 앞면일때만 클릭 유도를 위한 강조 테두리
            .then(
                if (!isFlipped) {
                    Modifier.attentionBorder(
                        shape = RoundedCornerShape(20.dp), animated = true
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onFlip() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (isFlipped) {
            SrsCardBack(card = card, isSpeaking = isSpeaking, onSpeak = onSpeak)
        } else {
            SrsCardFront(card = card)
        }
    }
}

//----- 카드 앞면
@Composable
private fun SrsCardFront(card: Flashcard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = card.frontText,
            fontSize = 15.sp,
            color = Color(0xFF777777),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SpacingL))

        if (!card.hint.isNullOrBlank()) {
            Text(
                text = card.hint,
                style = TitleDialogSB,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(SpacingL))
        }
        // 구분선
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(Color(0xFFE8750A))
        )

        Spacer(modifier = Modifier.height(SpacingL))
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            modifier = Modifier.height(24.dp)
        )
        Text(
            text = "눌러서 교정 확인",
            style = TextAnalysisR,
            color = TextPrimary
        )
    }
}

//----- 카드 뒷면
@Composable
private fun SrsCardBack(
    card: Flashcard,
    isSpeaking: Boolean,
    onSpeak: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "V CORRECT ANSWER",
                    style = TextCorrectionSB,
                    color = TextCorrect

                )
            }
            Spacer(modifier = Modifier.height(SpacingM))
            // 정답 문장
            Text(
                text = card.backText,
                style = PercentageDialogB,
                modifier = Modifier.padding(horizontal = SpacingXL)

            )
            // Grammar Note 박스
            if (card.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF8E1))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Grammar Note",
                            style = TextExplanationR,
                            color = Color(0xFFB8860B),
                        )
                        Spacer(modifier = Modifier.height(SpacingS))
                        Text(
                            text = card.explanation,
                            style = TextAnalysisR
                        )
                    }
                }
            }
        }
        // 우측 상단 스피커 버튼
        IconButton(
            onClick = onSpeak,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.VolumeUp,
                contentDescription = "발음 듣기",
                tint = if (isSpeaking) ThemePrimary else TextCorrect
            )
        }
    }
}

//----- 평가 버튼 (2x2 그리드)
@Composable
private fun SrsRatingButtons(
    isFlipped: Boolean,
    selectedRating: ReviewRating?,
    onRatingSelected: (ReviewRating) -> Unit,
) {
    Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SrsRatingButton(
                Modifier.weight(1f),
                "Again",
                "1m",
                TextPrimary,
                isSelected = selectedRating == ReviewRating.AGAIN,
                icon = Icons.Default.Refresh,
                isFlipped = isFlipped,
                onClick = {
                    onRatingSelected(ReviewRating.AGAIN)
                }
            )
            SrsRatingButton(
                Modifier.weight(1f),
                "Hard",
                "2h",
                TextPrimary,
                isSelected = selectedRating == ReviewRating.HARD,
                icon = Icons.Default.SentimentNeutral,
                isFlipped = isFlipped,
                onClick = {
                    onRatingSelected(ReviewRating.HARD)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SrsRatingButton(
                Modifier.weight(1f),
                "Good",
                "4h",
                TextPrimary,
                isSelected = selectedRating == ReviewRating.GOOD,
                icon = Icons.Default.SentimentSatisfiedAlt,
                isFlipped = isFlipped,
                onClick = {
                    onRatingSelected(
                        ReviewRating.GOOD
                    )
                }
            )
            SrsRatingButton(
                Modifier.weight(1f),
                "Easy",
                "Tomorrow",
                TextPrimary,
                isSelected = selectedRating == ReviewRating.EASY,
                icon = Icons.Default.SentimentVerySatisfied,
                isFlipped = isFlipped,
                onClick = {
                    onRatingSelected(ReviewRating.EASY)
                }
            )
        }
    }
}

@Composable
private fun SrsRatingButton(
    modifier: Modifier = Modifier,
    label: String,
    time: String,
    iconColor: Color,
    icon: ImageVector,
    isSelected: Boolean,
    isFlipped: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isSelected) BorderStroke(2.dp, iconColor) else null,
        colors = CardDefaults.cardColors(containerColor = if (isFlipped) Color.White else BackgroundDeactivated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }
            Text(
                text = label,
                fontWeight = ButtonScreenB.fontWeight,
                fontSize = TextAnalysisR.fontSize,
                color = TextPrimary
            )
            Text(
                text = time,
                style = TextCardR,
                color = TextCorrect
            )
        }
    }
}

//----- 로딩, 에러, 빈 상태, 완료
@Composable
private fun SrsLoadingContent(
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(modifier = modifier)
    Log.d("ummaDev", "SrsLoadingContent 로딩")
}

/**
 * 학습 정보 불러오지 못함,
 * 다시 시도 표시
 */
@Composable
private fun SrsErrorContent(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Log.d("ummaDev", "SrsErrorContent -----")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        Text("학습 정보를 불러오지 못했습니다")
        Button(onClick = onRetry) { Text(text = "다시 시도") }
    }
}

/** 복습할 카드 없음 */
@Composable
private fun SrsEmptyContent(modifier: Modifier = Modifier) {
    Log.d("ummaDev", "SrsEmptyContent -----")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        Text("없음")
        Text("오늘 복습할 카드가 없어요")
        Text("내일 다시 확인해보세요")
    }
}

