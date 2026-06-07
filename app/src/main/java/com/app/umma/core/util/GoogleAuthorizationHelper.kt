package com.app.umma.core.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google authorization / revoke 흐름을 담당한다.
 */
class GoogleAuthorizationHelper(
    context: Context
) {
    private val authorizationClient = Identity.getAuthorizationClient(context)

    /**
     * 회원탈퇴 직전 현재 계정에 대한 Google authorization 상태를 확인한다.
     *
     * @param email 현재 Firebase 로그인에 연결된 Google 계정 이메일
     */
    suspend fun authorizeForDeleteAccount(email: String): DeleteAccountAuthorizationResult {
        val account = email.toGoogleAccount()
        val result = authorizationClient.authorize(
            AuthorizationRequest.builder()
                .setAccount(account)
                .setRequestedScopes(DELETE_ACCOUNT_SCOPES)
                .build()
        ).await()

        return if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: throw IllegalStateException("Google authorization resolution is missing.")
            DeleteAccountAuthorizationResult.ResolutionRequired(
                intentSender = pendingIntent.intentSender
            )
        } else {
            DeleteAccountAuthorizationResult.Authorized(account = account)
        }
    }

    /**
     * authorization resolution 결과를 읽고 revoke 대상 계정을 반환한다.
     *
     * @param data StartIntentSenderForResult 결과 intent
     * @param email revoke 대상 계정 이메일
     */
    fun consumeAuthorizationResolutionResult(
        data: Intent?,
        email: String
    ): Account {
        authorizationClient.getAuthorizationResultFromIntent(data)
        return email.toGoogleAccount()
    }

    /**
     * 현재 앱에 부여된 Google 접근 권한을 해제한다.
     *
     * @param account revoke 대상 계정
     */
    suspend fun revokeDeleteAccountAccess(account: Account) {
        authorizationClient.revokeAccess(
            RevokeAccessRequest.builder()
                .setAccount(account)
                .setScopes(DELETE_ACCOUNT_SCOPES)
                .build()
        ).await()
    }

    private fun String.toGoogleAccount(): Account = Account(this, GOOGLE_ACCOUNT_TYPE)

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"

        val DELETE_ACCOUNT_SCOPES = listOf(
            Scope("openid"),
            Scope("email"),
            Scope("profile")
        )
    }
}

/**
 * 회원탈퇴 전 authorization 확인 결과다.
 */
sealed interface DeleteAccountAuthorizationResult {
    /**
     * 추가 사용자 확인 없이 revoke를 진행할 수 있다.
     */
    data class Authorized(
        val account: Account
    ) : DeleteAccountAuthorizationResult

    /**
     * 사용자 확인 UI를 먼저 띄워야 한다.
     */
    data class ResolutionRequired(
        val intentSender: IntentSender
    ) : DeleteAccountAuthorizationResult
}
