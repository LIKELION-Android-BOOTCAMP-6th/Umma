package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.ChallengeLevel
import com.app.umma.domain.model.learningstate.CorrectionStylePolicy
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.GrammarStrategyPolicy
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.LearningFocus
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpokenRegisterStrategy
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.learningstate.VocabularyStrategyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BuildLearnerAdaptationProfileUseCaseTest {
    private val useCase = BuildLearnerAdaptationProfileUseCase()

    @Test
    fun `null lang state creates conservative beginner safe profile`() {
        // LangState가 없다는 것은 실력이 낮다는 뜻이 아니라 분석 근거가 없다는 뜻이다.
        val profile = useCase(null)

        // 근거가 없으면 짧고 구체적인 대화를 우선해 과한 challenge를 막는다.
        assertEquals(ProfileConfidence.Low, profile.core.levelConfidence)
        assertEquals(SkillStage.Foundation, profile.core.grammarStage)
        assertEquals(ChallengeLevel.Support, profile.chatPolicy.challengeLevel)
        assertEquals(ResponseLengthPolicy.OneShortSentence, profile.chatPolicy.responseLength)
        assertEquals(QuestionStylePolicy.OneConcreteQuestion, profile.chatPolicy.questionStyle)
        assertEquals(PrimaryLanguageSupportPolicy.PrimaryLanguageFirst, profile.chatPolicy.primaryLanguageSupport)
        assertEquals(CorrectionStylePolicy.MinimalFix, profile.correctionPolicy.correctionStyle)
        assertEquals(GrammarStrategyPolicy.FixBlockingErrorOnly, profile.correctionPolicy.grammarStrategy)
    }

    @Test
    fun `initial lang state is low confidence and does not expose raw metrics`() {
        // initial snapshot은 아직 반복 관측이 없으므로 "실력 판정"보다 "보수적 fallback"이 먼저다.
        val profile = useCase(LangState.initial(LangCode.EN))

        // initial 값은 A1 확정이 아니라 low-confidence fallback으로 해석한다.
        assertEquals(ProfileConfidence.Low, profile.core.levelConfidence)
        assertEquals(ChallengeLevel.Support, profile.chatPolicy.challengeLevel)
        assertEquals(ChallengeLevel.Support, profile.correctionPolicy.challengeLevel)
        assertNull(profile.core.focus.primaryFocus)
        assertNull(profile.core.focus.secondaryFocus)

        // profile은 raw metric 숫자를 담지 않고 stage/policy enum만 제공한다.
        assertFalse(profile.toString().contains("grammarAccuracy"))
        assertFalse(profile.toString().contains("fluencyScore"))
        assertFalse(profile.toString().contains("naturalnessScore"))
    }

    @Test
    fun `high score mixed metrics keep challenge conservative`() {
        // grammar는 높지만 fluency가 낮은 상태를 만들어, 일부 고점만으로 challenge를 올리지 않는지 확인한다.
        val state = analyzedState(
            internal = InternalMetrics(
                grammarAccuracy = 0.92,
                vocabularyAppropriateness = 0.88,
                lexicalDiversity = 0.82,
                vocabularyLevel = VocabLevel.C1,
                sentenceComplexity = 0.86,
                speechRate = 0.18,
                pauseFrequency = 0.8,
                avgUtteranceLength = 0.2,
                spokenNaturalness = 0.84,
                naturalExpressionUsage = 0.82,
                errorRecurrence = 0.1,
                reviewRetention = 0.8
            ),
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.C1,
                grammarAccuracy = 0.92,
                expressionRange = 90,
                fluencyScore = 0.18,
                naturalnessScore = 0.84
            ),
            evidence = highConfidenceEvidence()
        )

        val profile = useCase(state)

        // 일부 점수가 높아도 fluency가 크게 낮으면 전체 challenge를 올리지 않는다.
        assertEquals(ProfileConfidence.Low, profile.core.levelConfidence)
        assertEquals(SkillStage.Refined, profile.core.grammarStage)
        assertEquals(SkillStage.Foundation, profile.core.fluencyStage)
        assertEquals(ChallengeLevel.Support, profile.chatPolicy.challengeLevel)
        assertEquals(ChallengeLevel.Support, profile.correctionPolicy.challengeLevel)
    }

    @Test
    fun `stages are calculated independently for each skill area`() {
        // 각 영역이 서로 다른 값을 가져야 총점 하나로 뭉개지지 않는다는 점을 검증한다.
        val state = analyzedState(
            internal = InternalMetrics(
                grammarAccuracy = 0.32,
                vocabularyAppropriateness = 0.56,
                lexicalDiversity = 0.62,
                vocabularyLevel = VocabLevel.B1,
                sentenceComplexity = 0.5,
                speechRate = 0.7,
                pauseFrequency = 0.15,
                avgUtteranceLength = 0.72,
                spokenNaturalness = 0.76,
                naturalExpressionUsage = 0.78,
                errorRecurrence = 0.2,
                reviewRetention = 0.7
            ),
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.B1,
                grammarAccuracy = 0.32,
                expressionRange = 45,
                fluencyScore = 0.7,
                naturalnessScore = 0.78
            ),
            evidence = mediumConfidenceEvidence()
        )

        val profile = useCase(state)

        // 하나의 총점이 아니라 영역별 stage가 다르게 산출되어야 한다.
        assertEquals(SkillStage.Developing, profile.core.grammarStage)
        assertEquals(SkillStage.Stable, profile.core.vocabularyStage)
        assertEquals(SkillStage.Expanding, profile.core.fluencyStage)
        assertEquals(SkillStage.Expanding, profile.core.naturalnessStage)
        assertEquals(ChallengeLevel.Match, profile.chatPolicy.challengeLevel)
    }

    @Test
    fun `active focus is summarized to top one or two items`() {
        // confidence와 반복 횟수가 충분한 focus만 남기고, 낮은 confidence focus는 요약에서 제외한다.
        val state = analyzedState(
            activeFocus = listOf(
                focus(LearningFocusType.Article, observedCount = 4, confidence = 0.82, lastObservedAt = 4_000L),
                focus(LearningFocusType.Tense, observedCount = 3, confidence = 0.74, lastObservedAt = 3_000L),
                focus(LearningFocusType.WordOrder, observedCount = 8, confidence = 0.2, lastObservedAt = 5_000L),
                focus(LearningFocusType.Preposition, observedCount = 2, confidence = 0.61, lastObservedAt = 1_000L)
            ),
            evidence = highConfidenceEvidence()
        )

        val profile = useCase(state)

        // confidence가 낮은 WordOrder는 제외하고, 상위 두 focus만 profile에 노출한다.
        assertEquals(LearningFocusType.Article, profile.core.focus.primaryFocus)
        assertEquals(LearningFocusType.Tense, profile.core.focus.secondaryFocus)
        assertEquals(7, profile.core.focus.observedCount)
        assertEquals(ProfileConfidence.High, profile.core.focus.confidence)
    }

    @Test
    fun `chat and correction policies share the same core ability profile`() {
        // 같은 core 판단을 기반으로 하되, 각 기능은 필요한 출력 정책만 다르게 가져가야 한다.
        val state = analyzedState(
            internal = InternalMetrics(
                grammarAccuracy = 0.7,
                vocabularyAppropriateness = 0.73,
                lexicalDiversity = 0.72,
                vocabularyLevel = VocabLevel.B2,
                sentenceComplexity = 0.71,
                speechRate = 0.74,
                pauseFrequency = 0.1,
                avgUtteranceLength = 0.76,
                spokenNaturalness = 0.78,
                naturalExpressionUsage = 0.75,
                errorRecurrence = 0.1,
                reviewRetention = 0.8
            ),
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.B2,
                grammarAccuracy = 0.7,
                expressionRange = 60,
                fluencyScore = 0.78,
                naturalnessScore = 0.77
            ),
            evidence = highConfidenceEvidence()
        )

        val profile = useCase(state)

        // Chat과 Correction은 같은 core 판단을 공유하되, 각 기능에 맞는 정책 enum만 다르게 가진다.
        assertEquals(ProfileConfidence.High, profile.core.levelConfidence)
        assertEquals(ChallengeLevel.Stretch, profile.chatPolicy.challengeLevel)
        assertEquals(ChallengeLevel.Stretch, profile.correctionPolicy.challengeLevel)
        assertEquals(ResponseLengthPolicy.NaturalBrief, profile.chatPolicy.responseLength)
        assertEquals(VocabularyStrategyPolicy.ImproveCollocation, profile.correctionPolicy.vocabularyStrategy)
        assertEquals(SpokenRegisterStrategy.NativeLikeCasual, profile.correctionPolicy.spokenRegisterStrategy)
    }

    @Test
    fun `profile does not store primary or selected language`() {
        // primaryLang/selectedLang은 prompt builder 입력이지 profile 저장 필드가 아니다.
        val profile = useCase(analyzedState())

        // primaryLanguageSupport는 "보조 설명 정책"이고, 실제 primaryLang 값 저장과는 다르다.
        assertFalse(profile.toString().contains("primaryLang="))
        // selectedLang은 prompt builder 입력으로만 쓰이므로 profile 데이터 문자열에도 필드로 나타나면 안 된다.
        assertFalse(profile.toString().contains("selectedLang="))
    }

    private fun analyzedState(
        internal: InternalMetrics = InternalMetrics(
            grammarAccuracy = 0.58,
            vocabularyAppropriateness = 0.6,
            lexicalDiversity = 0.62,
            vocabularyLevel = VocabLevel.B1,
            sentenceComplexity = 0.55,
            speechRate = 0.58,
            pauseFrequency = 0.2,
            avgUtteranceLength = 0.6,
            spokenNaturalness = 0.59,
            naturalExpressionUsage = 0.57,
            errorRecurrence = 0.2,
            reviewRetention = 0.7
        ),
        external: ExternalMetrics = ExternalMetrics(
            vocabularyLevel = internal.vocabularyLevel,
            grammarAccuracy = internal.grammarAccuracy,
            expressionRange = 40,
            fluencyScore = 0.62,
            naturalnessScore = 0.58
        ),
        evidence: Map<LearningMetricKey, MetricEvidence> = mediumConfidenceEvidence(),
        activeFocus: List<LearningFocus> = emptyList()
    ): LangState {
        // profile 테스트는 저장소를 거치지 않고 LangState snapshot 해석만 검증한다.
        // createdAt/updatedAt은 profile 계산에 직접 쓰이지 않지만, fixture가 실제 snapshot처럼 보이도록 유지한다.
        return LangState.initial(LangCode.EN, createdAt = 1_000L, updatedAt = 2_000L).copy(
            internal = internal,
            external = external,
            analysisMeta = LangStateAnalysisMeta(
                metricEvidence = evidence,
                activeFocus = activeFocus,
                lastSignalAt = 2_000L
            ),
            lastAnalyzedAt = 2_000L
        )
    }

    private fun mediumConfidenceEvidence(): Map<LearningMetricKey, MetricEvidence> {
        // medium 근거는 "일부 반복 관측은 있지만 high challenge를 확정하기엔 부족한" 상태를 만든다.
        return mapOf(
            LearningMetricKey.GrammarAccuracy to evidence(
                observedCount = 2,
                confidence = 0.62
            )
        )
    }

    private fun highConfidenceEvidence(): Map<LearningMetricKey, MetricEvidence> {
        // high 근거는 여러 metric에 반복 관측이 쌓인 상태를 재현한다.
        return mapOf(
            LearningMetricKey.GrammarAccuracy to evidence(3, 0.86),
            LearningMetricKey.VocabularyAppropriateness to evidence(2, 0.8),
            LearningMetricKey.SpokenNaturalness to evidence(2, 0.78)
        )
    }

    private fun evidence(
        observedCount: Int,
        confidence: Double
    ): MetricEvidence {
        // sourceTypes와 direction은 "어떤 경로에서 어떤 방향으로 누적됐는지"를 profile이 나중에 다시 해석할 수 있게 남긴다.
        return MetricEvidence(
            observedCount = observedCount,
            confidence = confidence,
            sourceTypes = setOf(LearningSignalSource.CorrectionSignal),
            direction = EvidenceDirection.Up,
            directionCount = observedCount,
            lastObservedAt = 2_000L
        )
    }

    private fun focus(
        type: LearningFocusType,
        observedCount: Int,
        confidence: Double,
        lastObservedAt: Long
    ): LearningFocus {
        // firstObservedAt/lastObservedAt은 confidence와 최근성 순서를 재현하기 위한 fixture 값이다.
        return LearningFocus(
            type = type,
            observedCount = observedCount,
            confidence = confidence,
            firstObservedAt = 1_000L,
            lastObservedAt = lastObservedAt
        )
    }
}
