package com.example.umma.data.repository

import com.example.umma.data.model.learningstate.toDto
import com.example.umma.data.model.user.toDto
import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.model.user.UserProfile
import com.example.umma.domain.repository.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserProfileRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : UserProfileRepository {
    /**
     * 최초 로그인 사용자 여부 확인
     * 판단 기준 : users/{uid} 존재 여부
     */
    override suspend fun isNewUser(uid: String): Boolean {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            !document.exists() || document.getBoolean("isSetupCompleted") == false
        } catch (e: Exception) {
            true
        }
    }

    /** AUTH-004: 초기 설정 데이터 저장 */
    override suspend fun saveInitialSetup(
        profile: UserProfile,
        langPref: UserLangPref,
        initialLangState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit> = runCatching {
        val batch = firestore.batch()
        val userRef = firestore.collection("users").document(profile.uid)

        // 프로필 정보 저장
        batch.set(userRef, profile.toDto())

        // 언어 설정 저장
        val prefRef = userRef.collection("user_learning_preference").document("current")
        batch.set(prefRef, langPref.toDto())

        // 언어 상태 저장
        val stateRef = userRef.collection("learning_states").document(initialLangState.lang.code)
        batch.set(stateRef, initialLangState.toDto())

        // 대시보드 요약 저장
        val dashRef = userRef.collection("dashboard_summaries").document(dashSummary.lang.code)
        batch.set(dashRef, dashSummary.toDto())

        // 세션 요약 저장
        val sessionRef = userRef.collection("session_summaries").document(sessionSummary.lang.code)
        batch.set(sessionRef, sessionSummary.toDto())

        // 플래시카드 요약 저장
        val cardRef = userRef.collection("flashcard_summaries").document(flashcardSummary.lang.code)
        batch.set(cardRef, flashcardSummary.toDto())

        // 한번에 저장 하나라도 하나라도 실패 시 전체 실패
        batch.commit().await()
    }
}