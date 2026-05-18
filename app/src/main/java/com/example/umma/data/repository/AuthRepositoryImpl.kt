package com.example.umma.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.umma.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
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