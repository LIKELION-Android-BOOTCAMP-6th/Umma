package com.app.umma.domain.usecase.user

import com.app.umma.domain.model.user.UserProfile
import com.app.umma.domain.repository.UserProfileRepository
import javax.inject.Inject


/**
 * 사용자 프로필을 Firestore에서 가져오는 UseCase
 */
class GetUserProfileUseCase @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) {
    suspend operator fun invoke(uid: String): UserProfile? {
        return userProfileRepository.getUserProfile(uid)
    }
}