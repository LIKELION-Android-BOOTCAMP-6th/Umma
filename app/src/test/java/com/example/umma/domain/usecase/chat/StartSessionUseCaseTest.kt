package com.example.umma.domain.usecase.chat

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.repository.ChatRepository
import com.example.umma.domain.repository.LearningStateRepo
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StartSessionUseCase]에 대한 단위 테스트 클래스입니다.
 */
class StartSessionUseCaseTest {

    private val chatRepository: ChatRepository = mockk()
    private val learningStateRepo: LearningStateRepo = mockk()
    private val buildPromptUseCase: BuildPromptUseCase = mockk()

    private val startSessionUseCase = StartSessionUseCase(
        repository = chatRepository,
        learningStateRepo = learningStateRepo,
        buildPromptUseCase = buildPromptUseCase
    )

    /**
     * 사용자 설정이 존재하는 경우, 프롬프트를 빌드하고 세션을 성공적으로 시작하는지 테스트합니다.
     */
    @Test
    fun `invoke should return success when user preferences exist`() = runTest {
        // Given
        val selectedLang = LangCode.EN
        val userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = selectedLang)
        val expectedPrompt = "Mocked Prompt"
        val expectedSessionId = "session_123"

        every { learningStateRepo.observeUserPref() } returns flowOf(userPref)
        every { learningStateRepo.observeLangState(selectedLang) } returns flowOf(null)
        every { buildPromptUseCase(selectedLang, any()) } returns expectedPrompt
        coEvery { chatRepository.startSession(selectedLang, expectedPrompt) } returns Result.success(expectedSessionId)

        // When
        val result = startSessionUseCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedSessionId, result.getOrNull())
    }

    /**
     * 사용자 설정이 존재하지 않는 경우(null), failure를 반환하는지 테스트합니다.
     */
    @Test
    fun `invoke should return failure when user preferences are missing`() = runTest {
        // Given
        every { learningStateRepo.observeUserPref() } returns flowOf(null)

        // When
        val result = startSessionUseCase()

        // Then
        assertTrue(result.isFailure)
        assertEquals("User preferences not found", result.exceptionOrNull()?.message)
    }

    /**
     * Repository에서 세션 시작이 실패한 경우, 해당 에러를 그대로 반환하는지 테스트합니다.
     */
    @Test
    fun `invoke should return failure when repository fails to start session`() = runTest {
        // Given
        val selectedLang = LangCode.EN
        val userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = selectedLang)
        val errorMsg = "Network Error"

        every { learningStateRepo.observeUserPref() } returns flowOf(userPref)
        every { learningStateRepo.observeLangState(selectedLang) } returns flowOf(null)
        every { buildPromptUseCase(selectedLang, any()) } returns "Some Prompt"
        coEvery { chatRepository.startSession(any(), any()) } returns Result.failure(Exception(errorMsg))

        // When
        val result = startSessionUseCase()

        // Then
        assertTrue(result.isFailure)
        assertEquals(errorMsg, result.exceptionOrNull()?.message)
    }
}
