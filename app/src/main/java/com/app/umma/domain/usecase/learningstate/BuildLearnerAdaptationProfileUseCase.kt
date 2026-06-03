package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.ChallengeLevel
import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionAdaptationPolicy
import com.app.umma.domain.model.learningstate.CorrectionStylePolicy
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.ExpressionGrowthPolicy
import com.app.umma.domain.model.learningstate.GrammarStrategyPolicy
import com.app.umma.domain.model.learningstate.IntentSupportPolicy
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocus
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.PrimaryBridgePolicy
import com.app.umma.domain.model.learningstate.PrimaryLanguageSupportPolicy
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.QuestionLoadPolicy
import com.app.umma.domain.model.learningstate.RecastStylePolicy
import com.app.umma.domain.model.learningstate.ResponseLengthPolicy
import com.app.umma.domain.model.learningstate.SpeechSpeedPolicy
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.SpokenRegisterStrategy
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.learningstate.VocabularyStrategyPolicy
import javax.inject.Inject

/**
 * 저장된 LangState를 Chat/Correction이 함께 사용할 교육 전략 profile로 바꾼다.
 *
 * 이 UseCase는 저장하지 않고, prompt 문장도 만들지 않는다.
 * 숫자 metric을 domain policy enum으로 해석하는 read-only 경계만 담당한다.
 */
class BuildLearnerAdaptationProfileUseCase @Inject constructor() {
    operator fun invoke(langState: LangState?): LearnerAdaptationProfile {
        // LangState가 없으면 분석 근거도 없다는 뜻이므로 가장 보수적인 profile을 만든다.
        if (langState == null) return conservativeProfile()

        // 초기 LangState는 A1 실력으로 확정하지 않는다.
        // lastAnalyzedAt과 evidence가 모두 비어 있으면 "실력 낮음"이 아니라 "근거 부족"이다.
        val confidence = estimateProfileConfidence(langState)
        // 문법은 대화 안전성과 직접 연결되므로 가장 보수적인 단위로 먼저 분리한다.
        val grammarStage = stageFromScore(langState.internal.grammarAccuracy)
        // 어휘는 한 항목만 보면 왜곡되기 쉬워서, 적절성/다양성/레벨 단서를 함께 본다.
        val vocabularyStage = stageFromScore(
            averageOf(
                langState.internal.vocabularyAppropriateness,
                langState.internal.lexicalDiversity,
                // expressionRange는 external에 남아 있지만 현재 누적 표현 폭의 유일한 source다.
                // vocabularyLevel도 expressionRange에서 영향을 받으므로 둘을 더하지 않고 더 강한 단서 하나만 쓴다.
                maxOf(
                    vocabLevelScore(langState.internal.vocabularyLevel),
                    expressionRangeScore(langState.external.expressionRange)
                )
            )
        )
        // 유창성은 속도만 높다고 좋아지지 않으므로 pause와 발화 길이를 같이 묶어 본다.
        val fluencyStage = stageFromScore(
            averageOf(
                langState.internal.speechRate,
                1.0 - langState.internal.pauseFrequency,
                langState.internal.avgUtteranceLength
            )
        )
        // 자연스러움은 표시용 external이 아니라 내부 구어체/표현 선택 지표만으로 판단한다.
        val naturalnessStage = stageFromScore(
            averageOf(
                langState.internal.spokenNaturalness,
                langState.internal.naturalExpressionUsage
            )
        )
        // active focus는 그대로 넘기지 않고, prompt에 실제로 유효한 상위 항목만 요약한다.
        val focusSummary = summarizeFocus(langState.analysisMeta.activeFocus)
        // core profile은 Chat과 Correction이 공통으로 읽는 기준점이라 기능별 정책 이전에 먼저 만든다.
        val core = LearnerAbilityProfile(
            cefrLevel = langState.internal.vocabularyLevel,
            levelConfidence = confidence,
            grammarStage = grammarStage,
            vocabularyStage = vocabularyStage,
            fluencyStage = fluencyStage,
            naturalnessStage = naturalnessStage,
            focus = focusSummary
        )
        val challenge = chooseChallengeLevel(
            confidence = confidence,
            stages = listOf(grammarStage, vocabularyStage, fluencyStage, naturalnessStage)
        )

        // core는 공유하고, chat/correction은 같은 능력 판단을 각 기능의 말투/설명 방식으로만 바꾼다.
        return LearnerAdaptationProfile(
            core = core,
            chatPolicy = buildChatPolicy(
                langState = langState,
                confidence = confidence,
                grammarStage = grammarStage,
                vocabularyStage = vocabularyStage,
                fluencyStage = fluencyStage,
                naturalnessStage = naturalnessStage
            ),
            correctionPolicy = buildCorrectionPolicy(
                challenge = challenge,
                confidence = confidence,
                grammarStage = grammarStage,
                vocabularyStage = vocabularyStage,
                naturalnessStage = naturalnessStage
            )
        )
    }

    private fun conservativeProfile(): LearnerAdaptationProfile {
        // 근거가 없을 때는 사용자를 낮은 실력으로 단정하지 않고, 과한 challenge만 막는다.
        val core = LearnerAbilityProfile(
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
        )
        // 분석 근거가 없을 때도 Chat과 Correction이 같은 안전한 기준을 보도록 두 정책을 함께 만든다.
        return LearnerAdaptationProfile(
            core = core,
            chatPolicy = buildChatPolicy(
                langState = null,
                confidence = ProfileConfidence.Low,
                grammarStage = SkillStage.Foundation,
                vocabularyStage = SkillStage.Foundation,
                fluencyStage = SkillStage.Foundation,
                naturalnessStage = SkillStage.Foundation
            ),
            correctionPolicy = buildCorrectionPolicy(
                challenge = ChallengeLevel.Support,
                confidence = ProfileConfidence.Low,
                grammarStage = SkillStage.Foundation,
                vocabularyStage = SkillStage.Foundation,
                naturalnessStage = SkillStage.Foundation
            )
        )
    }

    private fun estimateProfileConfidence(langState: LangState): ProfileConfidence {
        // evidence는 장기 지표의 반복 관측 근거이고, confidence는 그 근거를 얼마나 믿을지 정한다.
        val evidence = langState.analysisMeta.metricEvidence.values
        // evidence가 없고 분석 시각도 없으면 초기 snapshot이므로 low confidence로 둔다.
        if (evidence.isEmpty() && langState.lastAnalyzedAt == null) return ProfileConfidence.Low

        // 관측 횟수는 한두 번의 튀는 결과가 confidence를 뒤집지 못하도록 누적량만 본다.
        val totalEvidenceCount = evidence.sumOf { it.observedCount.coerceAtLeast(0) }
        // 보정된 confidence만 평균내서 raw AI confidence가 그대로 장기 profile을 흔들지 못하게 한다.
        val averageConfidence = evidence.map { it.confidence.coerceIn(0.0, 1.0) }.averageOrZero()
        // Mixed direction은 "좋아졌다/나빠졌다"가 충돌한다는 뜻이라 문자열이 아닌 enum 계약으로 판별한다.
        val hasMixedSignal = evidence.any { it.direction == EvidenceDirection.Mixed }
        // lastAnalyzedAt만 있고 실제 metric이 모두 비어 있으면 분석 완료가 아니라 빈 분석일 수 있다.
        val hasMeaningfulMetric = hasMeaningfulMetric(langState)
        // grammar/vocabulary/fluency/naturalness가 서로 너무 벌어지면 일부 고점만 믿지 않도록 보수적으로 둔다.
        val metricSpread = stageSpread(
            listOf(
                stageFromScore(langState.internal.grammarAccuracy),
                stageFromScore(langState.internal.vocabularyAppropriateness),
                stageFromScore(
                    averageOf(
                        langState.internal.speechRate,
                        1.0 - langState.internal.pauseFrequency,
                        langState.internal.avgUtteranceLength
                    )
                ),
                stageFromScore(
                    averageOf(
                        langState.internal.spokenNaturalness,
                        langState.internal.naturalExpressionUsage
                    )
                )
            )
        )

        // 지표가 서로 크게 충돌하면 일부 점수가 높더라도 challenge를 보수적으로 유지한다.
        if (hasMixedSignal || metricSpread >= HIGH_SPREAD_STAGE_DISTANCE) return ProfileConfidence.Low

        // 분석 시각만으로 Medium을 주면 초기/빈 분석이 실제 실력처럼 과대 해석될 수 있다.
        if (!hasMeaningfulMetric && totalEvidenceCount == 0) return ProfileConfidence.Low

        // 여러 근거가 높은 confidence로 누적된 경우에만 high confidence를 허용한다.
        if (totalEvidenceCount >= HIGH_EVIDENCE_COUNT && averageConfidence >= HIGH_CONFIDENCE_SCORE) {
            return ProfileConfidence.High
        }

        // 분석 시각과 의미 있는 metric이 있거나 일부 evidence가 있으면 medium까지는 올릴 수 있다.
        if (totalEvidenceCount >= MEDIUM_EVIDENCE_COUNT || (langState.lastAnalyzedAt != null && hasMeaningfulMetric)) {
            return ProfileConfidence.Medium
        }

        return ProfileConfidence.Low
    }

    private fun summarizeFocus(focuses: List<LearningFocus>): LearningFocusSummary {
        // confidence가 너무 낮은 focus는 prompt에 노출하지 않는다.
        val eligibleFocuses = focuses.filter { focus ->
            focus.confidence >= MIN_FOCUS_CONFIDENCE && focus.observedCount > 0
        }
        if (eligibleFocuses.isEmpty()) {
            return LearningFocusSummary(
                primaryFocus = null,
                secondaryFocus = null,
                confidence = ProfileConfidence.Low,
                observedCount = 0
            )
        }

        // prompt에 들어갈 focus는 confidence, 반복 횟수, 최근성 순서로 상위 1~2개만 고른다.
        val topFocuses = eligibleFocuses.sortedWith(
            compareByDescending<LearningFocus> { it.confidence }
                .thenByDescending { it.observedCount }
                .thenByDescending { it.lastObservedAt }
        ).take(MAX_PROMPT_FOCUS_COUNT)
        // focus 요약은 항목 자체보다 "얼마나 반복됐는지"와 "얼마나 믿을 수 있는지"를 같이 남긴다.
        val selectedObservedCount = topFocuses.sumOf { it.observedCount }
        val selectedConfidence = topFocuses.map { it.confidence }.averageOrZero()

        return LearningFocusSummary(
            primaryFocus = topFocuses.getOrNull(0)?.type,
            secondaryFocus = topFocuses.getOrNull(1)?.type,
            confidence = confidenceFromScore(selectedConfidence, selectedObservedCount),
            observedCount = selectedObservedCount
        )
    }

    private fun buildChatPolicy(
        langState: LangState?,
        confidence: ProfileConfidence,
        grammarStage: SkillStage,
        vocabularyStage: SkillStage,
        fluencyStage: SkillStage,
        naturalnessStage: SkillStage
    ): ChatAdaptationPolicy {
        // Chat은 Correction의 4단계 challenge보다 대화 지속 가능성을 더 세밀하게 봐야 한다.
        val band = chooseConversationBand(
            langState = langState,
            confidence = confidence,
            grammarStage = grammarStage,
            vocabularyStage = vocabularyStage,
            fluencyStage = fluencyStage,
            naturalnessStage = naturalnessStage
        )

        // band별 정책은 prompt에 enum 이름으로 노출되지 않고, BuildPromptUseCase가 행동 문장으로 압축한다.
        return when (band) {
            ConversationAbilityBand.IntentOnly -> ChatAdaptationPolicy(
                // 의미 단서가 거의 없으므로 AI가 먼저 의도를 복원하고, 사용자는 단어/선택지만 말해도 이어갈 수 있게 한다.
                conversationBand = band,
                intentSupport = IntentSupportPolicy.InferActively,
                primaryBridge = PrimaryBridgePolicy.Active,
                recastStyle = RecastStylePolicy.TinyInline,
                expressionGrowth = ExpressionGrowthPolicy.OneTinyPhrase,
                questionLoad = QuestionLoadPolicy.ConcreteChoice,
                responseLength = ResponseLengthPolicy.OneShortSentence,
                speechSpeed = SpeechSpeedPolicy.SlowBeginner
            )
            ConversationAbilityBand.PhraseEmerging -> ChatAdaptationPolicy(
                // 단어와 짧은 구는 보이지만 문장 생성 부담이 크므로, 짧은 확인과 쉬운 패턴 하나를 우선한다.
                conversationBand = band,
                intentSupport = IntentSupportPolicy.ConfirmBriefly,
                primaryBridge = PrimaryBridgePolicy.Brief,
                recastStyle = RecastStylePolicy.SimpleInline,
                expressionGrowth = ExpressionGrowthPolicy.OneSimplePattern,
                questionLoad = QuestionLoadPolicy.ConcreteChoice,
                responseLength = ResponseLengthPolicy.ShortTwoStep,
                speechSpeed = SpeechSpeedPolicy.Guided
            )
            ConversationAbilityBand.SimpleSentence -> ChatAdaptationPolicy(
                // 짧은 문장 생산은 가능하므로 의도 확인은 줄이되, 질문은 실제 내용 하나로 제한해 다음 발화를 보호한다.
                conversationBand = band,
                intentSupport = IntentSupportPolicy.ConfirmBriefly,
                primaryBridge = PrimaryBridgePolicy.Brief,
                recastStyle = RecastStylePolicy.SimpleInline,
                expressionGrowth = ExpressionGrowthPolicy.OneSimplePattern,
                questionLoad = QuestionLoadPolicy.OneConcreteFollowUp,
                responseLength = ResponseLengthPolicy.ShortTwoStep,
                speechSpeed = SpeechSpeedPolicy.Guided
            )
            ConversationAbilityBand.BasicConversation -> ChatAdaptationPolicy(
                // 기본 왕복 대화가 가능하므로 target 언어 중심으로 반응하고, 자연스러운 일상 표현 하나만 확장한다.
                conversationBand = band,
                intentSupport = IntentSupportPolicy.TrustMeaning,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                recastStyle = RecastStylePolicy.NaturalInline,
                expressionGrowth = ExpressionGrowthPolicy.OneEverydayExpression,
                questionLoad = QuestionLoadPolicy.OpenShort,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                speechSpeed = SpeechSpeedPolicy.NormalLearning
            )
            ConversationAbilityBand.ConnectedExpression -> ChatAdaptationPolicy(
                // 이유/감정/상황 설명이 가능하므로 대화 흐름을 넓히되, prompt 비대를 막기 위해 확장은 한 표현으로 제한한다.
                conversationBand = band,
                intentSupport = IntentSupportPolicy.TrustMeaning,
                primaryBridge = PrimaryBridgePolicy.FallbackOnly,
                recastStyle = RecastStylePolicy.NaturalInline,
                expressionGrowth = ExpressionGrowthPolicy.OneEverydayExpression,
                questionLoad = QuestionLoadPolicy.OpenShort,
                responseLength = ResponseLengthPolicy.NaturalBrief,
                speechSpeed = SpeechSpeedPolicy.SlightlyFast
            )
            ConversationAbilityBand.NuanceControl -> ChatAdaptationPolicy(
                // 의미 전달은 안정적인 단계이므로 보조 언어를 닫고, 사용자가 이끄는 일반 대화 안에서 뉘앙스만 미세 조정한다.
                conversationBand = band,
                intentSupport = IntentSupportPolicy.FollowUserLead,
                primaryBridge = PrimaryBridgePolicy.None,
                recastStyle = RecastStylePolicy.NuanceOnly,
                expressionGrowth = ExpressionGrowthPolicy.OneNativeLikeChoice,
                questionLoad = QuestionLoadPolicy.NuanceFollowUp,
                responseLength = ResponseLengthPolicy.Flexible,
                speechSpeed = SpeechSpeedPolicy.Advanced
            )
        }
    }

    private fun chooseConversationBand(
        langState: LangState?,
        confidence: ProfileConfidence,
        grammarStage: SkillStage,
        vocabularyStage: SkillStage,
        fluencyStage: SkillStage,
        naturalnessStage: SkillStage
    ): ConversationAbilityBand {
        // meaningful LangState가 없으면 "초급 확정"이 아니라 첫 selectedLang fallback으로 둔다.
        // 실제 첫 발화가 fluent하면 prompt가 현재 발화를 우선 보고 더 자연스럽게 따라가도록 별도 지시한다.
        if (langState == null || !hasMeaningfulLangState(langState)) {
            return ConversationAbilityBand.IntentOnly
        }

        // confidence가 낮고 핵심 대화 지표가 대부분 Foundation이면 의도 복원 우선 단계로 둔다.
        val foundationCount = listOf(grammarStage, vocabularyStage, fluencyStage)
            .count { it == SkillStage.Foundation }
        if (confidence == ProfileConfidence.Low && foundationCount >= 2) {
            return ConversationAbilityBand.IntentOnly
        }

        // 발화 길이와 pause는 "대화를 계속할 수 있는가"에 직접 영향을 주므로 낮은 단계로 보수 조정한다.
        val pauseStage = stageFromScore(1.0 - (langState.internal.pauseFrequency.coerceIn(0.0, 1.0)))
        val utteranceStage = stageFromScore(langState.internal.avgUtteranceLength)
        val structureStage = stageFromScore(langState.internal.sentenceComplexity)
        val weakestCoreStage = listOf(grammarStage, vocabularyStage, fluencyStage).minByOrNull { it.ordinal }
            ?: SkillStage.Foundation

        // 단어/구는 보이지만 문장 유지 근거가 부족하면 PhraseEmerging으로 둔다.
        if (
            weakestCoreStage.ordinal <= SkillStage.Foundation.ordinal ||
            pauseStage == SkillStage.Foundation ||
            utteranceStage == SkillStage.Foundation
        ) {
            return ConversationAbilityBand.PhraseEmerging
        }

        // 짧은 문장 근거는 있으나 structure나 grammar가 아직 낮으면 SimpleSentence가 더 안전하다.
        if (
            grammarStage == SkillStage.Developing ||
            structureStage.ordinal <= SkillStage.Developing.ordinal
        ) {
            return ConversationAbilityBand.SimpleSentence
        }

        // 안정적인 왕복 대화는 grammar/vocabulary/fluency 중 최소 두 영역이 Stable 이상이어야 한다.
        val stableConversationCount = listOf(grammarStage, vocabularyStage, fluencyStage)
            .count { it.ordinal >= SkillStage.Stable.ordinal }
        if (stableConversationCount >= 2) {
            // 표현 연결 단계는 vocabulary/structure/naturalness가 함께 올라온 경우에만 허용한다.
            val connectedCount = listOf(vocabularyStage, structureStage, naturalnessStage)
                .count { it.ordinal >= SkillStage.Expanding.ordinal }
            if (connectedCount >= 2) {
                // NuanceControl은 높은 confidence와 자연스러움/어휘 고점이 같이 있어야 과잉 평가를 막을 수 있다.
                if (
                    confidence == ProfileConfidence.High &&
                    naturalnessStage == SkillStage.Refined &&
                    vocabularyStage.ordinal >= SkillStage.Expanding.ordinal &&
                    fluencyStage.ordinal >= SkillStage.Stable.ordinal
                ) {
                    return ConversationAbilityBand.NuanceControl
                }
                return ConversationAbilityBand.ConnectedExpression
            }
            return ConversationAbilityBand.BasicConversation
        }

        return ConversationAbilityBand.SimpleSentence
    }

    private fun buildCorrectionPolicy(
        challenge: ChallengeLevel,
        confidence: ProfileConfidence,
        grammarStage: SkillStage,
        vocabularyStage: SkillStage,
        naturalnessStage: SkillStage
    ): CorrectionAdaptationPolicy {
        // confidence가 낮으면 교정도 최소 수정 중심으로 둔다.
        if (confidence == ProfileConfidence.Low) {
            return CorrectionAdaptationPolicy(
                challengeLevel = ChallengeLevel.Support,
                correctionStyle = CorrectionStylePolicy.MinimalFix,
                vocabularyStrategy = VocabularyStrategyPolicy.KeepSimpleWords,
                grammarStrategy = GrammarStrategyPolicy.FixBlockingErrorOnly,
                spokenRegisterStrategy = SpokenRegisterStrategy.Simple,
                primaryLanguageSupport = PrimaryLanguageSupportPolicy.PrimaryLanguageFirst
            )
        }

        // confidence가 충분할 때만 stage별로 교정 설명의 깊이를 다르게 한다.
        // challenge는 공통 기준이고, 각 세부 전략은 stage에 따라 덜/더 확장되도록 분리한다.
        return CorrectionAdaptationPolicy(
            challengeLevel = challenge,
            correctionStyle = correctionStyleFor(challenge),
            vocabularyStrategy = vocabularyStrategyFor(vocabularyStage, challenge),
            grammarStrategy = grammarStrategyFor(grammarStage, challenge),
            spokenRegisterStrategy = registerStrategyFor(naturalnessStage, challenge),
            primaryLanguageSupport = supportPolicyFor(challenge)
        )
    }

    private fun chooseChallengeLevel(
        confidence: ProfileConfidence,
        stages: List<SkillStage>
    ): ChallengeLevel {
        // confidence가 낮으면 점수가 높아 보여도 도전 강도를 올리지 않는다.
        if (confidence == ProfileConfidence.Low) return ChallengeLevel.Support

        // 가장 약한 영역이 실제 대화 난이도의 병목이므로 weakest stage를 기준으로 둔다.
        val weakestStage = stages.minByOrNull { it.ordinal } ?: SkillStage.Foundation
        return when (weakestStage) {
            SkillStage.Foundation -> ChallengeLevel.Support
            SkillStage.Developing -> ChallengeLevel.Match
            SkillStage.Stable -> if (confidence == ProfileConfidence.High) {
                ChallengeLevel.Stretch
            } else {
                ChallengeLevel.Match
            }
            SkillStage.Expanding -> ChallengeLevel.Stretch
            SkillStage.Refined -> if (confidence == ProfileConfidence.High) {
                ChallengeLevel.Refine
            } else {
                ChallengeLevel.Stretch
            }
        }
    }

    private fun correctionStyleFor(challenge: ChallengeLevel): CorrectionStylePolicy {
        return when (challenge) {
            ChallengeLevel.Support -> CorrectionStylePolicy.MinimalFix
            ChallengeLevel.Match -> CorrectionStylePolicy.ExplainOneReason
            ChallengeLevel.Stretch -> CorrectionStylePolicy.NaturalSpokenRewrite
            ChallengeLevel.Refine -> CorrectionStylePolicy.NuanceAndRegister
        }
    }

    private fun vocabularyStrategyFor(
        vocabularyStage: SkillStage,
        challenge: ChallengeLevel
    ): VocabularyStrategyPolicy {
        // 어휘 stage가 낮으면 challenge가 높더라도 단어 확장을 제한한다.
        if (vocabularyStage.ordinal <= SkillStage.Developing.ordinal) return VocabularyStrategyPolicy.KeepSimpleWords
        return when (challenge) {
            ChallengeLevel.Support -> VocabularyStrategyPolicy.KeepSimpleWords
            ChallengeLevel.Match -> VocabularyStrategyPolicy.AddOneUsefulExpression
            ChallengeLevel.Stretch -> VocabularyStrategyPolicy.ImproveCollocation
            ChallengeLevel.Refine -> VocabularyStrategyPolicy.RefineNativeChoice
        }
    }

    private fun grammarStrategyFor(
        grammarStage: SkillStage,
        challenge: ChallengeLevel
    ): GrammarStrategyPolicy {
        // 문법 stage가 낮으면 구조 확장보다 의미를 막는 오류 수정이 먼저다.
        if (grammarStage == SkillStage.Foundation) return GrammarStrategyPolicy.FixBlockingErrorOnly
        return when (challenge) {
            ChallengeLevel.Support -> GrammarStrategyPolicy.FixBlockingErrorOnly
            ChallengeLevel.Match -> GrammarStrategyPolicy.FixOneMainPattern
            ChallengeLevel.Stretch -> GrammarStrategyPolicy.ExpandSentenceStructure
            ChallengeLevel.Refine -> GrammarStrategyPolicy.RefineAdvancedStructure
        }
    }

    private fun registerStrategyFor(
        naturalnessStage: SkillStage,
        challenge: ChallengeLevel
    ): SpokenRegisterStrategy {
        // 자연스러움 stage가 낮으면 register 설명보다 단순한 일상 표현을 우선한다.
        if (naturalnessStage.ordinal <= SkillStage.Developing.ordinal) return SpokenRegisterStrategy.Simple
        return when (challenge) {
            ChallengeLevel.Support -> SpokenRegisterStrategy.Simple
            ChallengeLevel.Match -> SpokenRegisterStrategy.EverydaySpoken
            ChallengeLevel.Stretch -> SpokenRegisterStrategy.NativeLikeCasual
            ChallengeLevel.Refine -> SpokenRegisterStrategy.FormalWhenNeeded
        }
    }

    private fun supportPolicyFor(challenge: ChallengeLevel): PrimaryLanguageSupportPolicy {
        // 같은 challenge라도 설명 보조 언어 강도는 builder에서 명확히 분리해 재사용한다.
        return when (challenge) {
            ChallengeLevel.Support -> PrimaryLanguageSupportPolicy.PrimaryLanguageFirst
            ChallengeLevel.Match -> PrimaryLanguageSupportPolicy.BriefPrimaryLanguageHint
            ChallengeLevel.Stretch -> PrimaryLanguageSupportPolicy.TargetLanguageFirstWithPrimaryFallback
            ChallengeLevel.Refine -> PrimaryLanguageSupportPolicy.TargetLanguageOnly
        }
    }

    private fun confidenceFromScore(
        confidence: Double,
        observedCount: Int
    ): ProfileConfidence {
        // focus는 여러 번 같은 방향으로 반복 관측돼야만 profile confidence를 올린다.
        return when {
            observedCount >= HIGH_FOCUS_COUNT && confidence >= HIGH_CONFIDENCE_SCORE -> ProfileConfidence.High
            observedCount >= MEDIUM_FOCUS_COUNT && confidence >= MEDIUM_CONFIDENCE_SCORE -> ProfileConfidence.Medium
            else -> ProfileConfidence.Low
        }
    }

    private fun stageFromScore(score: Double): SkillStage {
        // 0..1 점수를 prompt 정책에서 바로 쓸 수 있는 stage로 변환한다.
        val clampedScore = score.coerceIn(0.0, 1.0)
        return when {
            clampedScore < FOUNDATION_MAX -> SkillStage.Foundation
            clampedScore < DEVELOPING_MAX -> SkillStage.Developing
            clampedScore < STABLE_MAX -> SkillStage.Stable
            clampedScore < EXPANDING_MAX -> SkillStage.Expanding
            else -> SkillStage.Refined
        }
    }

    private fun vocabLevelScore(level: VocabLevel): Double {
        // CEFR enum을 0..1 보조값으로만 바꾼다. 이 값은 profile 밖으로 노출하지 않는다.
        return level.ordinal.toDouble() / (VocabLevel.entries.size - 1).toDouble()
    }

    private fun expressionRangeScore(expressionRange: Int): Double {
        // expressionRange는 external에 있지만 누적 표현 폭 source라 profile에서만 예외적으로 읽는다.
        // C2 후보 기준인 80개 이상을 상한으로 둬 표시용 큰 숫자가 profile을 과도하게 밀지 못하게 한다.
        return (expressionRange.coerceAtLeast(0).toDouble() / EXPRESSION_RANGE_C2_THRESHOLD)
            .coerceIn(0.0, 1.0)
    }

    private fun hasMeaningfulMetric(langState: LangState): Boolean {
        // initial snapshot의 0 값은 "초급" 근거가 아니라 "아직 모름"에 가깝다.
        return listOf(
            langState.internal.grammarAccuracy,
            langState.internal.vocabularyAppropriateness,
            langState.internal.lexicalDiversity,
            maxOf(
                vocabLevelScore(langState.internal.vocabularyLevel),
                expressionRangeScore(langState.external.expressionRange)
            ),
            langState.internal.speechRate,
            langState.internal.pauseFrequency,
            langState.internal.avgUtteranceLength,
            langState.internal.spokenNaturalness,
            langState.internal.naturalExpressionUsage
        ).any { value ->
            // 아주 작은 흔들림은 분석 근거로 보지 않고, 실제 관측된 metric만 confidence에 반영한다.
            value.coerceIn(0.0, 1.0) >= MEANINGFUL_METRIC_MIN
        }
    }

    private fun hasMeaningfulLangState(langState: LangState): Boolean {
        // selectedLang LangState가 있더라도 분석 시각이 없으면 아직 실제 대화 능력 근거로 보기 어렵다.
        if (langState.lastAnalyzedAt == null) return false
        // metricEvidence는 분석이 단순 초기값 저장이 아니라 실제 관측에서 왔는지 보여주는 핵심 근거다.
        if (langState.analysisMeta.metricEvidence.isEmpty()) return false
        // 주요 internal metric이 전부 초기값이면 evidence가 있어도 첫 대화 fallback으로 유지한다.
        return hasMeaningfulMetric(langState)
    }

    private fun stageSpread(stages: List<SkillStage>): Int {
        // high score mixed 상태를 잡기 위해 가장 높은 stage와 낮은 stage의 차이를 본다.
        val ordinals = stages.map { it.ordinal }
        return (ordinals.maxOrNull() ?: 0) - (ordinals.minOrNull() ?: 0)
    }

    private fun averageOf(vararg values: Double): Double {
        // 모든 metric은 0..1 범위로 해석해 stage 계산에 넣는다.
        return values.map { it.coerceIn(0.0, 1.0) }.averageOrZero()
    }

    private fun Iterable<Double>.averageOrZero(): Double {
        val values = toList()
        if (values.isEmpty()) return 0.0
        return values.average()
    }

    private companion object {
        private const val FOUNDATION_MAX = 0.25
        private const val DEVELOPING_MAX = 0.45
        private const val STABLE_MAX = 0.65
        private const val EXPANDING_MAX = 0.82
        private const val MIN_FOCUS_CONFIDENCE = 0.35
        private const val MEDIUM_CONFIDENCE_SCORE = 0.5
        private const val HIGH_CONFIDENCE_SCORE = 0.75
        private const val MEDIUM_EVIDENCE_COUNT = 2
        private const val HIGH_EVIDENCE_COUNT = 6
        private const val MEDIUM_FOCUS_COUNT = 2
        private const val HIGH_FOCUS_COUNT = 4
        private const val MAX_PROMPT_FOCUS_COUNT = 2
        private const val HIGH_SPREAD_STAGE_DISTANCE = 3
        private const val MEANINGFUL_METRIC_MIN = 0.05
        private const val EXPRESSION_RANGE_C2_THRESHOLD = 80.0
    }
}
