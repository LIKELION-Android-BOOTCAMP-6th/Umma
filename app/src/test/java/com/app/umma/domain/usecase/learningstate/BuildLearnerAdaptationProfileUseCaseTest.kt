package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.LearningFocus
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.VocabLevel
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
        assertEquals(ConversationAbilityBand.IntentOnly, profile.chatPolicy.conversationBand)
        assertEquals(ResponseLengthPolicy.OneShortSentence, profile.chatPolicy.responseLength)
        assertEquals(QuestionLoadPolicy.ConcreteChoice, profile.chatPolicy.questionLoad)
        assertEquals(PrimaryBridgePolicy.Active, profile.chatPolicy.primaryBridge)
        // 근거 없음 → MeaningFirst band (COR-TUNE-003).
        assertEquals(CorrectionGrowthBand.MeaningFirst, profile.correctionPolicy.band)
    }

    @Test
    fun `initial lang state is low confidence and does not expose raw metrics`() {
        // initial snapshot은 아직 반복 관측이 없으므로 "실력 판정"보다 "보수적 fallback"이 먼저다.
        val profile = useCase(LangState.initial(LangCode.EN))

        // initial 값은 A1 확정이 아니라 low-confidence fallback으로 해석한다.
        assertEquals(ProfileConfidence.Low, profile.core.levelConfidence)
        assertEquals(ConversationAbilityBand.IntentOnly, profile.chatPolicy.conversationBand)
        // initial LangState → low confidence → MeaningFirst band (COR-TUNE-003).
        assertEquals(CorrectionGrowthBand.MeaningFirst, profile.correctionPolicy.band)
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
        assertEquals(ConversationAbilityBand.IntentOnly, profile.chatPolicy.conversationBand)
        // low confidence이지만 grammarStage=Refined, vocabularyStage=Refined → PatternFix(낮은 band 보수 조정, 단일 고점 미상승).
        assertEquals(CorrectionGrowthBand.PatternFix, profile.correctionPolicy.band)
    }

    @Test
    fun `fixed phrase evidence without sentence response ability stays intent only`() {
        // 단어와 고정 표현을 일부 기억해도 짧은 자유 문장과 대화 반응 근거가 없으면 0단계 대화 리드가 필요하다.
        val state = analyzedState(
            internal = InternalMetrics(
                grammarAccuracy = 0.18,
                vocabularyAppropriateness = 0.36,
                lexicalDiversity = 0.28,
                vocabularyLevel = VocabLevel.A1,
                sentenceComplexity = 0.12,
                speechRate = 0.2,
                pauseFrequency = 0.86,
                avgUtteranceLength = 0.14,
                spokenNaturalness = 0.18,
                naturalExpressionUsage = 0.16,
                errorRecurrence = 0.7,
                reviewRetention = 0.2
            ),
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.A1,
                grammarAccuracy = 0.18,
                expressionRange = 18,
                fluencyScore = 0.18,
                naturalnessScore = 0.18
            ),
            evidence = mediumConfidenceEvidence()
        )

        val profile = useCase(state)

        // CHAT-TUNE-005: "단어를 조금 앎"만으로 PhraseEmerging에 올리지 않고 AI가 대화를 대부분 리드한다.
        assertEquals(SkillStage.Foundation, profile.core.grammarStage)
        assertEquals(SkillStage.Foundation, profile.core.fluencyStage)
        assertEquals(ConversationAbilityBand.IntentOnly, profile.chatPolicy.conversationBand)
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
        assertEquals(ConversationAbilityBand.IntentOnly, profile.chatPolicy.conversationBand)
    }

    @Test
    fun `chat profile ignores external display scores but keeps expression range exception`() {
        // external은 사용자 표시용 projection이므로, 내부 분석용 profile을 끌어올리는 근거가 되면 안 된다.
        val state = analyzedState(
            internal = InternalMetrics(
                grammarAccuracy = 0.22,
                vocabularyAppropriateness = 0.24,
                lexicalDiversity = 0.2,
                vocabularyLevel = VocabLevel.A1,
                sentenceComplexity = 0.18,
                speechRate = 0.16,
                pauseFrequency = 0.82,
                avgUtteranceLength = 0.18,
                spokenNaturalness = 0.2,
                naturalExpressionUsage = 0.18,
                errorRecurrence = 0.7,
                reviewRetention = 0.2
            ),
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.C2,
                grammarAccuracy = 0.98,
                expressionRange = 120,
                fluencyScore = 0.96,
                naturalnessScore = 0.97
            ),
            evidence = highConfidenceEvidence()
        )

        val profile = useCase(state)

        // expressionRange는 누적 표현 폭 source라 vocabulary에는 제한적으로 반영된다.
        assertEquals(SkillStage.Stable, profile.core.vocabularyStage)
        // 나머지 표시용 external 점수가 높아도 Chat/Correction profile은 internal 병목을 기준으로 보수적으로 유지된다.
        assertEquals(SkillStage.Foundation, profile.core.grammarStage)
        assertEquals(SkillStage.Foundation, profile.core.fluencyStage)
        assertEquals(SkillStage.Foundation, profile.core.naturalnessStage)
        // CHAT-TUNE-005: vocabulary만 높아 보이고 문장/대화 지속 근거가 없으면 아직 AI가 흐름을 대부분 리드해야 한다.
        assertEquals(ConversationAbilityBand.IntentOnly, profile.chatPolicy.conversationBand)
        // grammarStage=Foundation → PatternFix (vocabulary=Stable이 있어 MeaningFirst보다 한 단계 위).
        assertEquals(CorrectionGrowthBand.PatternFix, profile.correctionPolicy.band)
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

        // Chat은 summary가 없으면 first selectedLang fallback을 쓰고, Correction은 기존 metric/evidence로 band를 계산한다.
        assertEquals(ProfileConfidence.High, profile.core.levelConfidence)
        assertEquals(ConversationAbilityBand.IntentOnly, profile.chatPolicy.conversationBand)
        // grammar=Stable, vocabulary=Expanding, fluency=Expanding, naturalness=Expanding → ConnectedExpression band.
        assertEquals(CorrectionGrowthBand.ConnectedExpression, profile.correctionPolicy.band)
        assertEquals(ResponseLengthPolicy.OneShortSentence, profile.chatPolicy.responseLength)
    }

    @Test
    fun `chat policy uses chat evidence summary as official band source`() {
        val state = analyzedState(
            internal = refinedInternalMetrics(),
            external = refinedExternalMetrics(),
            evidence = highConfidenceEvidence(),
            chatEvidenceSummary = ChatEvidenceSummary(
                targetLanguageComprehension = TargetLanguageComprehensionEvidence.NaturalFlow,
                targetLanguageProduction = TargetLanguageProductionEvidence.ConnectedTurns,
                supportLanguageDependence = LanguageDependenceEvidence.None,
                aiScaffoldingDependence = LanguageDependenceEvidence.Low,
                conversationSustainability = ConversationSustainabilityEvidence.SustainedNatural,
                consistency = ConversationConsistencyEvidence.Stable,
                responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
                confidence = ProfileConfidence.High,
                observedCount = 2,
                lastObservedAt = 3_000L
            )
        )

        val profile = useCase(state)

        // Chat band는 internal/external metric이 아니라 Chat evidence summary의 대화 지속 능력 근거로 산출한다.
        assertEquals(ConversationAbilityBand.ConnectedExpression, profile.chatPolicy.conversationBand)
        assertEquals(CorrectionGrowthBand.NuanceRefine, profile.correctionPolicy.band)
    }

    @Test
    fun `low confidence chat summary is capped to low conversation band`() {
        val state = analyzedState(
            internal = refinedInternalMetrics(),
            external = refinedExternalMetrics(),
            evidence = highConfidenceEvidence(),
            chatEvidenceSummary = ChatEvidenceSummary(
                // evidence body만 보면 높은 band 후보지만, Low confidence는 분석 신뢰도가 낮다는 뜻이다.
                targetLanguageComprehension = TargetLanguageComprehensionEvidence.NaturalFlow,
                targetLanguageProduction = TargetLanguageProductionEvidence.ConnectedTurns,
                supportLanguageDependence = LanguageDependenceEvidence.None,
                aiScaffoldingDependence = LanguageDependenceEvidence.Low,
                conversationSustainability = ConversationSustainabilityEvidence.SustainedNatural,
                consistency = ConversationConsistencyEvidence.Stable,
                responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
                confidence = ProfileConfidence.Low,
                observedCount = 1,
                lastObservedAt = 3_000L
            )
        )

        val profile = useCase(state)

        // Low summary는 저장/추적은 하되, 다음 세션 난이도를 높은 band로 올리지는 못하게 제한한다.
        assertEquals(ConversationAbilityBand.PhraseEmerging, profile.chatPolicy.conversationBand)
        // Correction은 Chat summary를 읽지 않으므로 기존 metric/evidence 기반 판단을 유지한다.
        assertEquals(CorrectionGrowthBand.NuanceRefine, profile.correctionPolicy.band)
    }

    @Test
    fun `high confidence all refined gives NuanceRefine correction band`() {
        // naturalness=Refined + vocabulary=Refined + High confidence → NuanceRefine.
        val state = analyzedState(
            internal = InternalMetrics(
                grammarAccuracy = 0.88,
                vocabularyAppropriateness = 0.91,
                lexicalDiversity = 0.87,
                vocabularyLevel = VocabLevel.C1,
                sentenceComplexity = 0.86,
                speechRate = 0.8,
                pauseFrequency = 0.08,
                avgUtteranceLength = 0.84,
                spokenNaturalness = 0.9,
                naturalExpressionUsage = 0.88,
                errorRecurrence = 0.05,
                reviewRetention = 0.9
            ),
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.C1,
                grammarAccuracy = 0.88,
                expressionRange = 75,
                fluencyScore = 0.85,
                naturalnessScore = 0.89
            ),
            evidence = highConfidenceEvidence()
        )

        val profile = useCase(state)

        assertEquals(CorrectionGrowthBand.NuanceRefine, profile.correctionPolicy.band)
    }

    @Test
    fun `meaning blocking focus prioritises PatternFix over naturalness band`() {
        // SentenceFragment focus가 쌓인 상태에서는 자연스러움 개선보다 패턴 안정화를 우선한다.
        val state = analyzedState(
            internal = InternalMetrics(
                grammarAccuracy = 0.68,
                vocabularyAppropriateness = 0.7,
                lexicalDiversity = 0.65,
                vocabularyLevel = VocabLevel.B1,
                sentenceComplexity = 0.62,
                speechRate = 0.65,
                pauseFrequency = 0.18,
                avgUtteranceLength = 0.68,
                spokenNaturalness = 0.66,
                naturalExpressionUsage = 0.63,
                errorRecurrence = 0.3,
                reviewRetention = 0.6
            ),
            external = ExternalMetrics(
                vocabularyLevel = VocabLevel.B1,
                grammarAccuracy = 0.68,
                expressionRange = 42,
                fluencyScore = 0.66,
                naturalnessScore = 0.65
            ),
            activeFocus = listOf(
                focus(LearningFocusType.SentenceFragment, observedCount = 5, confidence = 0.78, lastObservedAt = 3_000L)
            ),
            evidence = mediumConfidenceEvidence()
        )

        val profile = useCase(state)

        // SentenceFragment focus가 있고 grammarStage > Foundation → PatternFix 우선.
        assertEquals(CorrectionGrowthBand.PatternFix, profile.correctionPolicy.band)
    }

    @Test
    fun `mixed metric evidence keeps correction band conservative`() {
        // 숫자 metric은 높아 보여도 evidence 방향이 Mixed면 성장 방향 자체가 충돌한 상태다.
        // 이 경우 NuanceRefine처럼 높은 교정을 주면 사용자가 따라갈 수 없는 rewrite가 될 수 있다.
        val state = analyzedState(
            internal = refinedInternalMetrics(),
            external = refinedExternalMetrics(),
            evidence = mapOf(
                LearningMetricKey.GrammarAccuracy to evidence(
                    observedCount = 3,
                    confidence = 0.86,
                    direction = EvidenceDirection.Mixed
                ),
                LearningMetricKey.VocabularyAppropriateness to evidence(3, 0.84),
                LearningMetricKey.SpokenNaturalness to evidence(3, 0.82)
            )
        )

        val profile = useCase(state)

        // estimateProfileConfidence가 Mixed를 Low로 낮추고, correction band도 낮은 단계로 보수화한다.
        assertEquals(ProfileConfidence.Low, profile.core.levelConfidence)
        assertEquals(CorrectionGrowthBand.PatternFix, profile.correctionPolicy.band)
    }

    @Test
    fun `repeated core weakness caps correction band at SentenceShape`() {
        // 반복적인 GrammarAccuracy 하락 근거가 있으면 naturalness/어휘가 좋아도 먼저 문장 뼈대를 안정화해야 한다.
        // 이는 "좋은 문장으로 멋지게 바꾸기"보다 사용자가 재사용 가능한 구조를 받게 하려는 방어다.
        val state = analyzedState(
            internal = refinedInternalMetrics(),
            external = refinedExternalMetrics(),
            evidence = mapOf(
                LearningMetricKey.GrammarAccuracy to evidence(
                    observedCount = 3,
                    confidence = 0.82,
                    direction = EvidenceDirection.Down
                ),
                LearningMetricKey.VocabularyAppropriateness to evidence(3, 0.86),
                LearningMetricKey.SpokenNaturalness to evidence(3, 0.84),
                LearningMetricKey.NaturalExpressionUsage to evidence(3, 0.8)
            )
        )

        val profile = useCase(state)

        // high confidence 근거가 있어도 core weakness가 반복되면 NuanceRefine이 아니라 SentenceShape로 제한한다.
        assertEquals(ProfileConfidence.High, profile.core.levelConfidence)
        assertEquals(CorrectionGrowthBand.SentenceShape, profile.correctionPolicy.band)
    }

    @Test
    fun `low sentence complexity blocks upper correction band despite high other metrics`() {
        // grammar/vocabulary/naturalness가 높아도 sentenceComplexity가 낮으면 긴 rewrite나 nuance 교정은 과하다.
        // 사용자가 실제로 감당할 문장 구조 근거가 약하므로 SentenceShape 이하로 방어한다.
        val state = analyzedState(
            internal = refinedInternalMetrics().copy(sentenceComplexity = 0.2),
            external = refinedExternalMetrics(),
            evidence = highConfidenceEvidence()
        )

        val profile = useCase(state)

        assertEquals(ProfileConfidence.High, profile.core.levelConfidence)
        assertEquals(CorrectionGrowthBand.SentenceShape, profile.correctionPolicy.band)
    }

    @Test
    fun `single high evidence axis does not unlock connected correction band`() {
        // 전체 metric 점수는 좋아 보여도 반복 상승 근거가 한 축에만 몰리면 "다음 단계" 판단이 불안정하다.
        // 이 케이스를 ConnectedExpression으로 올리면 사용자가 감당할 수 있는 10% 성장폭을 넘길 수 있다.
        val state = analyzedState(
            internal = refinedInternalMetrics().copy(
                // NuanceRefine 조건은 피하고 ConnectedExpression 조건만 검증하기 위해 naturalness를 Expanding 구간에 둔다.
                spokenNaturalness = 0.78,
                naturalExpressionUsage = 0.78
            ),
            external = refinedExternalMetrics().copy(naturalnessScore = 0.78),
            evidence = mapOf(
                LearningMetricKey.GrammarAccuracy to evidence(
                    observedCount = 6,
                    confidence = 0.9
                )
            )
        )

        val profile = useCase(state)

        // observedCount가 충분해 confidence는 High지만, 상승 근거가 한 축뿐이면 EverydayNatural에 머문다.
        assertEquals(ProfileConfidence.High, profile.core.levelConfidence)
        assertEquals(CorrectionGrowthBand.EverydayNatural, profile.correctionPolicy.band)
    }

    @Test
    fun `chat only evidence does not unlock upper correction band`() {
        // CHAT-TUNE-006 1차에서는 ChatSession evidence를 저장만 하고 correction band 계산에는 쓰지 않는다.
        // source filtering이 없으면 아래 반복 상승 근거가 ConnectedExpression/NuanceRefine을 열 수 있다.
        val state = analyzedState(
            internal = refinedInternalMetrics().copy(
                spokenNaturalness = 0.78,
                naturalExpressionUsage = 0.78
            ),
            external = refinedExternalMetrics().copy(naturalnessScore = 0.78),
            evidence = mapOf(
                LearningMetricKey.GrammarAccuracy to evidence(
                    observedCount = 6,
                    confidence = 0.9,
                    sourceTypes = setOf(LearningSignalSource.ChatSession)
                ),
                LearningMetricKey.VocabularyAppropriateness to evidence(
                    observedCount = 6,
                    confidence = 0.9,
                    sourceTypes = setOf(LearningSignalSource.ChatSession)
                ),
                LearningMetricKey.SpokenNaturalness to evidence(
                    observedCount = 6,
                    confidence = 0.9,
                    sourceTypes = setOf(LearningSignalSource.ChatSession)
                )
            )
        )

        val profile = useCase(state)

        // internal metric은 높지만 correction source 근거가 없으므로 상위 correction band를 열지 않는다.
        assertEquals(CorrectionGrowthBand.EverydayNatural, profile.correctionPolicy.band)
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
        activeFocus: List<LearningFocus> = emptyList(),
        chatEvidenceSummary: ChatEvidenceSummary? = null
    ): LangState {
        // profile 테스트는 저장소를 거치지 않고 LangState snapshot 해석만 검증한다.
        // createdAt/updatedAt은 profile 계산에 직접 쓰이지 않지만, fixture가 실제 snapshot처럼 보이도록 유지한다.
        return LangState.initial(LangCode.EN, createdAt = 1_000L, updatedAt = 2_000L).copy(
            internal = internal,
            external = external,
            analysisMeta = LangStateAnalysisMeta(
                metricEvidence = evidence,
                activeFocus = activeFocus,
                lastSignalAt = 2_000L,
                chatEvidenceSummary = chatEvidenceSummary
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
        confidence: Double,
        direction: EvidenceDirection = EvidenceDirection.Up,
        sourceTypes: Set<LearningSignalSource> = setOf(LearningSignalSource.CorrectionSignal)
    ): MetricEvidence {
        // sourceTypes와 direction은 "어떤 경로에서 어떤 방향으로 누적됐는지"를 profile이 나중에 다시 해석할 수 있게 남긴다.
        return MetricEvidence(
            observedCount = observedCount,
            confidence = confidence,
            sourceTypes = sourceTypes,
            direction = direction,
            directionCount = observedCount,
            lastObservedAt = 2_000L
        )
    }

    private fun refinedInternalMetrics(): InternalMetrics {
        // 여러 지표가 높은 상태를 만들어, correction band 방어가 없으면 NuanceRefine까지 올라갈 수 있는 fixture다.
        return InternalMetrics(
            grammarAccuracy = 0.9,
            vocabularyAppropriateness = 0.9,
            lexicalDiversity = 0.86,
            vocabularyLevel = VocabLevel.C1,
            sentenceComplexity = 0.86,
            speechRate = 0.82,
            pauseFrequency = 0.08,
            avgUtteranceLength = 0.84,
            spokenNaturalness = 0.9,
            naturalExpressionUsage = 0.88,
            errorRecurrence = 0.05,
            reviewRetention = 0.9
        )
    }

    private fun refinedExternalMetrics(): ExternalMetrics {
        // external은 표시용이지만 fixture를 실제 snapshot처럼 유지하기 위해 internal 고점과 맞춘다.
        return ExternalMetrics(
            vocabularyLevel = VocabLevel.C1,
            grammarAccuracy = 0.9,
            expressionRange = 75,
            fluencyScore = 0.85,
            naturalnessScore = 0.89
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
