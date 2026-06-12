package com.app.umma.data.repository

import com.app.umma.data.model.learningstate.toDto
import com.app.umma.data.model.user.toDto
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.user.UserProfile
import com.app.umma.domain.repository.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserProfileRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : UserProfileRepository {
    /**
     * 최초 로그인 사용자 여부 확인
     * 판단 기준 : users/{uid} 문서 없음 또는 isSetupCompleted가 true가 아님
     */
    override suspend fun isNewUser(uid: String): Boolean {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            !document.exists() || document.getBoolean("isSetupCompleted") != true
        } catch (_: Exception) {
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

        // 프로필 정보 저장 (claimLoginSession이 먼저 기록한 activeSession 등 다른 필드를 보존하기 위해 merge)
        batch.set(userRef, profile.toDto(), SetOptions.merge())

        // 언어 설정 저장
        val prefRef = userRef.collection("user_learning_preference").document("current")
        batch.set(prefRef, langPref.toDto())

        // 언어 상태 저장
        val stateRef = userRef.collection("language_states").document(initialLangState.lang.code)
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

    /**
     * 사용자 프로필 전체 조회
     * @param uid 사용자 UID
     * @return 조회한 유저의 문서, 문서가 없다면 null
     */
    override suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            if (!document.exists()) return null
            UserProfile(
                uid = document.getString("uid") ?: uid,
                nickname = document.getString("nickname") ?: "",

                email = document.getString("email") ?: "",
                // Firestore SDK는 raw List<*>로 돌려주므로 문자열 항목만 안전하게 복원한다.
                interestTopics = (document.get("interestTopics") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList(),
                isSetupCompleted = document.getBoolean("isSetupCompleted") ?: false,
                schema = (document.getLong("schemaVersion") ?: 1L).toInt(),
                createdAt = document.getLong("createdAt")
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * @param uid 사용자 UID
     * @param topics 문자열 리스트 (예: "Travel", "FOOD")
     */
    override suspend fun saveInterestTopics(
        uid: String,
        topics: List<String>
    ): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(uid)
            .update("interestTopics", topics)
            .await()
    }
}
