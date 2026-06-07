package com.app.umma.data.repository.chatconversation

import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
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
              "targetLanguageComprehension": "NaturalFlow",
              "targetLanguageProduction": "ConnectedTurns",
              "supportLanguageDependence": "None",
              "aiScaffoldingDependence": "Low",
              "conversationSustainability": "SustainedNatural",
              "consistency": "Stable",
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
        assertEquals(TargetLanguageComprehensionEvidence.NaturalFlow, evidence.targetLanguageComprehension)
        assertEquals(TargetLanguageProductionEvidence.ConnectedTurns, evidence.targetLanguageProduction)
        assertEquals(LanguageDependenceEvidence.None, evidence.supportLanguageDependence)
        assertEquals(LanguageDependenceEvidence.Low, evidence.aiScaffoldingDependence)
        assertEquals(ConversationSustainabilityEvidence.SustainedNatural, evidence.conversationSustainability)
        assertEquals(ConversationConsistencyEvidence.Stable, evidence.consistency)
        assertEquals(ResponseDifficultyFitEvidence.Fits, evidence.responseDifficultyFit)
        assertEquals(ProfileConfidence.High, evidence.confidence)
        assertEquals(ChatConversationEvidenceSource.GeminiConversationAnalysis, evidence.source)
        assertEquals("session-1", evidence.sourceSessionId)
        assertEquals(ConversationAbilityBand.NuanceControl, evidence.debugRecommendedBand)
    }

    @Test
    fun `normalizes unescaped quotes inside reason summary`() {
        val rawJson = """
            {
              "targetLanguageComprehension": "WordLevel",
              "targetLanguageProduction": "WordsOrFragments",
              "supportLanguageDependence": "High",
              "aiScaffoldingDependence": "High",
              "conversationSustainability": "RequiresSupport",
              "consistency": "Mixed",
              "responseDifficultyFit": "TooHard",
              "confidence": "Low",
              "reasonSummary": "학습자는 AI가 제공한 일본어 단어("ただいま", "いい")를 주로 반복했습니다.",
              "debugRecommendedBand": "IntentOnly"
            }
        """.trimIndent()

        val evidence = mapper.map(
            rawJson = rawJson,
            selectedLang = LangCode.JA,
            sourceSessionId = "session-with-bad-summary"
        )

        // reasonSummary는 debug/review용 설명이므로 따옴표 때문에 전체 분석 저장이 실패하면 안 된다.
        assertEquals(ProfileConfidence.Low, evidence.confidence)
        assertEquals(ConversationAbilityBand.IntentOnly, evidence.debugRecommendedBand)
        assertEquals("학습자는 AI가 제공한 일본어 단어(ただいま, いい)를 주로 반복했습니다.", evidence.reasonSummary)
    }
}
