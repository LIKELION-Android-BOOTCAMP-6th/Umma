package com.app.umma.domain.usecase.user

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.user.UserProfile
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.UserProfileRepository
import javax.inject.Inject

/**
 * AUTH-004 : 구글 로그인 직후, local/remote 닉네임/언어 등 초기 설정 저장
 */
class InitializeUserDataUseCase @Inject constructor(
    private val repository: UserProfileRepository,
    private val learningStateRepo: LearningStateRepo
) {
    suspend operator fun invoke(
        uid: String,
        email: String,
        nickname: String,
        primaryLang: LangCode,
        selectedLang: LangCode,
        topics: List<String>
    ): Result<Unit> {
        // ***** 기기 시간 사용하는 상태
        val currentTime = System.currentTimeMillis()
        // 초기 데이터 기본값 생성(팩토리 메서드)
        val profile = UserProfile.initial(uid, nickname, email, topics)
        val langPref = UserLangPref.initial(
            primaryLang = primaryLang,
            selectedLang = selectedLang
        )
        val langState = LangState.initial(selectedLang, currentTime)

        val dashSummary = DashSummary.initial(selectedLang)
        val sessionSummary = SessionSummary.initial(selectedLang)
        val flashcardSummary = FlashcardSummary.initial(selectedLang)
        // Repository 에 전달하여 Firestore 에 Batch 저장 실행
        val remoteResult = repository.saveInitialSetup(
            profile = profile,
            langPref = langPref,
            initialLangState = langState,
            dashSummary = dashSummary,
            sessionSummary = sessionSummary,
            flashcardSummary = flashcardSummary
        )

        remoteResult.getOrElse { error ->
            return Result.failure(error)
        }

        // Local 저장
        return learningStateRepo.createInitial(
            userUid = uid,
            userPref = langPref,
            langState = langState,
            dashSummary = dashSummary,
            sessionSummary = sessionSummary,
            flashcardSummary = flashcardSummary
        )
    }
}
