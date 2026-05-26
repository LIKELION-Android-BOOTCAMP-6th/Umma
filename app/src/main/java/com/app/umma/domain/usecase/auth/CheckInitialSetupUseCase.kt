package com.app.umma.domain.usecase.auth

import com.app.umma.domain.repository.UserProfileRepository
import javax.inject.Inject

class CheckInitialSetupUseCase @Inject constructor(
    private val repository: UserProfileRepository
) {
    suspend operator fun invoke(uid: String): Boolean {
        return repository.isNewUser(uid)
    }
}