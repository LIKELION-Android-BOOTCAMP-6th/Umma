package com.app.umma.data.repository.chatconversation

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.chat.ChatConversationAnalysisTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConversationEvidencePromptBuilderTest {
    private val builder = ChatConversationEvidencePromptBuilder()

    @Test
    fun `prompt evaluates only target language ability`() {
        val prompt = builder.build(
            ChatConversationAnalysisSession(
                sessionId = "session-1",
                selectedLang = LangCode.JA,
                turns = listOf(
                    // 사용자가 기준언어로는 유창하지만 학습언어는 거의 못 쓰는 상황을 prompt 기준에 고정한다.
                    turn(TurnSpeaker.USER, "나는 점심 먹었어. 근데 일본어로는 모르겠어."),
                    turn(TurnSpeaker.AI, "점심 먹었구나. ひるごはん? おいしい?")
                )
            )
        )

        // Gemini는 전체 대화의 자연스러움이 아니라 selectedLang으로 증명된 능력만 평가해야 한다.
        assertTrue(prompt.contains("Evaluate only the learner's ability in the target language: ja."))
        assertTrue(prompt.contains("must not increase target-language ability ratings"))
        assertTrue(prompt.contains("Do not treat fluent Korean/support-language speech as fluent target-language conversation."))
        assertTrue(prompt.contains("targetLanguageProduction: the meaningful independent units the learner produced in the target language only"))
        assertTrue(prompt.contains("supportLanguageDependence: whether support-language speech was necessary"))
        assertTrue(prompt.contains("aiScaffoldingDependence: how much the AI had to lead"))
        assertTrue(prompt.contains("Judge all evidence fields using the same target-language-only standard."))
    }

    @Test
    fun `prompt does not overrate scaffolded target language repetition as simple sentence ability`() {
        val prompt = builder.build(
            ChatConversationAnalysisSession(
                sessionId = "session-1",
                selectedLang = LangCode.JA,
                turns = listOf(
                    // AI가 먼저 준 단어와 구조를 사용자가 일부 반복한 상황은 자유 단문 능력으로 올리면 안 된다.
                    turn(TurnSpeaker.AI, "날씨 좋아? 天気いいです?"),
                    turn(TurnSpeaker.USER, "天気いいです"),
                    turn(TurnSpeaker.USER, "こう가 뭐야? 한국어로 알려줘")
                )
            )
        )

        // Gemini가 유도된 한 문장을 SimpleSentence/SimpleSentences로 과대평가하지 않게 기준을 명시한다.
        assertTrue(prompt.contains("copied, repeated, lightly recombined, or directly scaffolded"))
        assertTrue(prompt.contains("is not SimpleSentence production"))
        assertTrue(prompt.contains("One isolated target-language sentence is not enough"))
        assertTrue(prompt.contains("SimpleSentence only when the learner repeatedly responded correctly"))
        assertTrue(prompt.contains("SimpleSentences only when the learner independently produced more than one simple target-language sentence"))
        assertTrue(prompt.contains("treat it as WordsOrFragments or ShortPhrases unless later turns show independent transfer"))
        assertTrue(prompt.contains("Do not describe one isolated scaffolded sentence as independent production."))
        assertFalse(prompt.contains("debugRecommendedBand should normally be IntentOnly or PhraseEmerging"))
    }

    @Test
    fun `prompt keeps high support and low confidence evidence at intent only`() {
        val prompt = builder.build(
            ChatConversationAnalysisSession(
                sessionId = "session-1",
                selectedLang = LangCode.JA,
                turns = listOf(
                    // 신고 세션 회귀: 한국어 설명과 AI 리드가 없으면 이어지지 않는 상태를 PhraseEmerging으로 올리면 안 된다.
                    turn(TurnSpeaker.USER, "こんにちは"),
                    turn(TurnSpeaker.AI, "こんにちは. 기분 좋아? 気分がいい?"),
                    turn(TurnSpeaker.USER, "気分がいいです"),
                    turn(TurnSpeaker.USER, "그게 무슨 뜻이야? 한국어로 알려줘")
                )
            )
        )

        // Low confidence/RequiresSupport/High 의존성 조합은 debug 후보도 IntentOnly로 묶어 평가 흔들림을 줄인다.
        assertTrue(prompt.contains("If confidence is Low, conversationSustainability is RequiresSupport"))
        assertTrue(prompt.contains("both supportLanguageDependence and aiScaffoldingDependence are High"))
        assertTrue(prompt.contains("debugRecommendedBand should be IntentOnly"))
        assertTrue(prompt.contains("PhraseEmerging requires repeated target-language words or short phrases across turns"))
        assertTrue(prompt.contains("supportLanguageDependence and aiScaffoldingDependence no higher than Medium"))
        assertTrue(prompt.contains("conversationSustainability not RequiresSupport"))
        assertTrue(prompt.contains("do not recommend PhraseEmerging or above unless the learner later shows independent target-language transfer"))
        assertTrue(prompt.contains("responseDifficultyFit should normally be TooHard or Unknown, not Fits"))
    }

    @Test
    fun `prompt keeps reason summary safe for json decoding`() {
        val prompt = builder.build(
            ChatConversationAnalysisSession(
                sessionId = "session-1",
                selectedLang = LangCode.JA,
                turns = listOf(
                    // reasonSummary에 원문 예시를 따옴표로 넣으면 Gemini JSON이 깨질 수 있어 prompt에서 금지한다.
                    turn(TurnSpeaker.USER, "ただいま"),
                    turn(TurnSpeaker.AI, "おかえり. 기분 어때?")
                )
            )
        )

        assertTrue(prompt.contains("reasonSummary must be plain Korean text without quotation marks"))
        assertTrue(prompt.contains("no quotes or raw examples"))
    }

    @Test
    fun `prompt keeps final band as app policy responsibility`() {
        val prompt = builder.build(
            ChatConversationAnalysisSession(
                sessionId = "session-1",
                selectedLang = LangCode.EN,
                turns = listOf(
                    // band 결정은 아직 앱 domain policy 책임이므로 Gemini에는 evidence와 참고 band만 요구한다.
                    turn(TurnSpeaker.USER, "I ate lunch."),
                    turn(TurnSpeaker.AI, "Nice. Was it good?")
                )
            )
        )

        assertTrue(prompt.contains("Decide conversation ability evidence, not the final product band."))
        assertTrue(prompt.contains("The app will calculate ConversationAbilityBand using its own policy."))
        assertTrue(prompt.contains("debugRecommendedBand is only a reference guess and will not be directly applied."))
        assertFalse(prompt.contains("Apply recommendedBand directly"))
    }

    private fun turn(
        speaker: TurnSpeaker,
        text: String
    ): ChatConversationAnalysisTurn {
        // prompt builder는 speaker/text만 직렬화하므로 createdAt은 정렬 검증이 아닌 최소 fixture 값으로 둔다.
        return ChatConversationAnalysisTurn(
            speaker = speaker,
            text = text,
            createdAt = 1_000L
        )
    }
}
