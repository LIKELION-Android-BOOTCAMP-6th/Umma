package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatTurnContextSignal
import com.app.umma.domain.model.chat.LatestUserTurnRole
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.PrimaryBridgeReason
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SentenceDensityPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.VocabLevel
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
    fun `low confidence progressing context is not treated as failed fragment`() {
        // 짧은 명사구라도 최근 맥락 안에서 이어지고 있으면 반복 보정 대신 자연 대화 지속을 우선한다.
        val policy = useCase(
            transcript = "summer trip",
            contextSignal = ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.ProgressingInContext,
                followsAssistantQuestion = true
            ),
            profile = profile(confidence = ProfileConfidence.Low),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertEquals(ResponseLengthPolicy.ShortTwoStep, policy.responseLength)
        assertEquals(SentenceDensityPolicy.SimpleTwoStep, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Brief, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.OneConcreteFollowUp, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.Guided, policy.speechSpeed)
    }

    @Test
    fun `conversation start phrase avoids beginner auto support`() {
        // 짧은 시작 발화는 fragment처럼 보이지만 실제로는 대화 시작 의도라 SlowBeginner/Active bridge로 낮추지 않는다.
        val policy = useCase(
            transcript = "Hello, let's talk.",
            contextSignal = ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.TopicContinuation,
                followsAssistantQuestion = false
            ),
            profile = profile(
                confidence = ProfileConfidence.Low,
                conversationBand = ConversationAbilityBand.PhraseEmerging
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertEquals(ResponseLengthPolicy.ShortTwoStep, policy.responseLength)
        assertEquals(SentenceDensityPolicy.SimpleTwoStep, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Brief, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.OneConcreteFollowUp, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.Guided, policy.speechSpeed)
        assertEquals(PrimaryBridgeReason.ProfileDefault, policy.primaryBridgeReason)
    }

    @Test
    fun `primary dominant answer keeps active bridge even when it answers assistant question`() {
        // 직전 질문의 답변이어도 사용자가 기준언어로 버티는 상태라면 자연 대화보다 이해 보조를 우선해야 한다.
        val policy = useCase(
            transcript = "오늘 너무 힘들었어",
            contextSignal = ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.ProgressingInContext,
                followsAssistantQuestion = true
            ),
            profile = profile(confidence = ProfileConfidence.Low),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        assertEquals(ResponseLengthPolicy.OneShortSentence, policy.responseLength)
        assertEquals(SentenceDensityPolicy.OneIdea, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Active, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.SlowBeginner, policy.speechSpeed)
        assertEquals(PrimaryBridgeReason.PrimaryDominantTurn, policy.primaryBridgeReason)
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
        assertEquals(PrimaryBridgeReason.PrimaryDominantTurn, policy.primaryBridgeReason)
    }

    @Test
    fun `target language blocking phrase lowers burden without forcing primary bridge for capable profile`() {
        // target 언어로 자연 대화 중 "I don't know"라고 말하는 것은 막힘이지만, 그 자체만으로 한국어 혼합 요청은 아니다.
        val policy = useCase(
            transcript = "I don't know",
            profile = profile(confidence = ProfileConfidence.High),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertEquals(ResponseLengthPolicy.OneShortSentence, policy.responseLength)
        assertEquals(SentenceDensityPolicy.OneIdea, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Brief, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.SlowBeginner, policy.speechSpeed)
        assertEquals(PrimaryBridgeReason.ProfileDefault, policy.primaryBridgeReason)
    }

    @Test
    fun `target language complex sentence complaint lowers response burden`() {
        // "too complex"나 "can't understand"는 긴 영어 발화여도 support request이므로 NaturalBrief로 유지하면 안 된다.
        val policy = useCase(
            transcript = "I'm sorry, that's two complex sentences I can't understand totally.",
            profile = profile(confidence = ProfileConfidence.High),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertEquals(ResponseLengthPolicy.OneShortSentence, policy.responseLength)
        assertEquals(SentenceDensityPolicy.OneIdea, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Brief, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.SlowBeginner, policy.speechSpeed)
        assertEquals(PrimaryBridgeReason.ProfileDefault, policy.primaryBridgeReason)
    }

    @Test
    fun `beginner band relaxes automatic primary support when short answer is progressing in context`() {
        // 1~2단계 profile이어도 최근 맥락 안에서 정상 진행 중이면 같은 기준언어 보정을 반복하지 않는다.
        val policy = useCase(
            transcript = "はい",
            contextSignal = ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.ProgressingInContext,
                followsAssistantQuestion = true
            ),
            profile = profile(
                confidence = ProfileConfidence.Low,
                conversationBand = ConversationAbilityBand.PhraseEmerging
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        assertEquals(ResponseLengthPolicy.ShortTwoStep, policy.responseLength)
        assertEquals(SentenceDensityPolicy.SimpleTwoStep, policy.sentenceDensity)
        assertEquals(PrimaryBridgePolicy.Brief, policy.primaryBridge)
        assertEquals(QuestionLoadPolicy.OneConcreteFollowUp, policy.questionLoad)
        assertEquals(SpeechSpeedPolicy.Guided, policy.speechSpeed)
        assertEquals(PrimaryBridgeReason.ProfileDefault, policy.primaryBridgeReason)
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

    private fun profile(
        confidence: ProfileConfidence,
        conversationBand: ConversationAbilityBand = ConversationAbilityBand.BasicConversation
    ): LearnerAdaptationProfile {
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
                conversationBand = conversationBand,
                intentSupport = IntentSupportPolicy.TrustMeaning,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                recastStyle = RecastStylePolicy.NaturalInline,
                expressionGrowth = ExpressionGrowthPolicy.OneEverydayExpression,
                questionLoad = QuestionLoadPolicy.OpenShort,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            ),
            // Correction 정책은 이번 usecase 입력이 아니지만 profile 계약을 완성하기 위해 채운다.
            correctionPolicy = CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.EverydayNatural)
        )
    }
}
