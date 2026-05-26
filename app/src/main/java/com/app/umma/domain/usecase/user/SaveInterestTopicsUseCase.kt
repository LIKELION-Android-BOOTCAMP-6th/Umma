package com.app.umma.domain.usecase.user

import com.app.umma.domain.repository.UserProfileRepository
import javax.inject.Inject

/**
 * 사용자 관심 주제 5개를 Firestore에 저장하는 UseCase
 * 정확히 5개 선택 검증은 UI에서 처리
 */
class SaveInterestTopicsUseCase @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) {
    suspend operator fun invoke(uid: String, topics: List<String>): Result<Unit> {
        return userProfileRepository.saveInterestTopics(uid, topics)
    }
}