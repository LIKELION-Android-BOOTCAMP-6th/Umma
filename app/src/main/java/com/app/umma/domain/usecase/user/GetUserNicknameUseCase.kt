package com.app.umma.domain.usecase.user

import com.app.umma.domain.repository.UserProfileRepository
import javax.inject.Inject

/**
 * 사용자 프로필에서 닉네임만 조회하는 UseCase.
 */
class GetUserNicknameUseCase @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) {
    suspend operator fun invoke(uid: String): String? {
        return userProfileRepository
            .getUserProfile(uid)
            ?.nickname
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
