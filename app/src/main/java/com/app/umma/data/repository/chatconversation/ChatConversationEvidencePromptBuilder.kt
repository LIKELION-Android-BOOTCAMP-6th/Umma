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

            Core evaluation rule:
            - Evaluate only the learner's ability in the target language: ${session.selectedLang.code}.
            - Support-language or non-target-language speech may reveal the learner's intent, but it must not increase target-language ability ratings.
            - A conversation that continued mainly because the learner used a support language is not strong target-language evidence.
            - Separate the learner's target-language ability from the AI's help. If the AI carried the conversation with support-language explanations, hints, or very easy prompts, reflect that as support being required.
            - Do not count target-language words or sentences as independent ability when they were copied, repeated, lightly recombined, or directly scaffolded from the AI's immediately previous turns.
            - A prompted phrase such as repeating a word, echoing a pattern, or filling one known word into an AI-provided frame is not SimpleSentence production.
            - Judge all evidence fields using the same target-language-only standard.

            Rules:
            - Do not grade grammar like a correction feature.
            - Do not decide based on a single keyword.
            - Focus on whether the learner could understand and contribute in the target language across the session.
            - Do not treat fluent Korean/support-language speech as fluent target-language conversation.
            - If support language was needed to understand, recover, or continue, reflect that in supportLanguageDependence.
            - If the learner mostly used support language and produced little target language, set targetLanguageProduction low even if the overall conversation felt natural.
            - One isolated target-language sentence is not enough for PhraseEmerging or SimpleSentence when the rest of the session depended on support language or AI scaffolding.
            - Rate targetLanguageComprehension as SimpleSentence only when the learner repeatedly responded correctly to target-language sentence meaning without needing Korean/support-language explanation.
            - Rate targetLanguageProduction as SimpleSentences only when the learner independently produced more than one simple target-language sentence across the session, not just one scaffolded or repeated sentence.
            - If the learner's best target-language sentence appears after the AI modeled the same words or structure, treat it as WordsOrFragments or ShortPhrases unless later turns show independent transfer.
            - If confidence is Low, conversationSustainability is RequiresSupport, or both supportLanguageDependence and aiScaffoldingDependence are High, debugRecommendedBand should be IntentOnly.
            - PhraseEmerging requires repeated target-language words or short phrases across turns, with supportLanguageDependence and aiScaffoldingDependence no higher than Medium, and conversationSustainability not RequiresSupport.
            - If supportLanguageDependence or aiScaffoldingDependence is High, do not recommend PhraseEmerging or above unless the learner later shows independent target-language transfer without that support.
            - When support language or AI scaffolding is High, responseDifficultyFit should normally be TooHard or Unknown, not Fits.
            - Do not describe one isolated scaffolded sentence as independent production.
            - If target-language evidence is too short, mixed, or ambiguous, use Low confidence.
            - debugRecommendedBand is only a reference guess and will not be directly applied.

            Evidence meanings:
            - targetLanguageComprehension: how well the learner understood and responded to the AI's target-language speech without support-language explanation.
            - targetLanguageProduction: the meaningful independent units the learner produced in the target language only; copied, echoed, or directly scaffolded phrases are weak evidence.
            - supportLanguageDependence: whether support-language speech was necessary for the learner to understand or continue.
            - aiScaffoldingDependence: how much the AI had to lead with hints, choices, simplification, or very easy prompts.
            - conversationSustainability: whether the target-language conversation was sustained by the learner's target-language responses, not by support-language conversation.
            - consistency: whether the same target-language ability was stable across the session or only appeared in isolated turns.
            - responseDifficultyFit: whether the AI's target-language difficulty matched the learner's demonstrated target-language ability.
            - confidence: whether there is enough target-language evidence to trust this analysis.
            - reasonSummary: explain the target-language evidence in Korean; mention support-language use only as context, not as ability.
            - reasonSummary must be plain Korean text without quotation marks, raw transcript examples, markdown, or line breaks.

            Output schema:
            {
              "targetLanguageComprehension": "None|WordLevel|SimpleSentence|NaturalFlow",
              "targetLanguageProduction": "None|WordsOrFragments|ShortPhrases|SimpleSentences|ConnectedTurns",
              "supportLanguageDependence": "High|Medium|Low|None",
              "aiScaffoldingDependence": "High|Medium|Low|None",
              "conversationSustainability": "RequiresSupport|SupportedShort|SustainedSimple|SustainedNatural",
              "consistency": "Low|Mixed|Stable",
              "responseDifficultyFit": "TooHard|SlightlyHard|Fits|TooEasy|Unknown",
              "confidence": "Low|Medium|High",
              "reasonSummary": "short Korean summary, max 240 chars, no quotes or raw examples",
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
