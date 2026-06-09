package com.app.umma.domain.usecase.statistics

import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.learningstate.toLangAbilityStats
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuildStatisticsHistoryUseCaseTest {

    private val profileUseCase = BuildLearnerAdaptationProfileUseCase()
    private val useCase = BuildStatisticsHistoryUseCase(
        buildLearnerAdaptationProfileUseCase = profileUseCase
    )

    @Test
    fun `builds history from same ability stats used by summary cards`() {
        val savedState = LangState.initial(LangCode.EN).copy(
            // external 값은 일부러 ability stats와 다르게 둔다.
            // history가 이 값을 그대로 저장하면 카드와 차트의 최신 점이 달라지는 회귀가 재현된다.
            external = LangState.initial(LangCode.EN).external.copy(
                vocabularyLevel = VocabLevel.B2,
                grammarAccuracy = 0.11,
                expressionRange = 6,
                fluencyScore = 0.22,
                naturalnessScore = 0.33
            ),
            // internal과 analysisMeta는 카드가 읽는 LangAbilityStats의 실제 입력이다.
            // 따라서 history도 이 입력에서 나온 값으로 저장되어야 한다.
            internal = InternalMetrics.initial().copy(
                grammarAccuracy = 0.73,
                speechRate = 0.60,
                pauseFrequency = 0.20,
                spokenNaturalness = 0.70,
                naturalExpressionUsage = 0.50
            ),
            analysisMeta = LangStateAnalysisMeta(
                metricEvidence = mapOf(
                    LearningMetricKey.GrammarAccuracy to grammarEvidence()
                ),
                activeFocus = emptyList(),
                lastSignalAt = 1_700_000_000_000L,
                lastChatAnalysisEventId = "chat-session-1",
                chatEvidenceSummary = chatEvidence()
            )
        )
        val updateResult = LearningStateUpdateResult(
            lang = LangCode.EN,
            savedState = savedState,
            sourceEventId = "analysis-123",
            applied = true,
            updatedAt = 1_700_000_000_000L
        )
        val expectedStats = savedState.toLangAbilityStats(profileUseCase(savedState))

        val history = useCase(userId = "user-1", updateResult = updateResult)

        // history의 최신 snapshot은 Statistics 카드와 같은 ability stats를 써야 한다.
        // 이 보장이 깨지면 카드 현재값과 차트 마지막 점이 서로 다른 숫자로 보인다.
        assertEquals("user-1_en_analysis-123", history.id)
        assertEquals("user-1", history.userId)
        assertEquals(LangCode.EN, history.language)
        assertEquals(1_700_000_000_000L, history.recordedAt)
        assertEquals(expectedStats.conversation.currentBand, history.conversationBand)
        assertEquals(VocabLevel.B2, history.vocabularyLevel)
        assertEquals(expectedStats.grammar.score, history.grammarAccuracy)
        assertEquals(6, history.expressionRange)
        assertEquals(expectedStats.speaking.score, history.fluencyScore)
        assertEquals(expectedStats.comprehension.score, history.naturalnessScore)
        assertEquals("analysis-123", history.sourceEventId)
        assertEquals(SyncStatus.PENDING, history.syncStatus)
        assertFalse(history.id.isBlank())
    }

    @Test
    fun `does not record conversation band when chat evidence is missing`() {
        val savedState = LangState.initial(LangCode.EN).copy(
            // external에는 값이 있어도 chat evidence가 없으면 카드의 종합 레벨은 아직 측정 준비 중이다.
            // history도 임의의 기본 band를 찍지 않아야 초기 사용자가 실제 레벨을 가진 것처럼 보이지 않는다.
            external = LangState.initial(LangCode.EN).external.copy(
                grammarAccuracy = 0.5,
                fluencyScore = 0.5,
                naturalnessScore = 0.5
            )
        )
        val updateResult = LearningStateUpdateResult(
            lang = LangCode.EN,
            savedState = savedState,
            sourceEventId = "initial-analysis",
            applied = true,
            updatedAt = 1_700_000_000_000L
        )

        val history = useCase(userId = "user-1", updateResult = updateResult)

        // ConversationBand는 nullable field이므로 근거가 없을 때 null로 남길 수 있다.
        // 이 null이 chart mapper에서 제외되어 "측정 준비 중" 상태와 맞게 동작한다.
        assertEquals(null, history.conversationBand)
        assertEquals(0.5, history.grammarAccuracy, 0.0)
        assertEquals(0.5, history.fluencyScore, 0.0)
        assertEquals(0.5, history.naturalnessScore, 0.0)
    }

    private fun grammarEvidence(): MetricEvidence {
        // 문법 카드는 evidence가 있을 때만 score를 노출하므로, history 테스트도 같은 조건을 만든다.
        return MetricEvidence(
            observedCount = 3,
            confidence = 0.82,
            sourceTypes = setOf(LearningSignalSource.CorrectionSignal),
            direction = EvidenceDirection.Up,
            directionCount = 3,
            lastObservedAt = 1_700_000_000_000L
        )
    }

    private fun chatEvidence(): ChatEvidenceSummary {
        // 말하기/이해력/종합 레벨 카드는 Chat evidence가 있을 때만 측정값을 노출한다.
        // fixture는 실제 대화 분석이 누적된 상태를 재현한다.
        return ChatEvidenceSummary(
            targetLanguageComprehension = TargetLanguageComprehensionEvidence.SimpleSentence,
            targetLanguageProduction = TargetLanguageProductionEvidence.SimpleSentences,
            supportLanguageDependence = LanguageDependenceEvidence.Medium,
            aiScaffoldingDependence = LanguageDependenceEvidence.Medium,
            conversationSustainability = ConversationSustainabilityEvidence.SustainedSimple,
            consistency = ConversationConsistencyEvidence.Stable,
            responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
            confidence = ProfileConfidence.Medium,
            observedCount = 3,
            lastObservedAt = 1_700_000_000_000L
        )
    }
}
