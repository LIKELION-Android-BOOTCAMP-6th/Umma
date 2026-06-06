package com.app.umma.data.repository.chatconversation

import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.SupportRequiredEvidence
import com.app.umma.domain.model.chat.UserContributionEvidence
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.ProfileConfidence
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatConversationEvidenceResponseMapperTest {
    private val mapper = ChatConversationEvidenceResponseMapper()

    @Test
    fun `maps gemini json to conversation evidence without treating debug band as source of truth`() {
        val rawJson = """
            {
              "conversationSustainability": "SustainedNatural",
              "supportRequiredToContinue": "None",
              "userContributionLevel": "ConnectedTurns",
              "responseDifficultyFit": "Fits",
              "confidence": "High",
              "reasonSummary": "학습언어 대화가 안정적으로 이어졌다.",
              "debugRecommendedBand": "NuanceControl"
            }
        """.trimIndent()

        val evidence = mapper.map(
            rawJson = rawJson,
            selectedLang = LangCode.EN,
            sourceSessionId = "session-1"
        )

        // mapper는 Gemini JSON을 evidence로만 변환한다. debugRecommendedBand는 저장되지만 적용 책임은 없다.
        assertEquals(LangCode.EN, evidence.selectedLang)
        assertEquals(ConversationSustainabilityEvidence.SustainedNatural, evidence.conversationSustainability)
        assertEquals(SupportRequiredEvidence.None, evidence.supportRequiredToContinue)
        assertEquals(UserContributionEvidence.ConnectedTurns, evidence.userContributionLevel)
        assertEquals(ResponseDifficultyFitEvidence.Fits, evidence.responseDifficultyFit)
        assertEquals(ProfileConfidence.High, evidence.confidence)
        assertEquals(ChatConversationEvidenceSource.GeminiConversationAnalysis, evidence.source)
        assertEquals("session-1", evidence.sourceSessionId)
        assertEquals(ConversationAbilityBand.NuanceControl, evidence.debugRecommendedBand)
    }
}
