package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.ChallengeLevel
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionAdaptationPolicy
import com.app.umma.domain.model.learningstate.CorrectionStylePolicy
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.GrammarStrategyPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.PrimaryBridgeReason
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SentenceDensityPolicy
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpokenRegisterStrategy
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.learningstate.VocabularyStrategyPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildChatTurnAdaptationPolicyUseCaseTest {
    private val useCase = BuildChatTurnAdaptationPolicyUseCase()

    @Test
    fun `low confidence fragment uses active bridge and lowest response burden`() {
        // 첫 selectedLang 세션에서 단어 조각만 들어오면 실제 실력을 저장하지 않고 이번 응답만 강하게 낮춘다.
        val policy = useCase(
            transcript = "I apple hungry",
            profile = profile(confidence = ProfileConfidence.Low),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertEquals(ResponseLengthPolicy.OneShortSentence, policy.responseLength)
        assertEquals(SentenceDensityPolicy.OneIdea, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Active, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.SlowBeginner, policy.speechSpeed)
    }

    @Test
    fun `low confidence fluent utterance is not trapped in beginner fallback`() {
        // LangState 근거가 없어도 충분히 긴 target 발화는 초저숙련으로 고정하면 안 된다.
        val policy = useCase(
            transcript = "I am building a language learning app and testing the conversation feature today",
            profile = profile(confidence = ProfileConfidence.Low),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertEquals(ResponseLengthPolicy.NaturalBrief, policy.responseLength)
        assertEquals(SentenceDensityPolicy.NaturalBrief, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.FallbackOnly, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.OpenShort, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.NormalLearning, policy.speechSpeed)
    }

    @Test
    fun `explicit blocking phrase lowers current turn even with confident profile`() {
        // 장기 profile이 높아도 사용자가 "모르겠어"라고 말한 현재 turn은 대화 단절 방지가 우선이다.
        val policy = useCase(
            transcript = "모르겠어",
            profile = profile(confidence = ProfileConfidence.High),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertEquals(ResponseLengthPolicy.OneShortSentence, policy.responseLength)
        assertEquals(SentenceDensityPolicy.OneIdea, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Active, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.SlowBeginner, policy.speechSpeed)
    }

    @Test
    fun `explicit primary support request opens bridge without treating user as failed turn`() {
        // "한국어를 섞어줘"는 대화 단절이 아니라 응답 방식 요청이므로, 기준언어 보조 이유를 별도로 남긴다.
        val policy = useCase(
            transcript = "한국어를 섞어서 설명해줘",
            profile = profile(confidence = ProfileConfidence.Low),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        assertEquals(ResponseLengthPolicy.OneShortSentence, policy.responseLength)
        assertEquals(SentenceDensityPolicy.OneIdea, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Active, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.SlowBeginner, policy.speechSpeed)
        assertEquals(PrimaryBridgeReason.ExplicitSupportRequest, policy.primaryBridgeReason)
    }

    @Test
    fun `explicit primary support request follows configured primary language`() {
        // primaryLang은 한국어로 고정되지 않으므로, 영어 기준 사용자의 "explain in English"도 같은 요청으로 봐야 한다.
        val policy = useCase(
            transcript = "Please explain in English too",
            profile = profile(confidence = ProfileConfidence.Low),
            primaryLang = LangCode.EN,
            selectedLang = LangCode.JA
        )

        assertEquals(ResponseLengthPolicy.OneShortSentence, policy.responseLength)
        assertEquals(SentenceDensityPolicy.OneIdea, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Active, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.SlowBeginner, policy.speechSpeed)
        assertEquals(PrimaryBridgeReason.ExplicitSupportRequest, policy.primaryBridgeReason)
    }

    private fun profile(confidence: ProfileConfidence): LearnerAdaptationProfile {
        // test fixture는 저장 LangState가 아니라 turn policy 입력으로 쓰이는 profile read model만 재현한다.
        return LearnerAdaptationProfile(
            core = LearnerAbilityProfile(
                cefrLevel = VocabLevel.B1,
                levelConfidence = confidence,
                grammarStage = SkillStage.Stable,
                vocabularyStage = SkillStage.Stable,
                fluencyStage = SkillStage.Stable,
                naturalnessStage = SkillStage.Stable,
                focus = LearningFocusSummary(
                    primaryFocus = null,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Low,
                    observedCount = 0
                )
            ),
            chatPolicy = ChatAdaptationPolicy(
                // 기본 profile은 일반 대화 수준으로 두어 turn signal이 실제로 낮추는지 확인한다.
                conversationBand = ConversationAbilityBand.BasicConversation,
                intentSupport = IntentSupportPolicy.TrustMeaning,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                recastStyle = RecastStylePolicy.NaturalInline,
                expressionGrowth = ExpressionGrowthPolicy.OneEverydayExpression,
                questionLoad = QuestionLoadPolicy.OpenShort,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            ),
            correctionPolicy = CorrectionAdaptationPolicy(
                // Correction 정책은 이번 usecase 입력이 아니지만 profile 계약을 완성하기 위해 최소 값으로 채운다.
                challengeLevel = ChallengeLevel.Match,
                correctionStyle = CorrectionStylePolicy.ExplainOneReason,
                vocabularyStrategy = VocabularyStrategyPolicy.AddOneUsefulExpression,
                grammarStrategy = GrammarStrategyPolicy.FixOneMainPattern,
                spokenRegisterStrategy = SpokenRegisterStrategy.EverydaySpoken,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.BriefPrimaryLanguageHint
            )
        )
    }
}
