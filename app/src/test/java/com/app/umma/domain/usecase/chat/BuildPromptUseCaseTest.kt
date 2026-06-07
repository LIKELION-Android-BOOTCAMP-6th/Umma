package com.app.umma.domain.usecase.chat

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
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.realtime.SessionTurn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPromptUseCaseTest {
    private val useCase = BuildPromptUseCase()

    @Test
    fun `prompt exposes simplified style instead of policy matrix`() {
        // 새 prompt surface는 모델에게 여러 policy 축을 조합시키지 않고, 대화 원칙과 현재 스타일만 전달한다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.PhraseEmerging,
                primaryBridge = PrimaryBridgePolicy.Active,
                confidence = ProfileConfidence.Low
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        // 세션 prompt는 기존 branches/learner_policy/learner_profile/turn override 구조를 제거하고 단순 섹션만 남긴다.
        assertTrue(prompt.contains("conversation_frame:"))
        assertTrue(prompt.contains("persona:"))
        assertTrue(prompt.contains("language_use:"))
        assertTrue(prompt.contains("conversation_principles:"))
        assertTrue(prompt.contains("current_style:"))
        assertTrue(prompt.contains("style_reference:"))
        assertTrue(prompt.contains("context:"))
        assertFalse(prompt.contains("branches:"))
        assertFalse(prompt.contains("learner_policy:"))
        assertFalse(prompt.contains("learner_profile:"))
        assertFalse(prompt.contains("current_turn_override:"))

        // 언어쌍과 친구 대화 목표는 유지하되, 세부 policy enum은 prompt에 노출하지 않는다.
        assertTrue(prompt.contains("learning_language: 일본어"))
        assertTrue(prompt.contains("support_language: 한국어"))
        assertTrue(prompt.contains("따뜻한 일상 대화 상대"))
        assertTrue(prompt.contains("조각난 말, 멈춤, 기준언어 혼합"))
        assertTrue(prompt.contains("저장된 근거가 적으므로 현재 발화와 최근 맥락을 더 믿는다."))
        assertTrue(prompt.contains("예시는 복사하지 말고 난이도와 리듬만 참고한다."))
        assertTrue(prompt.contains("사용자는 일본어 단어와 짧은 구를 일부 알아듣고"))
        assertTrue(prompt.contains("먼저 친구 반응으로 조금 이어 가고"))
        assertTrue(prompt.contains("한국어 짧은 말로 의미를 받친 뒤"))
        assertTrue(prompt.contains("쉬운 일본어 구나 아주 짧은 문장 하나를 붙인다."))
        assertTrue(prompt.contains("일본어 만으로 길게 이어 가지 않는다."))
        assertTrue(prompt.contains("단어, 짧은 구, 아주 쉬운 한 문장으로 반응할 수 있게 한다."))
        // 일본어 세션에서는 영어 예시가 들어가면 language_use의 두 언어 제한과 충돌하므로 일본어 예시만 허용한다.
        assertTrue(prompt.contains("ともだちと話した. 기분 좀 나아졌어?"))
        assertFalse(prompt.contains("Lunch? Good?"))
        assertFalse(prompt.contains("Tired today."))
        assertFalse(prompt.contains("초기 단계에서는 AI가 대화를 대부분 리드한다."))
        assertFalse(prompt.contains("PhraseEmerging"))
        assertFalse(prompt.contains("PrimaryBridgePolicy"))
        assertFalse(prompt.contains("OneShortSentence"))
    }

    @Test
    fun `prompt does not fall back to English examples for non English languages without examples`() {
        // 아직 독일어 전용 예시를 만들지 않았더라도 영어 예시를 넣으면 제3언어가 prompt에 새는 회귀가 된다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.IntentOnly,
                primaryBridge = PrimaryBridgePolicy.Active
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.DE
        )

        // 언어별 예시가 없는 경우에는 문장 예시 대신 리듬 설명만 제공해 출력 언어를 오염시키지 않는다.
        assertTrue(prompt.contains("아직 언어별 문장 예시가 없으므로"))
        assertTrue(prompt.contains("독일어 말 한 조각만 붙인다."))
        assertFalse(prompt.contains("Sleep well?"))
        assertFalse(prompt.contains("Hungry?"))
    }

    @Test
    fun `prompt avoids hard tutor limits and phrase guard examples`() {
        // 이번 개편은 "1~3단어/한 의미" 같은 제한형 지시와 실패 phrase guard를 runtime prompt에서 제거하는 것이 핵심이다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.IntentOnly,
                primaryBridge = PrimaryBridgePolicy.Active,
                confidence = ProfileConfidence.Low
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        // prompt는 금지어 목록이 아니라 상위 행동 원칙으로만 모델을 유도해야 한다.
        assertTrue(prompt.contains("영어 만으로 대화를 이어가기 어렵고"))
        assertTrue(prompt.contains("대화 부담은 네가 가져가고, 사용자가 조금만 반응해도 친구처럼 이어 간다."))
        assertTrue(prompt.contains("긴 질문보다 짧은 친구 반응으로 이어 간다."))
        assertTrue(prompt.contains("한국어, 단어 하나, 응/네처럼 아주 짧게 반응해도 충분하다."))
        assertTrue(prompt.contains("먼저 네 짧은 친구 반응으로 조금 이어 가고, 사용자가 막히거나 흐름이 비었을 때만 한국어 질문과 쉬운 선택지로 작은 답 길을 둔다."))
        assertTrue(prompt.contains("지원 요청을 받으면 '섞어서 말하겠다'고 예고하지 말고"))
        assertTrue(prompt.contains("뜻을 짧게 확인한 뒤에는 그 표현에 머물지 말고 사용자의 현재 말, 최근 주제, 관심사 중 가까운 쪽으로 대화를 이어 간다."))
        assertTrue(prompt.contains("이 단계의 모든 응답은 한국어 짧은 말과 영어 아주 작은 단어/구를 함께 둔다."))
        assertTrue(prompt.contains("한국어 문장이 먼저 의미를 받치고, 영어는 그 옆의 작은 조각으로만 붙는다."))
        assertTrue(prompt.contains("영어 만으로 응답하지 않는다."))
        assertTrue(prompt.contains("언어 형식은 language_use를 따르고, 먼저 친구처럼 반응해 조금 이어 간 뒤 필요할 때만 의미를 바로 알 수 있는 작은 답 길을 둔다."))
        assertTrue(prompt.contains("아래 예시는 이 단계의 기본 말투다. 복사하지 말고 같은 길이와 섞임 리듬으로 말한다."))
        assertTrue(prompt.contains("선택지는 기본 말투가 아니라, 사용자가 막힐 때만 얹는 작은 발판이다."))
        assertTrue(prompt.contains("집 앞 산책 좋지. Walk. 바람도 좋았겠다."))
        assertTrue(prompt.contains("친구랑 통화했구나. Friend. 같이 걸으면 덜 심심하지."))
        assertTrue(prompt.contains("막히면: 어디였어? 집 앞 / 공원. Home / park."))
        assertFalse(prompt.contains("Sleep well?"))
        assertFalse(prompt.contains("영어 단어 하나나 두 단어 표현"))
        assertFalse(prompt.contains("고개"))
        assertFalse(prompt.contains("끄덕"))
        assertFalse(prompt.contains("1~3단어"))
        assertFalse(prompt.contains("한 가지 의미"))
        assertFalse(prompt.contains("따라"))
        assertFalse(prompt.contains("준비"))
        assertFalse(prompt.contains("연습"))
        assertFalse(prompt.contains("반복"))
        assertFalse(prompt.contains("문장 만들"))
        assertFalse(prompt.contains("i dont know"))
        assertFalse(prompt.contains("알겠다니까"))
    }

    @Test
    fun `japanese intent only prompt uses small daily topics instead of reassurance loop`() {
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.IntentOnly,
                primaryBridge = PrimaryBridgePolicy.Active,
                confidence = ProfileConfidence.Low
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.JA
        )

        // 신고된 회귀는 IntentOnly가 안심 루틴이나 AI 주도 상황극으로 굳는 문제였으므로 사용자 마지막 말에 붙어 작게 이동하게 한다.
        assertTrue(prompt.contains("일본어 만으로 대화를 이어가기 어렵고"))
        assertTrue(prompt.contains("대화 부담은 네가 가져가고, 사용자가 조금만 반응해도 친구처럼 이어 간다."))
        assertTrue(prompt.contains("긴 질문보다 짧은 친구 반응으로 이어 간다."))
        assertTrue(prompt.contains("한국어, 단어 하나, 응/네처럼 아주 짧게 반응해도 충분하다."))
        assertTrue(prompt.contains("먼저 네 짧은 친구 반응으로 조금 이어 가고, 사용자가 막히거나 흐름이 비었을 때만 한국어 질문과 쉬운 선택지로 작은 답 길을 둔다."))
        assertTrue(prompt.contains("지원 요청을 받으면 '섞어서 말하겠다'고 예고하지 말고"))
        assertTrue(prompt.contains("뜻을 짧게 확인한 뒤에는 그 표현에 머물지 말고 사용자의 현재 말, 최근 주제, 관심사 중 가까운 쪽으로 대화를 이어 간다."))
        assertTrue(prompt.contains("이 단계의 모든 응답은 한국어 짧은 말과 일본어 아주 작은 단어/구를 함께 둔다."))
        assertTrue(prompt.contains("한국어 문장이 먼저 의미를 받치고, 일본어는 그 옆의 작은 조각으로만 붙는다."))
        assertTrue(prompt.contains("일본어 만으로 응답하지 않는다."))
        assertTrue(prompt.contains("언어 형식은 language_use를 따르고, 먼저 친구처럼 반응해 조금 이어 간 뒤 필요할 때만 의미를 바로 알 수 있는 작은 답 길을 둔다."))
        assertTrue(prompt.contains("아래 예시는 이 단계의 기본 말투다. 복사하지 말고 같은 길이와 섞임 리듬으로 말한다."))
        assertTrue(prompt.contains("선택지는 기본 말투가 아니라, 사용자가 막힐 때만 얹는 작은 발판이다."))
        assertTrue(prompt.contains("집 앞 산책 좋지. さんぽ. 바람도 좋았겠다."))
        assertTrue(prompt.contains("친구랑 통화했구나. ともだち. 같이 걸으면 덜 심심하지."))
        assertTrue(prompt.contains("막히면: 어디였어? 집 앞 / 공원. いえ / こうえん."))
        assertFalse(prompt.contains("일본어 단어 하나나 두 단어 표현"))
        assertFalse(prompt.contains("같이 말해"))
        assertFalse(prompt.contains("다시 한 번"))
        assertFalse(prompt.contains("해보자"))
        assertFalse(prompt.contains("연습"))
        assertFalse(prompt.contains("반복"))
        assertFalse(prompt.contains("문장 만들"))
        assertFalse(prompt.contains("고개"))
        assertFalse(prompt.contains("끄덕"))
        assertFalse(prompt.contains("よく寝た?"))
        assertFalse(prompt.contains("오늘은 천천히. ゆっくり."))
    }

    @Test
    fun `all conversation bands avoid non verbal stage directions`() {
        ConversationAbilityBand.entries.forEach { band ->
            val prompt = useCase(
                profile = profile(
                    conversationBand = band,
                    primaryBridge = PrimaryBridgePolicy.Brief,
                    confidence = ProfileConfidence.Medium
                ),
                primaryLang = LangCode.KO,
                selectedLang = LangCode.JA
            )

            // prompt를 늘리지 않고도, 자막에 괄호 지문을 만들 수 있는 비언어 동작 묘사만 차단한다.
            assertFalse(prompt.contains("고개"))
            assertFalse(prompt.contains("끄덕"))
            assertFalse(prompt.contains("몸짓"))
            assertFalse(prompt.contains("표정"))
            assertFalse(prompt.contains("무대 지시"))
        }
    }

    @Test
    fun `prompt carries compact recent context without copying previous habits`() {
        // 최근 맥락은 모델이 현재 발화를 직접 해석하는 근거이며, 이전 AI 응답 습관을 모방하라는 지시가 아니다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.SimpleSentence,
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

        assertTrue(prompt.contains("- USER: I apple hungry"))
        assertTrue(prompt.contains("- food and hunger"))
        assertTrue(prompt.contains("이전 AI의 응답 습관은 모방하지 않는다."))
    }

    @Test
    fun `prompt does not expose conversation evidence fields`() {
        // evidence는 StartSessionUseCase에서 profile로 해석된 뒤 들어오므로 prompt에는 raw evidence명이 노출되면 안 된다.
        val prompt = useCase(
            profile = profile(
                conversationBand = ConversationAbilityBand.ConnectedExpression,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                confidence = ProfileConfidence.High
            ),
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )

        assertTrue(prompt.contains("생각, 이유, 감정, 상황을 어느 정도 이어 말할 수 있다."))
        assertFalse(prompt.contains("targetLanguageComprehension"))
        assertFalse(prompt.contains("targetLanguageProduction"))
        assertFalse(prompt.contains("supportLanguageDependence"))
        assertFalse(prompt.contains("aiScaffoldingDependence"))
        assertFalse(prompt.contains("conversationSustainability"))
        assertFalse(prompt.contains("consistency"))
        assertFalse(prompt.contains("supportRequiredToContinue"))
        assertFalse(prompt.contains("userContributionLevel"))
        assertFalse(prompt.contains("responseDifficultyFit"))
        assertFalse(prompt.contains("debugRecommendedBand"))
        assertFalse(prompt.contains("GeminiConversationAnalysis"))
    }

    private fun profile(
        conversationBand: ConversationAbilityBand,
        primaryBridge: PrimaryBridgePolicy,
        confidence: ProfileConfidence = ProfileConfidence.Medium
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
                    primaryFocus = null,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Low,
                    observedCount = 0
                )
            ),
            chatPolicy = ChatAdaptationPolicy(
                // 6단계 band는 유지하되, prompt에는 단계명 대신 current_style 문장으로만 내려간다.
                conversationBand = conversationBand,
                intentSupport = IntentSupportPolicy.TrustMeaning,
                primaryBridge = primaryBridge,
                recastStyle = RecastStylePolicy.NaturalInline,
                expressionGrowth = ExpressionGrowthPolicy.OneEverydayExpression,
                questionLoad = QuestionLoadPolicy.OpenShort,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            ),
            // Correction 정책은 Chat 프롬프트 테스트 입력이 아니지만 profile 계약을 완성하기 위해 채운다.
            correctionPolicy = CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.EverydayNatural)
        )
    }
}
