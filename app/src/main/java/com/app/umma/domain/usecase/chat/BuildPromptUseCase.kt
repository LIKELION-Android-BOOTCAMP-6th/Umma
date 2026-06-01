package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.realtime.SessionTurn
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
     * @param recentTopicSummaries 최근 5개 세션 주제 요약 목록. 존재 시 AI 에 전달해 대화 연속성을 높인다. (#162-C)
     *   현재 호출처 wiring 은 후속 PR 에서 완성된다 — 이번 PR 에서는 시그니처와 주입 hook 만 추가.
     * @return OpenAI Realtime 세션에 전달할 시스템 지침 문자열
     */
    operator fun invoke(
        langCode: LangCode,
        langState: LangState?,
        recentFullContext: List<SessionTurn> = emptyList(),
        recentTopicSummaries: List<String> = emptyList()
    ): String {
        val languageName = when(langCode) {
            LangCode.EN -> "English"
            LangCode.JA -> "Japanese"
            LangCode.KO -> "Korean"
            LangCode.DE -> "German"
            // LangCode.UNKNOWN 은 정상 흐름에서 여기로 도달하면 안 된다.
            //   상위 layer(DASH-006 AC 9 fallback 등)에서 primaryLang 으로 교체됐어야 함.
            //   여기까지 왔다면 fallback 누락 → AI 프롬프트는 안전 default(English) 로 처리.
            //   (UNKNOWN 사용 규약은 LearningCoreModels.kt 의 LangCode 주석 참고)
            LangCode.UNKNOWN -> "English"
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

        // 최근 세션 주제 요약이 있으면 AI 가 대화 방향을 자연스럽게 이어갈 수 있도록 주입한다.
        // 호출처가 emptyList() 를 전달할 경우 이 블록은 프롬프트에 포함되지 않는다. (#162-C)
        val topicsInstruction = if (recentTopicSummaries.isNotEmpty()) {
            val topicsList = recentTopicSummaries.joinToString(separator = "\n") { "- $it" }
            "\nRecent topics the student has discussed:\n$topicsList"
        } else {
            ""
        }

        return """
            You are Umma, a friendly $languageName tutor.
            The student's level is $level.
            $contextInstruction$topicsInstruction
            Lead a natural conversation, and adapt your complexity to the student.
            Keep responses conversational and concise.
        """.trimIndent()
    }
}
