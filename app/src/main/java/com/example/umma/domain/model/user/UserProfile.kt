package com.example.umma.domain.model.user

import com.google.firebase.firestore.PropertyName

/**
 * 신규 사용자 프로필 데이터 구조
 * 최초 로그인 시 생성
 */
data class UserProfile(
    val uid: String,
    val nickname: String,
    val email: String,
    // 관심 주제 목록 - 첫 대화일 때 다이얼로그 창에서 선택
    val interestTopics: List<String>,
    // 초기 설정 완료 여부
    @get:PropertyName("isSetupCompleted")
    val isSetupCompleted: Boolean,
    // 저장 구조 버전.
    val schema: Int = SCHEMA,
    // 최초 생성 시각.
    val createdAt: Long? = null,
) {
    companion object {
        const val SCHEMA = 1

        // 신규 사용자 최초 설정값.
        fun initial(
            uid: String,
            nickname: String,
            email: String,
            interestTopics: List<String>
        )
                : UserProfile {
            return UserProfile(
                uid = uid,
                nickname = nickname,
                email = email,
                interestTopics = interestTopics,
                isSetupCompleted = true,
                schema = SCHEMA,
                // ***** 기기 시간 사용하는 상태
                createdAt = System.currentTimeMillis()
            )
        }
    }
}

