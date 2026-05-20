package com.example.umma.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 인증 관련 데이터 처리를 담당하는 레포지토리 인터페이스
 */
interface AuthRepository {
    /**
     * 현재 로그인된 유저의 UID를 관찰 가능한 Flow로 제공.
     * 로그아웃 상태일 경우 null을 반환.
     */
    val currentUserUid: Flow<String?>

    /**
     * 구글 ID 토큰을 사용하여 Firebase 인증을 수행
     * @param idToken Credential Manager에서 획득한 구글 ID 토큰
     * @return 성공 시 유저 UID, 실패 시 Result 에러 반환
     */
    suspend fun signInWithGoogle(idToken: String): Result<String>

    /**
     * 동기, 즉시 조회
     */
    fun getCurrentUserUid(): String?

    /**
     * 동기, 즉시 조회
     */
    fun getCurrentUserEmail(): String?

    /**
     * 로그아웃을 수행
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Firebase 서버에 현재 세션 유효 재확인
     * AUTH-002
     */
    suspend fun hasValidSession(): Result<Boolean>
}