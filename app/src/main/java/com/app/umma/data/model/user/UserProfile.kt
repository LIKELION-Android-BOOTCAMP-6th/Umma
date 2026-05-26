package com.app.umma.data.model.user

import com.app.umma.domain.model.user.UserProfile
import com.google.firebase.firestore.PropertyName

data class UserProfileDto(
    val uid: String,
    val nickname: String,
    val email: String,
    // 관심 주제 목록 - 첫 대화일 때 다이얼로그 창에서 선택
    val interestTopics: List<String>,
    // 초기 설정 완료 여부
    @get:PropertyName("isSetupCompleted")
    val isSetupCompleted: Boolean,
    // 저장 구조 버전.
    val schemaVersion: Int,
    // 최초 생성 시각.
    val createdAt: Long? = null,
)

fun UserProfile.toDto(): UserProfileDto{
    return UserProfileDto(
        uid = uid,
        nickname = nickname,
        email= email,
        interestTopics= interestTopics,
        isSetupCompleted = isSetupCompleted,
        schemaVersion = schema,
        createdAt = createdAt
    )
}
