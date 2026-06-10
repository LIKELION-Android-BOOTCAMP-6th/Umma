package com.app.umma.presentation.srsstudy

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.umma.core.theme.BackgroundDeactivated
import com.app.umma.core.theme.ButtonScreenB
import com.app.umma.core.theme.CardElevation
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.RatingAgain
import com.app.umma.core.theme.RatingEasy
import com.app.umma.core.theme.RatingHard
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
import com.app.umma.core.theme.TextSecondaryR
import com.app.umma.core.theme.TextWrong
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.TitleB
import com.app.umma.core.theme.TitleColor
import com.app.umma.core.theme.TitleScreenSB
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
    onNavigateToCorrection: () -> Unit,
    viewModel: SrsStudyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 학습 언어 세팅, 초기 로딩 시작
    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopPronunciation()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPronunciation()
        }
    }

    // 저장 실패 시 Snackbar 표시 + 재시도
    // 재시도 클릭 시 마지막 평가(lastRating)로 다시 저장 시도
    LaunchedEffect(uiState.hasSaveError) {
        if (uiState.hasSaveError) {
            val result = snackbarHostState.showSnackbar(
                message = "저장에 실패했습니다",
                actionLabel = "재시도",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onRetryRating()
            }
            viewModel.onClearSaveError()
        }
    }

    Scaffold(
        // 루트 Scaffold가 이미 하단 네비/시스템 inset을 처리하므로,
        // 화면 자체 Scaffold는 inset을 중복 적용하지 않는다(버튼 아래 이중 여백 방지).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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

                uiState.cards.isEmpty() -> SrsEmptyContent(
                    Modifier.align(
                        Alignment.Center
                    ),
                    onNavigateToCorrection = onNavigateToCorrection
                )

                else -> SrsStudyContent(
                    uiState = uiState,
                    onCardFlip = { viewModel.onCardFlip() },
                    onRatingSelected = { viewModel.onRatingSelected(it) },
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
    onSpeak: () -> Unit
) {
    val card = uiState.currentCard ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpacingL)
            .padding(vertical = SpacingM),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(SpacingS))
        // 현재 카드 개수 / 총 카드 개수
        val displayTotalCount = uiState.studiedCardCount.takeIf { it > 0 } ?: uiState.cards.size
        val displayCurrentCount = (uiState.currentCardIndex + 1)
            .coerceAtMost(displayTotalCount)
            .coerceAtLeast(1)

        Text(
            text = "$displayCurrentCount / $displayTotalCount",
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = SpacingS),
            textAlign = TextAlign.End,
            style = TextAnalysisR,
            color = TitleColor
        )
        // 클릭 시 플래시카드 뒤집기
        SrsFlashCard(
            modifier = Modifier.weight(1f),
            card = card,
            isFlipped = uiState.isCardFlipped,
            isSpeaking = uiState.isSpeaking,
            onFlip = onCardFlip,
            onSpeak = onSpeak
        )
        Spacer(modifier = Modifier.height(SpacingS))
        // 앞면은 카드 영역 탭으로만 뒤집고, 하단 평가 버튼은 뒷면에서만 활성화한다.
        SrsRatingButtons(
            isFlipped = uiState.isCardFlipped,
            isSaving = uiState.isSaving,
            onRatingSelected = onRatingSelected,
            againLabel = uiState.againLabel,
            hardLabel = uiState.hardLabel,
            goodLabel = uiState.goodLabel,
            easyLabel = uiState.easyLabel,
        )
    }
}

//----- 플래시 카드 클릭 시 앞 뒤 전환
@Composable
private fun SrsFlashCard(
    modifier: Modifier = Modifier,
    card: Flashcard,
    isFlipped: Boolean,
    isSpeaking: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
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
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
    ) {
        // 카드 전환(뒤집기/다음 카드)이 하드 컷으로 보이지 않도록 내용만 부드럽게 교차 페이드한다.
        Crossfade(
            targetState = card to isFlipped,
            label = "srsCardFace"
        ) { (currentCard, flipped) ->
            if (flipped) {
                SrsCardBack(card = currentCard, isSpeaking = isSpeaking, onSpeak = onSpeak)
            } else {
                SrsCardFront(card = currentCard)
            }
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
            style = TextSecondaryR,
            color = Color(0xFF777777),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SpacingL))

        if (!card.hint.isNullOrBlank()) {
            Text(
                text = card.hint,
                style = TitleB,
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
        // 아이콘 펄스 애니메이션
        val tapTransition = rememberInfiniteTransition(label = "tapHintIcon")
        val tapAlpha by tapTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tapHintAlpha"
        )
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            tint = TextPrimary.copy(alpha = tapAlpha),
            modifier = Modifier.height(24.dp)
        )
        Text(
            text = "눌러서 교정 확인",
            style = TextExplanationR,
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
                .verticalScroll(rememberScrollState())
                .padding(vertical = SpacingL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CORRECT ANSWER",
                    style = TextCorrectionSB,
                    color = TextCorrect

                )
            }
            Spacer(modifier = Modifier.height(SpacingM))

            Text(
                text = card.frontText,
                style = TextExplanationR,
                color = TextWrong,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = SpacingXL)
            )
            Spacer(modifier = Modifier.height(SpacingM))
            // 정답 문장
            Text(
                text = card.backText,
                style = TitleScreenSB,
                modifier = Modifier.padding(horizontal = SpacingXL)
            )
            // Grammar Note 박스
            if (card.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpacingL)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF8E1))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Grammar Note",
                            style = TextSecondaryR,
                            color = Color(0xFFB8860B),
                        )
                        Spacer(modifier = Modifier.height(SpacingS))
                        Text(
                            text = card.explanation,
                            style = TextExplanationR
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
    isSaving: Boolean,
    onRatingSelected: (ReviewRating) -> Unit,
    againLabel: String,
    hardLabel: String,
    goodLabel: String,
    easyLabel: String,
) {
    Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SrsRatingButton(
                Modifier.weight(1f),
                "Again",
                againLabel,
                RatingAgain,
                icon = Icons.Default.Refresh,
                isFlipped = isFlipped,
                enabled = isFlipped && !isSaving,
                onClick = {
                    onRatingSelected(ReviewRating.AGAIN)
                }
            )
            SrsRatingButton(
                Modifier.weight(1f),
                "Hard",
                hardLabel,
                RatingHard,
                icon = Icons.Default.SentimentNeutral,
                isFlipped = isFlipped,
                enabled = isFlipped && !isSaving,
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
                goodLabel,
                ThemePrimary,
                icon = Icons.Default.SentimentSatisfiedAlt,
                isFlipped = isFlipped,
                enabled = isFlipped && !isSaving,
                onClick = {
                    onRatingSelected(ReviewRating.GOOD)
                }
            )
            SrsRatingButton(
                Modifier.weight(1f),
                "Easy",
                easyLabel,
                RatingEasy,
                icon = Icons.Default.SentimentVerySatisfied,
                isFlipped = isFlipped,
                enabled = isFlipped && !isSaving,
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
    isFlipped: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val disabledContentColor = TextCorrect.copy(alpha = 0.45f)
    val buttonContentColor = if (enabled) TextPrimary else disabledContentColor
    val buttonSubTextColor = if (enabled) TextCorrect else disabledContentColor
    val buttonIconColor = if (enabled) iconColor else disabledContentColor

    Card(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            // 앞면에서는 평가를 저장할 수 없으므로 하단 버튼 영역 자체를 비활성화한다.
            indication = if (isFlipped) LocalIndication.current else null,
            enabled = enabled
        ) { onClick() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = CardElevation,
        ),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isFlipped && enabled) Color.White else BackgroundDeactivated
            )
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
                    tint = buttonIconColor,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }
            // Again, Hard, Good, Easy
            Text(
                text = label,
                style = ButtonScreenB,
                color = buttonContentColor
            )
            // 다시, 10분, 1일, 4일
            Text(
                text = time,
                style = TextCardR,
                color = buttonSubTextColor
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
private fun SrsEmptyContent(
    modifier: Modifier = Modifier,
    onNavigateToCorrection: () -> Unit
) {
    Log.d("ummaDev", "SrsEmptyContent -----")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        Text("오늘 복습할 카드가 없어요")
        Text("내일 다시 확인해보세요")
        Spacer(modifier = Modifier.height(SpacingL))
        Button(
            onClick = onNavigateToCorrection,
            shape = RoundedCornerShape(ChipCornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
            modifier = Modifier
                .padding(top = SpacingL)
                .padding(horizontal = SpacingL, vertical = SpacingS),
        ) {
            Text(
                "AI 교정하러 가기",
            )
        }
    }
}

