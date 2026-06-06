package com.app.umma.data.repository.chatconversation

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.learningstate.TurnSpeaker
import javax.inject.Inject

/**
 * Chat 세션을 Gemini conversation evidence 분석 prompt로 변환합니다.
 *
 * 실패 문구 필터가 아니라 세션 흐름 근거를 보게 하기 위해, 확정 저장된 final turn만 전달한다.
 * prompt trace, 신고 메모, raw metric은 입력에서 제외해 프롬프트 튜닝 도구와 능력 측정을 분리한다.
 */
class ChatConversationEvidencePromptBuilder @Inject constructor() {
    fun build(session: ChatConversationAnalysisSession): String {
        val turns = session.turns
            .filter { it.text.isNotBlank() }
            .takeLast(MAX_TURNS)
            .joinToString("\n") { turn ->
                val speaker = when (turn.speaker) {
                    TurnSpeaker.USER -> "USER"
                    TurnSpeaker.AI -> "AI"
                }
                "- $speaker: ${turn.text.trim()}"
            }

        return """
            You analyze a language-learning voice chat session.
            Return JSON only.

            Goal:
            Decide conversation ability evidence, not the final product band.
            The app will calculate ConversationAbilityBand using its own policy.

            Rules:
            - Do not grade grammar like a correction feature.
            - Do not decide based on a single keyword.
            - Focus on whether the learner could keep the target-language conversation going.
            - If Korean/support language was needed to continue, reflect that in supportRequiredToContinue.
            - debugRecommendedBand is only a reference guess and will not be directly applied.

            Output schema:
            {
              "conversationSustainability": "RequiresSupport|SupportedWithHints|SustainedSimple|SustainedNatural",
              "supportRequiredToContinue": "High|Moderate|Low|None",
              "userContributionLevel": "Minimal|WordsOrFragments|ShortPhrases|SimpleSentences|ConnectedTurns",
              "responseDifficultyFit": "TooHard|SlightlyHard|Fits|TooEasy|Unknown",
              "confidence": "Low|Medium|High",
              "reasonSummary": "short Korean summary, max 240 chars",
              "debugRecommendedBand": "IntentOnly|PhraseEmerging|SimpleSentence|BasicConversation|ConnectedExpression|NuanceControl"
            }

            Session:
            language=${session.selectedLang.code}

            Final turns:
            $turns
        """.trimIndent()
    }

    private companion object {
        const val MAX_TURNS = 30
    }
}
