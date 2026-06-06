package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.SupportRequiredEvidence
import com.app.umma.domain.model.chat.UserContributionEvidence
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.VocabLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyChatConversationEvidenceUseCaseTest {
    private val useCase = ApplyChatConversationEvidenceUseCase()

    @Test
    fun `high support evidence keeps chat in intent only even when debug band is high`() {
        val evidence = evidence(
            // 대화 유지에 강한 보조가 필요했다면 debug 후보가 높아도 domain policy는 최하위로 보호해야 한다.
            conversationSustainability = ConversationSustainabilityEvidence.RequiresSupport,
            supportRequiredToContinue = SupportRequiredEvidence.High,
            userContributionLevel = UserContributionEvidence.WordsOrFragments,
            responseDifficultyFit = ResponseDifficultyFitEvidence.TooHard,
            confidence = ProfileConfidence.High,
            debugRecommendedBand = ConversationAbilityBand.NuanceControl
        )

        val result = useCase(baseProfile(), evidence)

        assertTrue(result.applied)
        assertEquals(ConversationAbilityBand.IntentOnly, result.calculatedBand)
        assertEquals(ConversationAbilityBand.IntentOnly, result.profile.chatPolicy.conversationBand)
    }

    @Test
    fun `connected conversation evidence calculates connected expression and ignores debug band`() {
        val evidence = evidence(
            // 사용자가 연결 발화를 안정적으로 만들었다는 근거가 있으면 debug 후보가 낮아도 domain policy가 상위 band를 계산한다.
            conversationSustainability = ConversationSustainabilityEvidence.SustainedNatural,
            supportRequiredToContinue = SupportRequiredEvidence.None,
            userContributionLevel = UserContributionEvidence.ConnectedTurns,
            responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
            confidence = ProfileConfidence.High,
            debugRecommendedBand = ConversationAbilityBand.IntentOnly
        )

        val result = useCase(baseProfile(), evidence)

        assertTrue(result.applied)
        assertEquals(ConversationAbilityBand.ConnectedExpression, result.calculatedBand)
        assertEquals(ProfileConfidence.High, result.profile.core.levelConfidence)
        assertEquals(SkillStage.Expanding, result.profile.core.fluencyStage)
    }

    @Test
    fun `low confidence evidence is not applied`() {
        val baseProfile = baseProfile()
        val evidence = evidence(
            // Low confidence는 review/debug에는 남길 수 있지만 실제 세션 profile을 바꾸면 band가 흔들릴 수 있다.
            confidence = ProfileConfidence.Low,
            debugRecommendedBand = ConversationAbilityBand.NuanceControl
        )

        val result = useCase(baseProfile, evidence)

        assertFalse(result.applied)
        assertEquals(baseProfile, result.profile)
    }

    @Test
    fun `expired evidence is not applied`() {
        val baseProfile = baseProfile()
        val evidence = evidence(
            // 만료된 evidence는 과거 세션 상황을 현재 능력처럼 재사용하지 않도록 제외한다.
            expiresAt = 1L,
            debugRecommendedBand = ConversationAbilityBand.NuanceControl
        )

        val result = useCase(baseProfile, evidence)

        assertFalse(result.applied)
        assertEquals(baseProfile, result.profile)
    }

    private fun evidence(
        conversationSustainability: ConversationSustainabilityEvidence = ConversationSustainabilityEvidence.SustainedSimple,
        supportRequiredToContinue: SupportRequiredEvidence = SupportRequiredEvidence.Low,
        userContributionLevel: UserContributionEvidence = UserContributionEvidence.SimpleSentences,
        responseDifficultyFit: ResponseDifficultyFitEvidence = ResponseDifficultyFitEvidence.Fits,
        confidence: ProfileConfidence = ProfileConfidence.Medium,
        debugRecommendedBand: ConversationAbilityBand? = null,
        expiresAt: Long? = null
    ): ChatConversationEvidence {
        return ChatConversationEvidence(
            selectedLang = LangCode.EN,
            conversationSustainability = conversationSustainability,
            supportRequiredToContinue = supportRequiredToContinue,
            userContributionLevel = userContributionLevel,
            responseDifficultyFit = responseDifficultyFit,
            confidence = confidence,
            source = ChatConversationEvidenceSource.ManualReview,
            debugRecommendedBand = debugRecommendedBand,
            expiresAt = expiresAt
        )
    }

    private fun baseProfile(): LearnerAdaptationProfile {
        return LearnerAdaptationProfile(
            core = LearnerAbilityProfile(
                cefrLevel = VocabLevel.A1,
                levelConfidence = ProfileConfidence.Low,
                grammarStage = SkillStage.Foundation,
                vocabularyStage = SkillStage.Foundation,
                fluencyStage = SkillStage.Foundation,
                naturalnessStage = SkillStage.Foundation,
                focus = LearningFocusSummary(
                    primaryFocus = null,
                    secondaryFocus = null,
                    confidence = ProfileConfidence.Low,
                    observedCount = 0
                )
            ),
            chatPolicy = ChatAdaptationPolicy.defaultsForBand(ConversationAbilityBand.IntentOnly),
            correctionPolicy = CorrectionGrowthPolicy.defaultsForBand(
                com.app.umma.domain.model.learningstate.CorrectionGrowthBand.MeaningFirst
            )
        )
    }
}
