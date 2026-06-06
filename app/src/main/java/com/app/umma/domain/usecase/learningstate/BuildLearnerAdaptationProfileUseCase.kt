package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.ChatAdaptationPolicy
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthBand
import com.app.umma.domain.model.learningstate.CorrectionGrowthPolicy
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearnerAbilityProfile
import com.app.umma.domain.model.learningstate.LearnerAdaptationProfile
import com.app.umma.domain.model.learningstate.LearningFocus
import com.app.umma.domain.model.learningstate.LearningFocusSummary
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SkillStage
import com.app.umma.domain.model.learningstate.VocabLevel
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
                confidence = confidence,
                grammarStage = grammarStage,
                vocabularyStage = vocabularyStage,
                fluencyStage = fluencyStage,
                naturalnessStage = naturalnessStage,
                focus = focusSummary,
                langState = langState
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
            // 근거 없음 = 실력 낮음이 아니라 과한 교정 방지. MeaningFirst가 가장 보수적인 기본값이다.
            correctionPolicy = CorrectionGrowthPolicy.defaultsForBand(CorrectionGrowthBand.MeaningFirst)
        )
    }

    private fun estimateProfileConfidence(langState: LangState): ProfileConfidence {
        // evidence는 장기 지표의 반복 관측 근거이고, confidence는 그 근거를 얼마나 믿을지 정한다.
        // CHAT-TUNE-006 1차에서는 ChatSession-only evidence를 저장만 하고 profile 계산에는 아직 쓰지 않는다.
        val evidence = langState.analysisMeta.metricEvidence.values.excludeChatOnlyEvidence()
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

        // band별 정책은 한 곳에서 관리해 LangState 기반 계산과 conversation evidence 기반 계산이 같은 결과를 쓰게 한다.
        return ChatAdaptationPolicy.defaultsForBand(band)
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

        // 단어/표현 폭 하나가 보여도 문장 뼈대와 대화 지속 근거가 함께 없으면 아직 "구 반응 가능"으로 올리지 않는다.
        // IntentOnly는 사용자가 학습언어만으로 대화를 이어갈 수 없는 상태를 보호하는 band다.
        if (shouldStayIntentOnlyForChat(
                grammarStage = grammarStage,
                vocabularyStage = vocabularyStage,
                fluencyStage = fluencyStage,
                pauseStage = pauseStage,
                utteranceStage = utteranceStage,
                structureStage = structureStage
            )
        ) {
            return ConversationAbilityBand.IntentOnly
        }

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

    private fun shouldStayIntentOnlyForChat(
        grammarStage: SkillStage,
        vocabularyStage: SkillStage,
        fluencyStage: SkillStage,
        pauseStage: SkillStage,
        utteranceStage: SkillStage,
        structureStage: SkillStage
    ): Boolean {
        // 낮은 band의 핵심은 "무엇을 아는가"보다 "학습언어만으로 대화가 이어지는가"다.
        val coreFoundationCount = listOf(grammarStage, vocabularyStage, fluencyStage)
            .count { it == SkillStage.Foundation }
        // 문장 구조와 이어 말하기가 모두 Foundation이면 단어/고정 표현 근거만으로 대화 가능 단계로 올릴 수 없다.
        val cannotBuildShortFreeSentence =
            structureStage == SkillStage.Foundation && utteranceStage == SkillStage.Foundation
        // grammar 자체가 Foundation인 상태에서 pause까지 높으면 사용자가 target-only 응답을 받기 어렵다.
        val conversationFlowBlocked =
            pauseStage == SkillStage.Foundation &&
                grammarStage == SkillStage.Foundation
        // vocabulary만 상대적으로 높아 보이는 상태는 "알고 있는 단어"일 수 있으므로 독립 대화 근거가 되지 않는다.
        val onlyVocabularyEvidence =
            vocabularyStage.ordinal > SkillStage.Foundation.ordinal &&
                grammarStage == SkillStage.Foundation &&
                fluencyStage == SkillStage.Foundation

        return coreFoundationCount >= 2 || cannotBuildShortFreeSentence || conversationFlowBlocked || onlyVocabularyEvidence
    }

    /**
     * 교정 성장 정책을 산출한다(COR-TUNE-003 / CHAT-TUNE-004 핸드오버).
     *
     * 흐름: [chooseCorrectionGrowthBand]로 band를 결정 → [CorrectionGrowthPolicy.defaultsForBand]로 기본 정책 매핑.
     * confidence가 낮으면 band를 낮춰 보수적 정책을 적용하고, 의미차단 focus가 있으면 패턴 안정화를 우선한다.
     */
    private fun buildCorrectionPolicy(
        confidence: ProfileConfidence,
        grammarStage: SkillStage,
        vocabularyStage: SkillStage,
        fluencyStage: SkillStage,
        naturalnessStage: SkillStage,
        focus: LearningFocusSummary,
        langState: LangState?
    ): CorrectionGrowthPolicy {
        val band = chooseCorrectionGrowthBand(
            confidence = confidence,
            grammarStage = grammarStage,
            vocabularyStage = vocabularyStage,
            fluencyStage = fluencyStage,
            naturalnessStage = naturalnessStage,
            focus = focus,
            langState = langState
        )
        return CorrectionGrowthPolicy.defaultsForBand(band)
    }

    /**
     * 교정 성장 band를 산출한다 (CHAT-TUNE-004 "산출 우선순위" 기반).
     *
     * 이 함수는 사용자가 "교정 결과를 이해하고 다시 쓸 수 있는가"를 우선한다.
     * 그래서 Chat band처럼 대화 지속 가능성만 보지 않고, 문장 구조와 correction evidence가
     * 상위 교정을 지지하는지까지 함께 확인한다.
     *
     * 산출 우선순위 (CHAT-TUNE-004 FlowDB 스펙 라인 378-383):
     *  1. meaningful LangState 없음 또는 ProfileConfidence.Low → 낮은 band로 보수 조정
     *  2. 의미 전달을 막는 active focus → MeaningFirst 또는 PatternFix
     *  3. grammarStage 또는 sentenceComplexity가 낮음 → SentenceShape 이하
     *  4. grammar/vocabulary/fluency가 안정될 때만 EverydayNatural 이상 허용
     *  5. naturalness+vocabulary 높고 confidence High → NuanceRefine
     */
    private fun chooseCorrectionGrowthBand(
        confidence: ProfileConfidence,
        grammarStage: SkillStage,
        vocabularyStage: SkillStage,
        fluencyStage: SkillStage,
        naturalnessStage: SkillStage,
        focus: LearningFocusSummary,
        langState: LangState?
    ): CorrectionGrowthBand {
        // metricEvidence는 "점수만 높은 상태"와 "반복 근거가 실제로 누적된 상태"를 구분하는 보수 게이트다.
        // null이면 근거가 없다는 뜻이므로 상위 band 판단에서 고점 metric만 믿지 않도록 한다.
        val evidenceProfile = langState?.correctionGrowthEvidenceProfile()

        // 1. Low confidence → 보수 조정. 하나의 고점 metric만으로 band를 올리지 않는다.
        if (confidence == ProfileConfidence.Low) {
            // Foundation이 지배적이면 MeaningFirst, 일부 vocabulary 근거가 있으면 PatternFix.
            return if (grammarStage.ordinal <= SkillStage.Foundation.ordinal &&
                vocabularyStage.ordinal <= SkillStage.Foundation.ordinal
            ) {
                CorrectionGrowthBand.MeaningFirst
            } else {
                CorrectionGrowthBand.PatternFix
            }
        }

        // evidence 방향이 충돌하거나 핵심 구조 지표에 반복 하락 근거가 있으면 자연스러움/뉘앙스 교정보다
        // 사용자가 다시 말할 수 있는 문장 형태 안정화를 우선한다.
        if (evidenceProfile?.hasMixedDirection == true || evidenceProfile?.hasRepeatedCoreWeakness == true) {
            return if (grammarStage == SkillStage.Foundation) {
                CorrectionGrowthBand.PatternFix
            } else {
                CorrectionGrowthBand.SentenceShape
            }
        }

        // 2. 의미 전달 오류나 핵심 문법 focus → 자연스러움 개선보다 패턴 안정화 우선.
        if (hasMeaningBlockingFocus(focus)) {
            return if (grammarStage == SkillStage.Foundation) {
                CorrectionGrowthBand.MeaningFirst
            } else {
                CorrectionGrowthBand.PatternFix
            }
        }

        // 3. grammarStage 또는 sentenceComplexity가 낮으면 SentenceShape 이하.
        val sentenceComplexityStage = langState?.let { stageFromScore(it.internal.sentenceComplexity) }
            ?: grammarStage // LangState가 없으면 grammar로 대리
        // sentenceComplexity는 문장 확장을 감당할 수 있는지 보는 독립 방어값이다.
        // grammar/vocabulary가 높아도 실제 문장 구조 근거가 낮으면 상위 natural/nuance 교정은 과할 수 있다.
        if (grammarStage.ordinal <= SkillStage.Developing.ordinal ||
            sentenceComplexityStage.ordinal <= SkillStage.Developing.ordinal
        ) {
            return if (grammarStage == SkillStage.Foundation) {
                CorrectionGrowthBand.PatternFix
            } else {
                CorrectionGrowthBand.SentenceShape
            }
        }

        // 4. grammar/vocabulary/fluency 중 2개 이상 Stable 이상이어야 EverydayNatural 허용.
        val stableCount = listOf(grammarStage, vocabularyStage, fluencyStage)
            .count { it.ordinal >= SkillStage.Stable.ordinal }
        if (stableCount < 2) {
            return CorrectionGrowthBand.SentenceShape
        }

        // 5. naturalness+vocabulary 높고 High confidence → NuanceRefine.
        if (confidence == ProfileConfidence.High &&
            naturalnessStage == SkillStage.Refined &&
            vocabularyStage.ordinal >= SkillStage.Expanding.ordinal &&
            evidenceProfile?.supportsNuanceRefine() == true
        ) {
            return CorrectionGrowthBand.NuanceRefine
        }

        // ConnectedExpression은 자연스러움/어휘 점수만으로 올리지 않는다.
        // "조금 어려운 다음 단계"를 유지하려면 최소 두 metric에서 반복 상승 근거가 있어야 한다.
        if (naturalnessStage.ordinal >= SkillStage.Expanding.ordinal &&
            vocabularyStage.ordinal >= SkillStage.Expanding.ordinal &&
            evidenceProfile?.supportsConnectedExpression() == true
        ) {
            return CorrectionGrowthBand.ConnectedExpression
        }

        return CorrectionGrowthBand.EverydayNatural
    }

    /**
     * 의미 전달을 막는 active focus 여부를 확인한다.
     *
     * 의미차단 focus: [LearningFocusType.SentenceFragment](불완전 문장), [LearningFocusType.MissingContext](주어/목적어 누락).
     * confidence Low 또는 관측 0이면 focus 게이트를 통과하지 않는다.
     */
    private fun hasMeaningBlockingFocus(focus: LearningFocusSummary): Boolean {
        if (focus.confidence == ProfileConfidence.Low || focus.observedCount <= 0) return false
        val meaningBlockingTypes = setOf(
            LearningFocusType.SentenceFragment,
            LearningFocusType.MissingContext
        )
        return focus.primaryFocus in meaningBlockingTypes || focus.secondaryFocus in meaningBlockingTypes
    }

    /**
     * correction band 산출에 필요한 evidence만 작게 요약한다.
     *
     * raw metric 숫자가 좋아 보여도 evidence가 충돌하거나 핵심 구조 지표가 반복적으로 내려가면
     * 상위 교정 band를 주지 않기 위한 gate다. 이 요약은 저장하지 않고 profile 계산 중에만 사용한다.
     */
    private fun LangState.correctionGrowthEvidenceProfile(): CorrectionGrowthEvidenceProfile {
        // Correction band는 ChatSession-only evidence로 상향/하향되지 않아야 한다.
        val correctionScopedEvidence = analysisMeta.metricEvidence.filterValues { evidence ->
            !evidence.isChatOnlyEvidence()
        }
        val evidenceValues = correctionScopedEvidence.values
        val hasMixedDirection = evidenceValues.any { evidence ->
            evidence.direction == EvidenceDirection.Mixed
        }
        val repeatedCoreWeaknessKeys = setOf(
            LearningMetricKey.GrammarAccuracy,
            LearningMetricKey.SentenceComplexity,
            LearningMetricKey.VocabularyAppropriateness
        )
        val hasRepeatedCoreWeakness = correctionScopedEvidence.any { (key, evidence) ->
            key in repeatedCoreWeaknessKeys &&
                evidence.direction == EvidenceDirection.Down &&
                evidence.observedCount >= MEDIUM_EVIDENCE_COUNT &&
                evidence.confidence >= MEDIUM_CONFIDENCE_SCORE
        }
        val positiveSupportCount = correctionScopedEvidence.count { (_, evidence) ->
            evidence.direction == EvidenceDirection.Up &&
                evidence.directionCount >= MEDIUM_EVIDENCE_COUNT &&
                evidence.confidence >= MEDIUM_CONFIDENCE_SCORE
        }
        val strongPositiveSupportCount = correctionScopedEvidence.count { (_, evidence) ->
            evidence.direction == EvidenceDirection.Up &&
                evidence.directionCount >= MEDIUM_EVIDENCE_COUNT &&
                evidence.confidence >= HIGH_CONFIDENCE_SCORE
        }

        return CorrectionGrowthEvidenceProfile(
            hasMixedDirection = hasMixedDirection,
            hasRepeatedCoreWeakness = hasRepeatedCoreWeakness,
            positiveSupportCount = positiveSupportCount,
            strongPositiveSupportCount = strongPositiveSupportCount
        )
    }

    private data class CorrectionGrowthEvidenceProfile(
        val hasMixedDirection: Boolean,
        val hasRepeatedCoreWeakness: Boolean,
        val positiveSupportCount: Int,
        val strongPositiveSupportCount: Int
    ) {
        // ConnectedExpression은 자연스러움/어휘 고점만으로 올리지 않고, 최소 두 축의 반복 상승 근거가 있어야 한다.
        fun supportsConnectedExpression(): Boolean = positiveSupportCount >= CONNECTED_EXPRESSION_EVIDENCE_COUNT

        // NuanceRefine은 가장 높은 교정 band이므로 강한 상승 근거가 여러 축에서 반복될 때만 허용한다.
        fun supportsNuanceRefine(): Boolean = strongPositiveSupportCount >= NUANCE_REFINE_EVIDENCE_COUNT
    }

    private fun Collection<MetricEvidence>.excludeChatOnlyEvidence(): List<MetricEvidence> {
        return filterNot { evidence -> evidence.isChatOnlyEvidence() }
    }

    private fun MetricEvidence.isChatOnlyEvidence(): Boolean {
        return sourceTypes == setOf(LearningSignalSource.ChatSession)
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
        private const val CONNECTED_EXPRESSION_EVIDENCE_COUNT = 2
        private const val NUANCE_REFINE_EVIDENCE_COUNT = 3
    }
}
