package com.app.umma.presentation.srsstudy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.flashcardreview.DeleteFlashcardsUseCase
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
    private val startReviewSession: StartReviewSessionUseCase,
    private val deleteFlashcards: DeleteFlashcardsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SrsCardListUiState())
    val uiState: StateFlow<SrsCardListUiState> = _uiState.asStateFlow()

    // 삭제 시 카운트 재계산에 사용
    private var currentLanguage: LangCode? = null

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
            currentLanguage = lang

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

    /**
     * 카드 한 장 선택/해제 토글
     */
    fun toggleSelection(cardId: String) {
        _uiState.update { state ->
            val newSet = state.selectedIds.toMutableSet()
            if (cardId in newSet) newSet.remove(cardId) else newSet.add(cardId)
            state.copy(selectedIds = newSet)
        }
    }

    /**
     * 전체 선택 / 전체 해제 토글
     */
    fun toggleSelectAll() {
        _uiState.update { state ->
            val newSet = if (state.areAllSelected) {
                emptySet()
            } else {
                state.cards.map { it.id }.toSet()
            }
            state.copy(selectedIds = newSet)
        }
    }

    /**
     * 선택된 카드 삭제
     */
    fun deleteSelected() {
        // 중복 삭제 방지
        if (_uiState.value.isDeleting) return
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, message = null) }

            val uid = getCurrentUserUid.getCurrentUserUid()
            val lang = currentLanguage
            if (uid == null || lang == null) {
                _uiState.update { it.copy(isDeleting = false, message = "삭제에 실패했습니다.") }
                return@launch
            }

            deleteFlashcards(uid, lang, ids).onSuccess {
                // 삭제된 카드를 목록에서 제거하고 선택 초기화
                _uiState.update { state ->
                    state.copy(
                        isDeleting = false,
                        cards = state.cards.filterNot { it.id in ids },
                        selectedIds = emptySet()
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isDeleting = false, message = "삭제에 실패했습니다.") }
            }
        }
    }

    /**
     * 메세지 초기화
     */
    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 정렬 기준 변경
     */
    fun setSortOrder(order: SrsCardSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }
}