package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatTurnContextSignal
import com.app.umma.domain.model.chat.LatestUserTurnRole
import com.app.umma.domain.model.learningstate.ChallengeLevel
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ChatTurnAdaptationPolicy
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
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.PrimaryBridgeReason
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.SentenceDensityPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpokenRegisterStrategy
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.learningstate.VocabularyStrategyPolicy
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.learningstate.TurnSpeaker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPromptUseCaseTest {
    private val useCase = BuildPromptUseCase()

    @Test
    fun `prompt is compact profile based instruction without raw metrics`() {
        // LangState raw metric 은 profile usecase 에서 이미 해석되므로 prompt builder 는 profile policy 만 읽어야 한다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.ConnectedExpression,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                questionLoad = QuestionLoadPolicy.OpenShort,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                primaryFocus = LearningFocusType.WordOrder
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        // v0 prompt 는 역할, 언어, 응답 흐름, 분기, profile, context 만 갖는 짧은 구조여야 한다.
        assertTrue(prompt.contains("persona:"))
        assertTrue(prompt.contains("languages:"))
        assertTrue(prompt.contains("response_flow:"))
        assertTrue(prompt.contains("branches:"))
        assertTrue(prompt.contains("learner_policy:"))
        assertTrue(prompt.contains("learner_profile:"))
        assertTrue(prompt.contains("context:"))
        // selectedLang 은 실제 대화 목표 언어로, primaryLang 은 보조 언어로 분리되어 들어간다.
        assertTrue(prompt.contains("- target: 영어"))
        assertTrue(prompt.contains("- support: 한국어"))
        // LangState 기반 profile 은 raw 숫자가 아니라 stage/policy 문장으로만 들어가야 한다.
        assertTrue(prompt.contains("grammar: 기본 대화를 안정적으로 이어간다"))
        assertTrue(prompt.contains("focus: 자연스러운 기회가 있을 때 어순"))
        // 기본 응답과 재표현은 문법적으로 맞는 문장보다 실제 원어민 구어체를 우선해야 한다.
        assertTrue(prompt.contains("모국어는 영어이고 한국어도 잘 구사하는, 눈치 빠른 원어민 친구"))
        assertTrue(prompt.contains("모든 응답의 첫 원칙은 실제 일상 대화처럼 자연스럽게 반응하는 것이다."))
        assertTrue(prompt.contains("학습 보조는 대화를 깨지 않는 범위에서만 조용히 섞고, 원어민이 자주 쓰는 자연스러운 구어체를 우선한다."))
        assertTrue(prompt.contains("원어민이 실제 자주 쓰는 영어 문장 안에 자연스럽게 한 번 녹인다"))
        // Chat band/policy는 내부 enum 이름이 아니라 행동 지시로만 압축되어 들어간다.
        assertTrue(prompt.contains("뜻이 보이면 확인 질문보다 자연스러운 대화 반응을 우선한다."))
        assertTrue(prompt.contains("상황에 맞는 일상 구어 표현 하나만 더한다."))
        assertFalse(prompt.contains("ConnectedExpression"))
        assertFalse(prompt.contains("OpenShort"))
        // raw metric 필드명이나 숫자 예시는 AI가 내부 상태를 말하게 만들 수 있으므로 노출하지 않는다.
        assertFalse(prompt.contains("grammarAccuracy"))
        assertFalse(prompt.contains("fluencyScore"))
        assertFalse(prompt.contains("naturalnessScore"))
        assertFalse(prompt.contains("0.42"))
    }

    @Test
    fun `support profile allows short primary language bridge for fragments`() {
        // 초저숙련 또는 근거 부족 profile 은 사용자가 다음 말을 잃지 않도록 primaryLang 보조가 필요하다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.IntentOnly,
                responseLength = ResponseLengthPolicy.OneShortSentence,
                questionLoad = QuestionLoadPolicy.ConcreteChoice,
                primaryBridge = PrimaryBridgePolicy.Active,
                confidence = ProfileConfidence.Low
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        // fragment 분기는 target 몰입보다 이해 보장을 먼저 두고, target 표현은 짧게 노출해야 한다.
        assertTrue(prompt.contains("fragment: 뜻만 있는 단어 조각이면 기준언어(한국어)로 의미를 먼저 받아 주고, 영어는 완성 문장보다 1~3단어 조합이나 아주 짧은 고정 표현 하나만 붙인다."))
        assertTrue(prompt.contains("priority_rule: 영어 노출보다 사용자가 이해하고 다음 말을 할 수 있게 하는 것이 먼저다."))
        // low confidence 는 낮은 실력 확정이 아니라 근거 부족이므로 현재 발화를 이해 가능한 반응으로 받아야 한다.
        assertTrue(prompt.contains("저장된 근거가 적어도 현재 발화가 이어질 수 있게 이해 가능한 반응을 우선한다."))
        // Support 정책은 장문 설명이나 표현 drill보다 실제 내용으로 짧게 반응할 여지를 만든다.
        assertTrue(prompt.contains("불완전한 말에서도 사용자의 의도를 먼저 추론하고 대화를 이어간다."))
        assertTrue(prompt.contains("필요할 때 음식, 장소, 감정, 행동처럼 실제 내용으로 짧게 답할 여지를 준다."))
        // 첫 세션에서 발화가 조각나면 audio speed만 낮추는 것으로 부족하므로 의미 단위로 밀도를 낮춘다.
        assertTrue(prompt.contains("첫 발화가 조각나도 천천히 말하며 한 가지 의미씩 이해하게 한다."))
    }

    @Test
    fun `target only profile keeps primary support closed by default`() {
        // 고급 profile 은 primaryLang 설명이 남용되면 실제 selectedLang 대화 경험이 약해진다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.NuanceControl,
                responseLength = ResponseLengthPolicy.Flexible,
                questionLoad = QuestionLoadPolicy.NuanceFollowUp,
                primaryBridge = PrimaryBridgePolicy.None,
                confidence = ProfileConfidence.High
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        // selectedLang 이 일본어이면 대화 목표는 일본어로 고정된다.
        assertTrue(prompt.contains("- target: 일본어"))
        // Chat의 primary bridge none 정책은 사용자가 요청하지 않으면 primaryLang 보조를 닫는다.
        assertTrue(prompt.contains("사용자가 요청한 경우를 제외하고 일본어만 사용한다."))
        // 고급 단계도 내부 band 이름 없이 일반 대화와 미세한 뉘앙스 조정만 지시한다.
        assertTrue(prompt.contains("사용자가 이끄는 주제와 말투를 따라가며 일반 대화처럼 답한다."))
        assertTrue(prompt.contains("저장된 profile을 적극 참고하되 대화 흐름을 우선한다."))
        assertFalse(prompt.contains("NuanceControl"))
    }

    @Test
    fun `prompt carries context but does not copy previous ai habits`() {
        // 최근 대화는 주제 연속성에 필요하지만 이전 AI의 나쁜 응답 습관을 강화하면 안 된다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.SimpleSentence,
                responseLength = ResponseLengthPolicy.ShortTwoStep,
                questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
                primaryBridge = PrimaryBridgePolicy.Brief
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN,
            recentFullContext = listOf(
                SessionTurn(
                    turnId = "turn-1",
                    sessionId = "session-1",
                    role = TurnSpeaker.USER,
                    text = "I apple hungry",
                    createdAt = 1_000L
                )
            ),
            recentTopicSummaries = listOf("food and hunger")
        )

        // 저장된 turn 과 topic 은 들어가지만, 사용 목적은 주제 이해로 제한된다.
        assertTrue(prompt.contains("- USER: I apple hungry"))
        assertTrue(prompt.contains("- food and hunger"))
        assertTrue(prompt.contains("최근 맥락은 주제 이해에만 쓰고, 이전 AI의 응답 습관은 모방하지 않는다."))
    }

    @Test
    fun `runtime prompt does not include previous failure trigger phrases`() {
        // 실패했던 문장을 금지 예시로 넣어도 Realtime 모델이 행동 예시로 오해할 수 있어 runtime prompt 에서 제거한다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.IntentOnly,
                responseLength = ResponseLengthPolicy.OneShortSentence,
                questionLoad = QuestionLoadPolicy.ConcreteChoice,
                primaryBridge = PrimaryBridgePolicy.Active
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        // 이전 실패 루프를 유발한 표현들은 prompt 본문에 직접 들어가면 안 된다.
        assertFalse(prompt.contains("yes/no"))
        assertFalse(prompt.contains("ready"))
        assertFalse(prompt.contains("준비"))
        assertFalse(prompt.contains("keep it simple"))
        assertFalse(prompt.contains("Let's try"))
        assertFalse(prompt.contains("repeat after"))
        assertFalse(prompt.contains("따라"))
    }

    @Test
    fun `turn override instruction is short response scoped guidance`() {
        // turn override는 세션 prompt 전체를 다시 보내지 않고 이번 응답에서 바뀌는 값만 짧게 전달해야 한다.
        val instruction = useCase.buildTurnOverrideInstruction(
            basePolicy = ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.NaturalBrief,
                sentenceDensity = SentenceDensityPolicy.NaturalBrief,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                questionLoad = QuestionLoadPolicy.OpenShort,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            ),
            turnPolicy = ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.OneShortSentence,
                sentenceDensity = SentenceDensityPolicy.OneIdea,
                primaryBridge = PrimaryBridgePolicy.Active,
                questionLoad = QuestionLoadPolicy.ConcreteChoice,
                speechSpeed = SpeechSpeedPolicy.SlowBeginner
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertTrue(instruction!!.contains("current_turn_override:"))
        assertTrue(instruction.contains("이번 응답은 짧게 반응하고 한 가지 의미만 전달한다."))
        assertTrue(instruction.contains("한국어로 의미를 먼저 받아 주고 영어는 1~3단어 조합이나 아주 짧은 표현만 붙인다."))
        assertTrue(instruction.contains("필요할 때 음식, 장소, 감정, 행동 중 하나로 짧게 답할 여지를 둔다."))
        // response override는 세션 persona/context를 반복하면 prompt 충돌과 지연을 키울 수 있다.
        assertFalse(instruction.contains("persona:"))
        assertFalse(instruction.contains("learner_profile:"))
        assertFalse(instruction.contains("recent_turns:"))
    }

    @Test
    fun `turn override instruction is omitted when policy matches baseline`() {
        // baseline과 같은 값을 매 turn 다시 보내면 "천천히/쉽게" 지시가 누적 강화될 수 있다.
        val basePolicy = ChatTurnAdaptationPolicy(
            responseLength = ResponseLengthPolicy.OneShortSentence,
            sentenceDensity = SentenceDensityPolicy.OneIdea,
            primaryBridge = PrimaryBridgePolicy.Active,
            questionLoad = QuestionLoadPolicy.ConcreteChoice,
            speechSpeed = SpeechSpeedPolicy.SlowBeginner
        )

        // 같은 policy면 이번 response에는 별도 instructions를 붙이지 않고 세션 prompt만 따르게 한다.
        val instruction = useCase.buildTurnOverrideInstruction(
            basePolicy = basePolicy,
            turnPolicy = basePolicy,
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertFalse(instruction?.contains("current_turn_override:") == true)
    }

    @Test
    fun `progressing context does not create interpretation override when policy matches baseline`() {
        // ProgressingInContext는 강한 보정을 막는 분류 신호이지, 그 자체로 반복 instruction을 만들지 않는다.
        val basePolicy = ChatTurnAdaptationPolicy(
            responseLength = ResponseLengthPolicy.ShortTwoStep,
            sentenceDensity = SentenceDensityPolicy.SimpleTwoStep,
            primaryBridge = PrimaryBridgePolicy.Brief,
            questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
            speechSpeed = SpeechSpeedPolicy.Guided
        )

        val instruction = useCase.buildTurnOverrideInstruction(
            basePolicy = basePolicy,
            turnPolicy = basePolicy,
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN,
            contextSignal = ChatTurnContextSignal(
                latestUserTurnRole = LatestUserTurnRole.ProgressingInContext,
                followsAssistantQuestion = true
            )
        )

        assertFalse(instruction?.contains("current_turn_override:") == true)
    }

    @Test
    fun `explicit primary support request creates bridge override even when baseline matches`() {
        // 첫 세션 baseline이 이미 Active여도 사용자가 한국어 보조를 직접 요청하면 이번 응답에 그 요청을 다시 전달해야 한다.
        val basePolicy = ChatTurnAdaptationPolicy(
            responseLength = ResponseLengthPolicy.OneShortSentence,
            sentenceDensity = SentenceDensityPolicy.OneIdea,
            primaryBridge = PrimaryBridgePolicy.Active,
            questionLoad = QuestionLoadPolicy.ConcreteChoice,
            speechSpeed = SpeechSpeedPolicy.SlowBeginner
        )

        val instruction = useCase.buildTurnOverrideInstruction(
            basePolicy = basePolicy,
            turnPolicy = basePolicy.copy(
                primaryBridgeReason = PrimaryBridgeReason.ExplicitSupportRequest
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        assertTrue(instruction!!.contains("current_turn_override:"))
        assertTrue(instruction.contains("한국어 보조 요청을 반영해 한국어로 이해를 먼저 보장하고, 일본어는 1~3단어 조합이나 아주 짧은 표현만 붙인다."))
    }

    @Test
    fun `beginner auto support does not repeat bridge override when baseline matches`() {
        // 1~2단계 자동 보조가 세션 기본값과 같으면 같은 기준언어 보조 instruction을 매 turn 반복하지 않는다.
        val basePolicy = ChatTurnAdaptationPolicy(
            responseLength = ResponseLengthPolicy.OneShortSentence,
            sentenceDensity = SentenceDensityPolicy.OneIdea,
            primaryBridge = PrimaryBridgePolicy.Active,
            questionLoad = QuestionLoadPolicy.ConcreteChoice,
            speechSpeed = SpeechSpeedPolicy.SlowBeginner
        )

        val instruction = useCase.buildTurnOverrideInstruction(
            basePolicy = basePolicy,
            turnPolicy = basePolicy.copy(
                primaryBridgeReason = PrimaryBridgeReason.BeginnerAutoSupport
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        assertFalse(instruction?.contains("current_turn_override:") == true)
    }

    private fun profile(
        conversationBand: ConversationAbilityBand,
        responseLength: ResponseLengthPolicy,
        questionLoad: QuestionLoadPolicy,
        primaryBridge: PrimaryBridgePolicy,
        confidence: ProfileConfidence = ProfileConfidence.Medium,
        primaryFocus: LearningFocusType? = null,
        focusConfidence: ProfileConfidence = if (primaryFocus == null) ProfileConfidence.Low else ProfileConfidence.Medium,
        focusObservedCount: Int = if (primaryFocus == null) 0 else 2
    ): LearnerAdaptationProfile {
        // fixture 는 저장 모델이 아니라 prompt builder 가 받는 domain read model 만 재현한다.
        return LearnerAdaptationProfile(
            core = LearnerAbilityProfile(
                cefrLevel = VocabLevel.B1,
                levelConfidence = confidence,
                grammarStage = SkillStage.Stable,
                vocabularyStage = SkillStage.Stable,
                fluencyStage = SkillStage.Stable,
                naturalnessStage = SkillStage.Stable,
                focus = LearningFocusSummary(
                    primaryFocus = primaryFocus,
                    secondaryFocus = null,
                    confidence = focusConfidence,
                    observedCount = focusObservedCount
                )
            ),
            chatPolicy = ChatAdaptationPolicy(
                conversationBand = conversationBand,
                intentSupport = intentSupportFor(conversationBand),
                primaryBridge = primaryBridge,
                recastStyle = recastFor(conversationBand),
                expressionGrowth = growthFor(conversationBand),
                questionLoad = questionLoad,
                responseLength = responseLength,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            ),
            correctionPolicy = CorrectionAdaptationPolicy(
                challengeLevel = correctionChallengeFor(conversationBand),
                correctionStyle = CorrectionStylePolicy.ExplainOneReason,
                vocabularyStrategy = VocabularyStrategyPolicy.AddOneUsefulExpression,
                grammarStrategy = GrammarStrategyPolicy.FixOneMainPattern,
                spokenRegisterStrategy = SpokenRegisterStrategy.EverydaySpoken,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.BriefPrimaryLanguageHint
            )
        )
    }

    private fun intentSupportFor(band: ConversationAbilityBand): IntentSupportPolicy {
        // fixture도 production 정책과 같은 방향으로 만들어 prompt 문구 검증이 실제 정책과 어긋나지 않게 한다.
        return when (band) {
            ConversationAbilityBand.IntentOnly -> IntentSupportPolicy.InferActively
            ConversationAbilityBand.PhraseEmerging,
            ConversationAbilityBand.SimpleSentence -> IntentSupportPolicy.ConfirmBriefly
            ConversationAbilityBand.BasicConversation,
            ConversationAbilityBand.ConnectedExpression -> IntentSupportPolicy.TrustMeaning
            ConversationAbilityBand.NuanceControl -> IntentSupportPolicy.FollowUserLead
        }
    }

    private fun recastFor(band: ConversationAbilityBand): RecastStylePolicy {
        // 낮은 band는 쉬운 재표현, 높은 band는 자연스러운 구어체/뉘앙스 중심으로 테스트한다.
        return when (band) {
            ConversationAbilityBand.IntentOnly -> RecastStylePolicy.TinyInline
            ConversationAbilityBand.PhraseEmerging,
            ConversationAbilityBand.SimpleSentence -> RecastStylePolicy.SimpleInline
            ConversationAbilityBand.BasicConversation,
            ConversationAbilityBand.ConnectedExpression -> RecastStylePolicy.NaturalInline
            ConversationAbilityBand.NuanceControl -> RecastStylePolicy.NuanceOnly
        }
    }

    private fun growthFor(band: ConversationAbilityBand): ExpressionGrowthPolicy {
        // 성장 폭은 band가 높아질수록 커지지만 prompt에는 항상 하나의 표현만 허용한다.
        return when (band) {
            ConversationAbilityBand.IntentOnly -> ExpressionGrowthPolicy.OneTinyPhrase
            ConversationAbilityBand.PhraseEmerging,
            ConversationAbilityBand.SimpleSentence -> ExpressionGrowthPolicy.OneSimplePattern
            ConversationAbilityBand.BasicConversation,
            ConversationAbilityBand.ConnectedExpression -> ExpressionGrowthPolicy.OneEverydayExpression
            ConversationAbilityBand.NuanceControl -> ExpressionGrowthPolicy.OneNativeLikeChoice
        }
    }

    private fun correctionChallengeFor(band: ConversationAbilityBand): ChallengeLevel {
        // 이 fixture는 Chat band와 Correction challenge가 별도 축이라는 점을 유지하기 위해 최소 매핑만 둔다.
        return when (band) {
            ConversationAbilityBand.IntentOnly,
            ConversationAbilityBand.PhraseEmerging -> ChallengeLevel.Support
            ConversationAbilityBand.SimpleSentence,
            ConversationAbilityBand.BasicConversation -> ChallengeLevel.Match
            ConversationAbilityBand.ConnectedExpression -> ChallengeLevel.Stretch
            ConversationAbilityBand.NuanceControl -> ChallengeLevel.Refine
        }
    }
}
