package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ChatTurnAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
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

class BuildChatSpeechSpeedUseCaseTest {
    private val useCase = BuildChatSpeechSpeedUseCase()

    @Test
    fun `low confidence learner uses first session safe speech speed`() {
        // 첫 학습 언어처럼 근거가 부족하면 첫 발화가 아주 낮은 수준일 수 있어 가장 안전한 속도를 우선한다.
        val speed = useCase(
            profile(
                confidence = ProfileConfidence.Low,
                stage = SkillStage.Stable,
                speechSpeed = SpeechSpeedPolicy.Advanced
            )
        )

        assertEquals(0.8, speed, 0.0)
    }

    @Test
    fun `weakest stage keeps speech speed conservative`() {
        // 한 영역만 기초 단계여도 음성은 전체 이해 속도에 맞춰 느리게 유지해야 한다.
        val speed = useCase(
            profile(
                confidence = ProfileConfidence.High,
                stage = SkillStage.Foundation,
                speechSpeed = SpeechSpeedPolicy.Advanced
            )
        )

        assertEquals(0.85, speed, 0.0)
    }

    @Test
    fun `advanced learner can use slightly faster but capped speed`() {
        // 고급 사용자에게도 학습용 음성은 너무 빠르면 안 되므로 상한을 둔다.
        val speed = useCase(
            profile(
                confidence = ProfileConfidence.High,
                stage = SkillStage.Refined,
                speechSpeed = SpeechSpeedPolicy.Advanced
            )
        )

        assertEquals(1.1, speed, 0.0)
    }

    @Test
    fun `low confidence fluent turn can recover from first session safe speed`() {
        // LangState 근거가 없더라도 현재 발화가 충분히 유창하면 첫 세션 safe speed에 계속 고정하지 않는다.
        val speed = useCase(
            profile = profile(
                confidence = ProfileConfidence.Low,
                stage = SkillStage.Stable,
                speechSpeed = SpeechSpeedPolicy.SlowBeginner
            ),
            turnPolicy = ChatTurnAdaptationPolicy(
                responseLength = ResponseLengthPolicy.NaturalBrief,
                sentenceDensity = SentenceDensityPolicy.NaturalBrief,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                questionLoad = QuestionLoadPolicy.OpenShort,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            )
        )

        assertEquals(1.0, speed, 0.0)
    }

    private fun profile(
        confidence: ProfileConfidence,
        stage: SkillStage,
        speechSpeed: SpeechSpeedPolicy
    ): LearnerAdaptationProfile {
        // speed 정책은 core stage와 chat speechSpeed만 보므로 나머지 값은 최소 fixture로 채운다.
        return LearnerAdaptationProfile(
            core = LearnerAbilityProfile(
                cefrLevel = VocabLevel.B1,
                levelConfidence = confidence,
                grammarStage = stage,
                vocabularyStage = stage,
                fluencyStage = stage,
                naturalnessStage = stage,
                focus = LearningFocusSummary(
                    primaryFocus = null,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Low,
                    observedCount = 0
                )
            ),
            chatPolicy = ChatAdaptationPolicy(
                // 이 테스트는 band 산출이 아니라 speed clamp만 보므로 가장 빠른 band로 고정해 보수 조정 여부를 선명하게 만든다.
                conversationBand = ConversationAbilityBand.NuanceControl,
                // intent/recast/growth 계열은 속도 계산 입력이 아니므로 고급 사용자 기본값으로 채워 의존하지 않음을 드러낸다.
                intentSupport = IntentSupportPolicy.FollowUserLead,
                primaryBridge = PrimaryBridgePolicy.None,
                recastStyle = RecastStylePolicy.NuanceOnly,
                expressionGrowth = ExpressionGrowthPolicy.OneNativeLikeChoice,
                questionLoad = QuestionLoadPolicy.NuanceFollowUp,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                // 실제 검증 대상은 이 speechSpeed 값과 core weakest stage 중 더 보수적인 값이 선택되는지다.
                speechSpeed = speechSpeed
            ),
            // Correction 정책은 speed usecase 입력이 아니지만 profile 계약을 완성하기 위해 채운다.
            correctionPolicy = CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.NuanceRefine)
        )
    }
}
