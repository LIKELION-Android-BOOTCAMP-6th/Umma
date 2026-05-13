package com.example.umma.presentation.auth

/**
 * 로그인 화면의 UI 상태 관리
 * 로딩 상태, 인증 결과, 에러 메세지를 화면에 전달
 */
data class AuthUiState(
    /** 현재 로딩중인지 여부 */
    val isLoading: Boolean = false,
    /** 구글 인증의 세부 상태 (IDLE, SUCCESS, FAILED) */
    val googleState: GoogleAuthState = GoogleAuthState.IDLE,
    /** 화면에 표시할 에러 메세지 */
    val errorMessage: String? = null
)

/**
 * 구글 로그인 인증 상태
 */
enum class GoogleAuthState {
    /** 로그인 요청 전 */
    IDLE,

    /** Firebase 인증 완료 */
    SUCCESS,

    /** 로그인 또는 Firebase 인증 실패한 상태 */
    FAILED
}