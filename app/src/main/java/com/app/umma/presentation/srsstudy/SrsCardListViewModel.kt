package com.app.umma.presentation.srsstudy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.flashcardreview.GetFlashcardsUseCase
import com.app.umma.domain.usecase.flashcardreview.StartReviewSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SrsCardListViewModel @Inject constructor(
    private val getFlashCard: GetFlashcardsUseCase,
    private val getCurrentUserUid: GetCurrentUserUidUseCase,
    private val startReviewSession: StartReviewSessionUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SrsCardListUiState())
    val uiState: StateFlow<SrsCardListUiState> = _uiState.asStateFlow()

    /**
     * 화면 처음 진입 시 호출
     * 현재 언어로 저장된 카드 리스트 불러오기
     */
    fun onEnter() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasLoadError = false
                )
            }
            val uid = getCurrentUserUid.getCurrentUserUid()
            val lang = startReviewSession().getOrNull()

            // 둘중 하나라도 null 이면 fail
            if (uid == null || lang == null) {
                _uiState.update { it.copy(isLoading = false, hasLoadError = true) }
                return@launch
            }

            getFlashCard(uid, lang).onSuccess { list ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cards = list
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasLoadError = true
                    )
                }
            }
        }
    }
}