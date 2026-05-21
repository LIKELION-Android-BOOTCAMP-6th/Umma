package com.example.umma.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.umma.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase Authentication을 사용하는 AuthRepository 구현체입니다.
 */
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @param:ApplicationContext private val context: Context
) : AuthRepository {

    override val currentUserUid: Flow<String?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        firebaseAuth.addAuthStateListener(authStateListener)

        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user

            if (user != null) {
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Firebase User is null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Firebase 세션, Google 자격증명 해제
     *
     * Firebase 인증 세션 종료, CredentialManager의 Google 자격 정보 초기화
     */
    override suspend fun signOut(): Result<Unit> {
        return try {
            // Firebase 세션 해제
            firebaseAuth.signOut()
            try {
                val credentialManager = CredentialManager.create(context)
                // credentialManager 에 저장된 Google 로그인 자격 정보 초기화
                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest()
                )
            } catch (e: Exception) {
                // CredentialManager의 정리 실패는 앱 인증 상태에 영향 주지 않으나 Warning 로그는 남김
                Log.w("Auth", "AuthRepositoryImpl.kt signOut ", e)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Firebase 로그아웃 실패
            Result.failure(e)
        }
    }

    /**
     * Firebase 서버에 현재 세션 유효 확인
     * getCurrentUserUid() 는 기기 로컬 캐시를 읽어서
     * 서버에서 계정이 삭제되거나 토큰이 만료되어도 로그인 정보가 남아있을 수 있는 문제
     * reload() 는 Firebase 서버에 실제로 요청을 보내 계정 유효성 검증
     *
     * return
     * - success(true) : 세션 유효
     * - success(false) : 세션 무효 (토큰 만료, 계정 삭제...) -> 로그아웃 처리
     * - failure : 네트워크 X, Firebase 서버 오류 -> 사용자의 재시도 필요
     */
    override suspend fun hasValidSession(): Result<Boolean> {
        // checkSession()에서 로그인 상태에서만 해당 함수 호출하기 때문에 !! 로 막는다 보다는
        // 혹시 모를 상황에 안전 선택
        val currentUser = firebaseAuth.currentUser ?: return Result.success(false)

        return try {
            // Firebase 서버에 계정 유효성 확인
            currentUser.reload().await()
            Result.success(true)
        } catch (e: FirebaseAuthInvalidUserException) {
            // 계정이 삭제되거나 토큰이 만료 -> 로컬 세션 제거
            firebaseAuth.signOut()
            Result.success(false)
        } catch (e: FirebaseNetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 동기, 즉시 조회
     */
    override fun getCurrentUserUid(): String? {
        return firebaseAuth.currentUser?.uid
    }

    /**
     * 동기, 즉시 조회
     */
    override fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }
}