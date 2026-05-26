package com.app.umma.presentation.srsstudy

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.core.tts.TextToSpeechController
import com.app.umma.domain.model.flashcard.ReviewDecision
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.flashcardreview.ApplyReviewDecisionUseCase
import com.app.umma.domain.usecase.flashcardreview.ObserveReviewDeckUseCase
import com.app.umma.domain.usecase.flashcardreview.StartReviewSessionUseCase
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
    private val ttsController: TextToSpeechController
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
                it.copy(isLoading = true, hasInitError = false)
            }

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
                        hasInitError = true
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
                    is ReviewDeckState.Content -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasInitError = false,
                            cards = deckState.cards,
                            currentCardIndex = 0,
                            isCardFlipped = false,
                            isDone = false
                        )
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

    /** 카드 클릭 시 앞 뒤 전환*/
    fun onCardFlip() {
        _uiState.update { it.copy(isCardFlipped = !it.isCardFlipped) }
    }


    /**
     * 평가 버튼 클릭 -> 평가 값 저장
     */
    fun onRatingSelected(rating: ReviewRating) {
        _uiState.update { it.copy(selectedRating = rating) }
    }

    /**
     * 선택된 평가 있을 때 -> Room 저장 -> 다음 카드로 이동
     * 평가 없으면 클릭 X
     */
    fun onConfirmRating() {
        // 저장 중이면 return
        if (_uiState.value.isSaving) return
        // 선택 안했으면 null: 종료
        val rating = _uiState.value.selectedRating ?: return
        // 현재 카드 없으면 null: 종료
        val card = _uiState.value.currentCard ?: return
        val userId = getCurrentUserUid.getCurrentUserUid() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, hasSaveError = false) }

            val decision = ReviewDecision(
                flashcardId = card.id,
                rating = rating,
                reviewedAt = System.currentTimeMillis()
            )
            // SM-2 계산 + ROOM 저장
            applyReviewDecision(userId, card, decision).onSuccess {
                ttsController.stop()
                _uiState.update { state ->
                    val nextIndex = state.currentCardIndex + 1
                    val isDone = nextIndex >= state.totalCards
                    state.copy(
                        // 마지막 카드: 인덱스 유지, 아니라면 다음
                        currentCardIndex = if (isDone) state.currentCardIndex else nextIndex,
                        // 다음 카드 앞면으로
                        isCardFlipped = false,
                        isDone = isDone,
                        selectedRating = null,
                        isSaving = false,
                        isSpeaking = false
                    )
                }
            }.onFailure { e ->
                Log.d("ummaDev", "SrsStudyViewModel onConfirmRating - $e")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasSaveError = true
                    )
                }
            }
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
        ttsController.speak(card.backText)
        _uiState.update { it.copy(isSpeaking = true) }
    }


    override fun onCleared() {
        super.onCleared()
        ttsController.shutdown()
    }
}