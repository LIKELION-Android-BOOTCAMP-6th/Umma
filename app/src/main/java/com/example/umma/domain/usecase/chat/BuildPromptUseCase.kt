package com.example.umma.domain.usecase.chat

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.realtime.SessionTurn
import javax.inject.Inject

/**
 * 현재 언어 코드와 학습 상태([LangState])를 기반으로 AI 튜터의 시스템 프롬프트를 생성하는 유스케이스입니다.
 *
 * 이 프롬프트는 AI의 페르소나, 교육 스타일, 학생의 숙련도 정보를 포함하여
 * 개인화된 학습 경험을 제공하는 기초가 됩니다.
 */
class BuildPromptUseCase @Inject constructor() {
    /**
     * 프롬프트를 생성합니다.
     *
     * @param langCode 현재 학습 중인 언어
     * @param langState 해당 언어의 장기 학습 데이터 (어휘 수준 포함)
     * @param recentFullContext 저장 완료된 최근 대화 context
     * @return Gemini Live API에 전달할 시스템 지침 문자열
     */
    operator fun invoke(
        langCode: LangCode,
        langState: LangState?,
        recentFullContext: List<SessionTurn> = emptyList()
    ): String {
        val languageName = when(langCode) {
            LangCode.EN -> "English"
            LangCode.JA -> "Japanese"
            LangCode.KO -> "Korean"
            LangCode.ES -> "Spanish"
        }
        val level = langState?.external?.vocabularyLevel?.name ?: "Beginner"
        val recentContext = recentFullContext
            .takeLast(12)
            .joinToString(separator = "\n") { turn ->
                "${turn.role.name}: ${turn.text}"
            }

        val contextInstruction = if (recentContext.isBlank()) {
            "There is no confirmed conversation context yet."
        } else {
            """
            Use this confirmed recent conversation context when continuing:
            $recentContext
            """.trimIndent()
        }

        return """
            You are Umma, a friendly $languageName tutor. 
            The student's level is $level. 
            $contextInstruction
            Lead a natural conversation, and adapt your complexity to the student.
            Keep responses conversational and concise.
        """.trimIndent()
    }
}
