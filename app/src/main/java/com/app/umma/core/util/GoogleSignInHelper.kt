package com.app.umma.core.util

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * 안드로이드 Credential Manager를 사용하여 구글 ID 토큰을 가져오는 유틸리티 클래스
 */
object GoogleSignInHelper {

    /**
     * 구글 로그인 바텀시트를 표시하고 선택된 계정의 ID 토큰을 가져옴
     * @param context Activity 기반의 Context
     * @param webClientId Firebase 콘솔에서 발급받은 웹 클라이언트 ID
     * @return 성공 시 구글 ID 토큰(String) 반환
     */
    suspend fun getGoogleIdToken(
        context: Context,
        webClientId: String
    ): String {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result: GetCredentialResponse = credentialManager.getCredential(
                context = context,
                request = request
            )
            val credential = result.credential

            if (credential is CustomCredential && 
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                return googleIdTokenCredential.idToken
            } else {
                throw Exception("지원하지 않는 크리덴셜 타입입니다.")
            }
        } catch (e: Exception) {
            throw e
        }
    }
}