package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildChatTranscriptionPromptUseCaseTest {
    private val useCase = BuildChatTranscriptionPromptUseCase()

    @Test
    fun `japanese chat transcription prompt allows primary target and english mixing`() {
        val prompt = useCase(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )
        val trace = useCase.buildTrace(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        // STT prompt는 대화 지시가 아니라 전사 힌트만 담아야 자막/교정 원문 품질만 좁게 개선한다.
        assertTrue(prompt.contains("primary language is Korean"))
        assertTrue(prompt.contains("target learning language is Japanese"))
        assertTrue(prompt.contains("English words may also appear naturally"))
        assertTrue(prompt.contains("mix Korean, Japanese, English within the same sentence"))
        assertTrue(prompt.contains("Do not translate between languages"))
        assertTrue(prompt.contains("imperfect pronunciation"))
        assertTrue(prompt.contains("If uncertain"))
        assertTrue(trace.contains("transcription={revision=stt_prompt_v1,languages=ko+ja+en}"))
        assertFalse(prompt.contains("answer"))
        assertFalse(prompt.contains("teacher"))
    }

    @Test
    fun `english target transcription trace does not duplicate english`() {
        val prompt = useCase(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )
        val trace = useCase.buildTrace(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        // 영어가 이미 학습언어인 세션에서는 trace와 혼합 언어 목록이 영어를 중복 표시하지 않는다.
        assertTrue(prompt.contains("mix Korean, English within the same sentence"))
        assertTrue(trace.contains("languages=ko+en"))
        assertFalse(trace.contains("ko+en+en"))
    }
}
