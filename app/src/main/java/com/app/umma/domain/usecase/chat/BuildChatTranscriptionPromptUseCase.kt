package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.LangCode
import javax.inject.Inject

/**
 * Realtime input transcription 전용 prompt를 만든다.
 *
 * Chat system prompt는 AI의 대화 행동을 정하지만, 이 prompt는 사용자의 음성을 어떻게
 * 받아 적을지만 다룬다. 두 책임을 분리해야 STT 보정이 AI 말투나 대화 정책을 오염시키지 않는다.
 */
class BuildChatTranscriptionPromptUseCase @Inject constructor() {
    operator fun invoke(
        primaryLang: LangCode,
        selectedLang: LangCode
    ): String {
        val primaryLanguageName = languageName(primaryLang)
        val selectedLanguageName = languageName(selectedLang)
        val languageList = mixedLanguageNames(primaryLanguageName, selectedLanguageName)

        return """
            The user is speaking in a casual voice chat.
            The user's primary language is $primaryLanguageName.
            The target learning language is $selectedLanguageName.
            English words may also appear naturally in casual speech.
            A single utterance may contain $languageList.
            Do not force the whole utterance into one language.
            Keep each segment in the language it was spoken or clearly intended.
            Do not translate between languages.
            Do not normalize the whole sentence into one language.
            The target language may appear as short words, fragments, or imperfect pronunciation.
            If a target-language word is clearly intended, transcribe the intended target-language word.
            Common English app, product, and technical words should remain in English when clearly intended, even if pronounced with a $primaryLanguageName accent.
            Keep $primaryLanguageName fillers as $primaryLanguageName fillers unless another language is clearly spoken.
            If uncertain, keep the closest likely transcript and do not invent unrelated words.
        """.trimIndent()
    }

    /**
     * Logcat/review 문서에는 STT prompt 본문 대신 revision과 언어쌍만 남긴다.
     */
    fun buildTrace(
        primaryLang: LangCode,
        selectedLang: LangCode
    ): String {
        val languageCodes = listOf(primaryLang.code, selectedLang.code, LangCode.EN.code)
            .distinct()
            .joinToString(separator = "+")
        return "transcription={revision=$PROMPT_REVISION,languages=$languageCodes}"
    }

    private fun mixedLanguageNames(
        primaryLanguageName: String,
        selectedLanguageName: String
    ): String {
        // 영어가 이미 기준/학습 언어면 중복해서 세 번째 언어처럼 강조하지 않는다.
        return listOf(primaryLanguageName, selectedLanguageName, "English")
            .distinct()
            .joinToString(separator = ", ")
    }

    private fun languageName(langCode: LangCode): String {
        return when (langCode) {
            LangCode.EN -> "English"
            LangCode.JA -> "Japanese"
            LangCode.KO -> "Korean"
            LangCode.DE -> "German"
            LangCode.UNKNOWN -> "English"
        }
    }

    private companion object {
        // STT prompt만의 실험 식별자다. Chat system prompt revision과 독립적으로 추적한다.
        private const val PROMPT_REVISION = "stt_prompt_v2"
    }
}
