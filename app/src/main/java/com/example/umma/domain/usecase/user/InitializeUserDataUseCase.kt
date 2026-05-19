package com.example.umma.domain.usecase.user

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.model.user.UserProfile
import com.example.umma.domain.repository.UserProfileRepository
import javax.inject.Inject

/**
 * AUTH-004 : 구글 로그인 직후, 닉네임/언어 등 초기 설정
 */
class InitializeUserDataUseCase @Inject constructor(
    private val repository: UserProfileRepository
) {
    suspend operator fun invoke(
        uid: String,
        email: String,
        nickname: String,
        nativeLang: LangCode,
        primaryLang: LangCode,
        topics: List<String>
    ): Result<Unit> {
        // ***** 기기 시간 사용하는 상태
        val currentTime = System.currentTimeMillis()
        // 초기 데이터 기본값 생성(팩토리 메서드)
        val profile = UserProfile.initial(uid, nickname, email, topics)
        val langPref = UserLangPref.initial(nativeLang, primaryLang)
        val langState = LangState.initial(primaryLang, currentTime)

        val dashSummary = DashSummary.initial(primaryLang)
        val sessionSummary = SessionSummary.initial(primaryLang)
        val flashcardSummary = FlashcardSummary.initial(primaryLang)
        // Repository 에 전달하여 Firestore 에 Batch 저장 실행
        return repository.saveInitialSetup(
            profile = profile,
            langPref = langPref,
            initialLangState = langState,
            dashSummary = dashSummary,
            sessionSummary = sessionSummary,
            flashcardSummary = flashcardSummary
        )
    }
}
