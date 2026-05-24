package com.example.umma.presentation.srsstudy

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.flashcard.Flashcard
import com.example.umma.domain.model.flashcard.FlashcardSchedule
import com.example.umma.domain.model.flashcard.ReviewRating
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.usecase.flashcardreview.StartReviewSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SrsStudyViewModel @Inject constructor(
    private val startReviewSession: StartReviewSessionUseCase
) : ViewModel() {
    /**
     * 화면 진입 시 학습 초기화(학습 언어 고정) 시작
     * Loading / Error(다시시도) 상태 관리
     *
     */
    private val _uiState = MutableStateFlow(SrsStudyUiState())
    val uiState: StateFlow<SrsStudyUiState> = _uiState.asStateFlow()

    // 초기화 작업 추적용
    // 화면 빠르게 여러번 탭 해도 초기화 중복 실행 X
    private var initJob: Job? = null

    // 화면 열리면 호출
    fun onEnter() {
        // 이미 초기화 중이면 return
        if (initJob?.isActive == true) {
            Log.d("ummaDev", "onEnter 스킵 — 이미 실행 중")
            return
        }
        // 학습 언어 읽는 중
        initJob = viewModelScope.launch {
            Log.d("ummaDev", "onEnter 시작됨")
            _uiState.update {
                it.copy(isLoading = true, hasInitError = false)
            }
            //----- 화면 확인용 더미 코드 - 시작
            delay(500)
            val dummyLanguage = LangCode.EN
            val currentTime = System.currentTimeMillis()
            val dummyCards = listOf(
                Flashcard(
                    id = "dummy_1",
                    language = dummyLanguage,
                    frontText = "나는 매일 학교에 간다.",
                    hint = "I am go to school everyday.",
                    backText = "I go to school everyday.",
                    explanation = "현재 시제 규칙: 일반적인 습관이나 반복되는 일상표현에는 be동사(am) 없이 일반동사 현재형을 사용합니다.",
                    schedule = FlashcardSchedule(
                        interval = 1,
                        easeFactor = 2.5,
                        nextReviewAt = currentTime
                    ),
                    createdAt = currentTime,
                    updatedAt = currentTime
                ),
                Flashcard(
                    id = "dummy_2",
                    language = dummyLanguage,
                    frontText = "그녀는 사과를 좋아한다.",
                    hint = "She like apples.",
                    backText = "She likes apples.",
                    explanation = "3인칭 단수 현재형 주어(She) 뒤의 동사에는 반드시 -s를 붙여야 합니다.",
                    schedule = FlashcardSchedule(
                        interval = 1,
                        easeFactor = 2.5,
                        nextReviewAt = currentTime
                    ),
                    createdAt = currentTime,
                    updatedAt = currentTime
                )
            )

            // 현재 학습 언어 가져오는 UseCase
            startReviewSession().onSuccess { lang ->
                Log.d("ummaDev", "언어 가져오기 성공: $lang")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedLearningLanguage = dummyLanguage,
                        hasInitError = false,
                        cards = dummyCards,          // ⭕ 이거 넣어주면
                        currentCardIndex = 0,        // ⭕ 이거랑 조합해서 currentCard가 자동 계산됨!
                        isCardFlipped = false,
                        isDone = false
                    )
                }
                //----- 화면 확인용 더미데이터 - 끝
//                startReviewSession().onSuccess { lang ->
//                _uiState.update {
//                    it.copy(
//                        isLoading = false,
//                        selectedLearningLanguage = lang,
//                        hasInitError = false
//                    )
//                }
            }.onFailure { e ->
                Log.d("ummaDev", "언어 가져오기 실패: $e")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasInitError = true
                    )
                }
            }
        }
    }

    /**
     * "다시 시도" 버튼 클릭 시 실행
     */
    fun onRetry() {
        initJob = null
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
     * 선택된 평가 있을 때 -> 다음 카드로 이동
     * 평가 없으면 반응 X
     */
    fun onConfirmRating() {
        val rating = _uiState.value.selectedRating ?: return

        _uiState.update { state ->
            val nextIndex = state.currentCardIndex + 1
            val isDone = nextIndex >= state.totalCards
            state.copy(
                // 마지막 카드: 인덱스 유지, 아니라면 다음
                currentCardIndex = if (isDone) state.currentCardIndex else nextIndex,
                // 다음 카드 앞면으로
                isCardFlipped = false,
                isDone = isDone,
                selectedRating = null
            )
        }
    }
}

