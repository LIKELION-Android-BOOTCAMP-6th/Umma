package com.app.umma.presentation.srsstudy

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.core.tts.TextToSpeechController
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.ReviewDecision
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.flashcardreview.ApplyReviewDecisionUseCase
import com.app.umma.domain.usecase.flashcardreview.ObserveReviewDeckUseCase
import com.app.umma.domain.usecase.flashcardreview.ReviewSchedulePolicy
import com.app.umma.domain.usecase.flashcardreview.StartReviewSessionUseCase
import com.app.umma.domain.usecase.flashcardreview.SyncDirtyFlashcardsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SRS 반복학습 화면의 상태와 동작을 관리
 *
 * 화면 진입 시 학습 초기화(학습 언어 고정) 시작
 * Loading / Error(다시시도) 상태 관리
 */
@HiltViewModel
class SrsStudyViewModel @Inject constructor(
    private val startReviewSession: StartReviewSessionUseCase,
    private val observeReviewDeck: ObserveReviewDeckUseCase,
    private val getCurrentUserUid: GetCurrentUserUidUseCase,
    private val applyReviewDecision: ApplyReviewDecisionUseCase,
    private val ttsController: TextToSpeechController,
    private val syncDirtyFlashcards: SyncDirtyFlashcardsUseCase,
    private val schedulePolicy: ReviewSchedulePolicy,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SrsStudyUiState())
    val uiState: StateFlow<SrsStudyUiState> = _uiState.asStateFlow()

    // 초기화 작업 추적용
    // 화면 빠르게 여러번 탭 해도 초기화 중복 실행 X
    private var initJob: Job? = null

    // 카드 관찰 job (언어 로드 완료 후 시작)
    private var deckJob: Job? = null

    // 화면 열리면 호출
    fun onEnter() {
        // 이미 초기화 중이면 return
        if (initJob?.isActive == true) {
            return
        }
        // 학습 언어 읽는 중
        initJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, hasInitError = false, cards = emptyList())
            }
            val uid = getCurrentUserUid.getCurrentUserUid()
            if (uid != null) launch { syncDirtyFlashcards(uid) }

            // 현재 학습 언어 가져오는 UseCase
            startReviewSession().onSuccess { lang ->
                _uiState.update {
                    it.copy(
                        selectedLearningLanguage = lang,
                        hasInitError = false
                    )
                }
                startDeckObservation(lang)
            }.onFailure { e ->
                Log.d("ummaDev", "SrsStudyViewModel onEnter - 언어 가져오기 실패: $e")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasInitError = true,
                    )
                }
            }
        }
    }

    /** 복습 카드 관찰 시작 (언어 로드 직후 호출) */
    private fun startDeckObservation(language: LangCode?) {
        // 언어 없음 -> 카드 없는 빈 화면
        if (language == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        val userUid = getCurrentUserUid.getCurrentUserUid()
        // 로그인 X, 로그인 화면으로 이동 필요
        if (userUid == null) {
            _uiState.update { it.copy(isLoading = false, hasInitError = true) }
            return
        }
        deckJob?.cancel()
        deckJob = viewModelScope.launch {
            observeReviewDeck(userUid, language).collect { deckState ->
                when (deckState) {
                    // 카드 있음 -> 첫 번째 카드부터 시작
                    is ReviewDeckState.Content -> {
                        _uiState.update { it ->
                            if (it.cards.isNotEmpty() && !it.isDone) {
                                // 이미 진행 중인 덱이 있으면 덱을 갈아엎지 않고 로딩만 해제
                                it.copy(isLoading = false, hasInitError = false)
                            } else {
                                // 최초 로드: 새 덱으로 초기화
                                it.copy(
                                    isLoading = false,
                                    hasInitError = false,
                                    cards = deckState.cards,
                                    currentCardIndex = 0,
                                    isCardFlipped = false,
                                    isDone = false,
                                    // 덱 최초 로드 시점의 카드 수 고정
                                    // again 평가로 늘어난 카드 영향 X
                                    studiedCardCount = deckState.cards.size
                                )
                            }
                        }
                        // 레이블 갱신
                        _uiState.value.currentCard?.let { updateRatingLabels(it) }
                    }

                    is ReviewDeckState.Empty -> _uiState.update {
                        it.copy(isLoading = false, cards = emptyList())
                    }
                    // 실패 -> 다시 시도 화면
                    is ReviewDeckState.Retry,
                    is ReviewDeckState.Error -> _uiState.update {
                        it.copy(isLoading = false, hasInitError = true)
                    }
                }
            }
        }
    }

    /**
     * "다시 시도" 버튼 클릭 시 실행
     */
    fun onRetry() {
        ttsController.stop()
        deckJob?.cancel()
        initJob = null
        deckJob = null
        onEnter()
    }

    /** 카드 클릭 시 앞 뒤 전환 */
    fun onCardFlip() {
        _uiState.update { it.copy(isCardFlipped = !it.isCardFlipped) }
    }

    // 저장 실패 시 재시도 버튼을 누르면 이 값으로 다시 저장을 시도
    private var lastRating: ReviewRating? = null

    /**
     * 평가 버튼 클릭 -> 즉시 저장 -> 다음 카드로 이동
     *
     * 저장에 실패하면 hasSaveError=true -> 화면에서 Snackbar로 재시도를 안내
     */
    fun onRatingSelected(rating: ReviewRating) {
        // 이미 저장 중이면 중복 실행 방지
        if (_uiState.value.isSaving) return
        val card = _uiState.value.currentCard ?: return
        val userId = getCurrentUserUid.getCurrentUserUid() ?: return

        // 실패 시 재시도할 수 있도록 마지막 평가를 기억
        lastRating = rating

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, hasSaveError = false) }

            val decision = ReviewDecision(
                flashcardId = card.id,
                rating = rating,
                reviewedAt = System.currentTimeMillis()
            )
            // SM-2 계산 + Room 저장 + Summary 갱신
            // 평가 결과로 다음 복습 간격을 계산하고, 기기 DB와 대시보드 요약에 반영
            applyReviewDecision(userId, card, decision).onSuccess {
                ttsController.stop()
                // Again 카드는 "방금 계산된 schedule"을 반영해 넣음
                _uiState.update { state ->
                    // Again 선택 시 현재 카드를 덱 끝에 추가해 당일 재노출
                    val rescheduledCard: Flashcard? = if (rating == ReviewRating.AGAIN) {
                        val next = schedulePolicy.calculateNextSchedule(
                            card.schedule,
                            rating,
                            decision.reviewedAt
                        )
                        card.copy(
                            schedule = card.schedule.copy(
                                interval = next.interval,
                                easeFactor = next.easeFactor,
                                nextReviewAt = next.nextReviewAt
                            ),
                            lastReviewRating = rating,
                            lastReviewedAt = decision.reviewedAt
                        )
                    } else {
                        null
                    }

                    val updatedCards = if (rescheduledCard != null) {
                        state.cards + rescheduledCard
                    } else {
                        state.cards
                    }
                    val nextIndex = state.currentCardIndex + 1
                    val isDone = nextIndex >= updatedCards.size
                    state.copy(
                        cards = updatedCards,
                        // 마지막 카드면 인덱스 유지, 아니면 다음으로 이동
                        currentCardIndex = if (isDone) state.currentCardIndex else nextIndex,
                        isCardFlipped = false,
                        isDone = isDone,
                        isSaving = false,
                        isSpeaking = false
                    )
                }
                // 다음 카드 기준으로 버튼 라벨 갱신 (update 밖에서 호출 = 중첩 update 방지)
                _uiState.value.currentCard?.let { updateRatingLabels(it) }
            }.onFailure { e ->
                Log.d("ummaDev", "SrsStudyViewModel onRatingSelected - $e")
                _uiState.update {
                    it.copy(isSaving = false, hasSaveError = true)
                }
            }
        }
    }

    /**
     * Snackbar 재시도 버튼 클릭 시 호출
     *
     * 마지막으로 선택한 평가(lastRating)로 다시 저장을 시도
     * lastRating이 없으면(예: 앱 재시작) 아무것도 하지 않음
     */
    fun onRetryRating() {
        val rating = lastRating ?: return
        onRatingSelected(rating)
    }

    /**
     * 저장 실패 에러 상태 초기화
     * 예: 스낵바 닫힘, 재시도 클릭
     */
    fun onClearSaveError() {
        _uiState.update { it.copy(hasSaveError = false) }
    }

    /**
     * 플래시 카드 바뀔 때마다 호출
     * 평가 버튼마다 간격 텍스트 갱신
     * Again은 세션 내 재등장이라 "다시" 고정
     */
    private fun updateRatingLabels(card: Flashcard) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                hardLabel = calcLabel(card, ReviewRating.HARD, now),
                goodLabel = calcLabel(card, ReviewRating.GOOD, now),
                easyLabel = calcLabel(card, ReviewRating.EASY, now),
            )
        }
    }

    /**
     * 분 단위 interval을 파악할수 있는 문자열로 변환
     * ReviewSchedulePolicy이 변경되면 여기 숫자도 자동으로 변경
     */
    private fun calcLabel(card: Flashcard, rating: ReviewRating, now: Long): String {
        val result = schedulePolicy.calculateNextSchedule(card.schedule, rating, now)
        val minutes = result.interval
        return when {
            minutes < 60 -> "${minutes}분"
            minutes < 1440 -> "${minutes / 60}시간"
            minutes < 10080 -> "${minutes / 1440}일"
            minutes < 43200 -> "${minutes / 10080}주"
            else -> "${minutes / 43200}달"
        }
    }

    /**
     * 스피커 버튼 클릭 -> 텍스트 발음 재생
     */
    fun onPlayPronunciation() {
        val card = _uiState.value.currentCard ?: return
        val lang = _uiState.value.selectedLearningLanguage ?: return
        // 언어 설정 실패-> 재생 X
        if (!ttsController.setLanguage(lang)) return
        // 재생 끝나면 실행
        ttsController.speak(card.backText) {
            _uiState.update { it.copy(isSpeaking = false) }
        }
        // 재생 시작하면 실행
        _uiState.update { it.copy(isSpeaking = true) }
    }


    override fun onCleared() {
        super.onCleared()
        ttsController.stop()
    }
}
