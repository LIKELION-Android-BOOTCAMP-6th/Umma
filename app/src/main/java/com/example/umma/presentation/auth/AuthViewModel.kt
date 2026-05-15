package com.example.umma.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.example.umma.domain.usecase.auth.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AuthViewModel @Inject constructor(
    /**
     * 로그인 실행용
     */
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    /**
     * 로그인 상태 확인용
     */
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeAuthStatus()
    }

    /**
     * 현재 로그인된 상태인지 감시
     */
    private fun observeAuthStatus() {
        viewModelScope.launch {
            getCurrentUserUidUseCase().collect() { uid ->
                if (uid != null) {
                    _uiState.update {
                        it.copy(googleState = GoogleAuthState.SUCCESS)
                    }
                }
            }
        }
    }

    /**
     * 구글 로그인 시도 함수
     * 로그인 시작 시 로딩 상태로 변경하여 중복 실행 방지 isLoading = true
     * useCase 실행 후 결과로 ui 상태 업데이트
     */
    fun signInWithGoogle(idToken: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            /** 로그인 시작 로딩... */
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
            signInWithGoogleUseCase(idToken).onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        googleState = GoogleAuthState.SUCCESS
                    )
                }
            }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            googleState = GoogleAuthState.FAILED,
                            errorMessage = "google 로그인 시도 중 에러 발생"
                        )
                    }
                }
        }
    }

    fun updateErrorMessage(message: String?) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                googleState = GoogleAuthState.FAILED
            )
        }
    }
}