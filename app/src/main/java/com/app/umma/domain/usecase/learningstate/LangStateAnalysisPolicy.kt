package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.CorrectionImprovementType
import com.app.umma.domain.model.learningstate.CorrectionIssueCategory
import com.app.umma.domain.model.learningstate.CorrectionLearningSignal
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningFocus
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.VocabLevel
import javax.inject.Inject

/**
 * LangState의 다음 snapshot을 계산하는 domain policy.
 *
 * Repository는 저장과 sync만 담당해야 하므로, 사용자 발화/교정/복습 입력에서
 * 언어능력 지표를 계산하는 책임은 이 policy에 둔다.
 */
interface LangStateAnalysisPolicy {
    /**
     * 현재 LangState와 분석 입력을 받아 다음 저장 후보를 만든다.
     *
     * 이 함수는 저장을 수행하지 않는다. 저장 orchestration과 중복 분석 방어는
     * ApplyLanguageStateUpdateUseCase와 LearningStateRepo 계약이 담당한다.
     */
    fun analyze(input: LangStateUpdateInput): LangState
}

/**
 * 기존 prepareNextState 로직을 기준으로 correction learning signal 해석을 확장한 기본 정책.
 *
 * signal이 없을 때는 기존 MVP 휴리스틱을 그대로 유지하고, signal이 있을 때만
 * evidence/focus와 과도한 correction 확장 방어를 추가한다.
 */
class DefaultLangStateAnalysisPolicy @Inject constructor() : LangStateAnalysisPolicy {
    override fun analyze(input: LangStateUpdateInput): LangState {
        // 계산은 현재 저장된 snapshot을 기준으로만 시작한다.
        // 여기서 별도 DB 조회를 하면 policy가 저장소 의존성을 다시 가져버린다.
        val current = input.currentState
        // update 시점은 저장 결과와 분석 결과가 같은 timestamp를 보게 맞춘다.
        val now = input.analyzedAt
        // Correction signal은 raw DTO가 아니라 domain 계약으로 들어온 관찰 신호다.
        // 검증은 analyze 초입에서 한 번만 수행해 이후 metric/evidence/focus가 같은 입력을 공유하게 한다.
        val validatedSignals = input.validatedCorrectionSignals()

        // 이번 batch에서 추정 가능한 측정값만 뽑는다.
        // null인 측정값은 이후 smoothMetric에서 기존 상태를 유지하게 만든다.
        // 각 helper는 "이번 입력에서 이 지표를 읽을 수 있는가"를 먼저 판단한다.
        val measuredGrammarAccuracy = measureGrammarAccuracy(
            input = input,
            validatedSignals = validatedSignals
        )
        val measuredVocabularyAppropriateness = measureVocabularyAppropriateness(
            input = input,
            validatedSignals = validatedSignals
        )
        val measuredLexicalDiversity = measureLexicalDiversity(input)
        val measuredSentenceComplexity = measureSentenceComplexity(input)
        val measuredSpeechRate = measureSpeechRate(input)
        val measuredAvgUtteranceLength = measureAvgUtteranceLength(input)
        val measuredReviewRetention = measureReviewRetention(input)
        val measuredNaturalness = measureNaturalness(
            input = input,
            validatedSignals = validatedSignals
        )
        val measuredNaturalExpressionUsage = measureNaturalExpressionUsage(
            input = input,
            measuredNaturalness = measuredNaturalness,
            measuredVocabularyAppropriateness = measuredVocabularyAppropriateness
        )
        val measuredErrorRecurrence = measureErrorRecurrence(
            input = input,
            validatedSignals = validatedSignals
        )
        val nextExpressionRange = calculateExpressionRange(
            previous = current.external.expressionRange,
            input = input
        )
        val measuredVocabularyLevel = estimateVocabularyLevel(
            expressionRange = nextExpressionRange,
            lexicalDiversity = measuredLexicalDiversity,
            sentenceComplexity = measuredSentenceComplexity
        )
        val nextVocabularyLevel = moveVocabularyLevelOneStep(
            previous = current.internal.vocabularyLevel,
            measured = measuredVocabularyLevel
        )

        // 기존 값에서 급격히 흔들리지 않도록 이동 평균을 적용한다.
        // 이 비율은 기존 prepareNextState와 동일해야 B 1차 결과 보존 AC를 만족한다.
        // 즉, policy 분리 후에도 숫자가 같은지 테스트로 확인할 수 있어야 한다.
        val nextInternal = current.internal.copy(
            grammarAccuracy = smoothMetric(current.internal.grammarAccuracy, measuredGrammarAccuracy),
            vocabularyAppropriateness = smoothMetric(
                current.internal.vocabularyAppropriateness,
                measuredVocabularyAppropriateness
            ),
            lexicalDiversity = smoothMetric(
                current.internal.lexicalDiversity,
                measuredLexicalDiversity
            ),
            vocabularyLevel = nextVocabularyLevel,
            sentenceComplexity = smoothMetric(
                current.internal.sentenceComplexity,
                measuredSentenceComplexity
            ),
            speechRate = smoothMetric(current.internal.speechRate, measuredSpeechRate),
            avgUtteranceLength = smoothMetric(
                current.internal.avgUtteranceLength,
                measuredAvgUtteranceLength
            ),
            reviewRetention = smoothMetric(current.internal.reviewRetention, measuredReviewRetention),
            spokenNaturalness = smoothMetric(current.internal.spokenNaturalness, measuredNaturalness),
            naturalExpressionUsage = smoothMetric(
                current.internal.naturalExpressionUsage,
                measuredNaturalExpressionUsage
            ),
            errorRecurrence = smoothMetric(current.internal.errorRecurrence, measuredErrorRecurrence)
        )

        // 외부 노출용 점수는 내부 지표를 다시 묶어서 계산한다.
        // Dashboard/Statistics는 이 snapshot을 그대로 읽으므로 기존 산출식을 유지한다.
        // 외부 점수를 바꾸면 화면과 통계가 함께 흔들리므로 여기서는 재계산 규칙을 그대로 둔다.
        val nextExternal = current.external.copy(
            vocabularyLevel = nextInternal.vocabularyLevel,
            grammarAccuracy = nextInternal.grammarAccuracy,
            expressionRange = nextExpressionRange,
            fluencyScore = calculateFluencyScore(nextInternal),
            naturalnessScore = calculateNaturalnessScore(nextInternal)
        )
        // analysisMeta는 장기 score와 별도로 "왜 이 방향으로 움직일 수 있는지"를 저장한다.
        // signal이 없으면 기존 meta를 보존해 CHAT-TUNE-001/002 결과를 흔들지 않는다.
        val nextAnalysisMeta = updateAnalysisMeta(
            current = current.analysisMeta,
            currentState = current,
            signals = validatedSignals,
            observedAt = now
        )

        return current.copy(
            internal = nextInternal,
            external = nextExternal,
            analysisMeta = nextAnalysisMeta,
            updatedAt = now,
            lastAnalyzedAt = now,
            // 같은 분석 이벤트가 다시 오더라도 상위 use case가 먼저 걸러내고,
            // 여기서는 계산 결과에 event id를 다시 실어 저장 경계를 맞춘다.
            lastAnalysisEventId = input.analysisEventId ?: current.lastAnalysisEventId
        )
    }

    private fun measureGrammarAccuracy(
        input: LangStateUpdateInput,
        validatedSignals: List<CorrectionLearningSignal>
    ): Double? {
        // 교정 수가 많을수록 정확도는 낮다고 본다.
        // userTurns 수가 0이면 비교 기준이 없으므로 null을 돌려 기존 값을 유지하게 한다.
        val userTurns = input.recentUserTurns.count { it.speaker == TurnSpeaker.USER }
        if (userTurns <= 0) return null

        // correctionCount는 실제 교정 밀도의 대용값이므로, 현재는 단순 비율로만 본다.
        val correctionCount = input.effectiveCorrectionCountForLongTermScore(validatedSignals)
        val rawScore = 1.0 - (correctionCount.toDouble() / userTurns.toDouble())
        return clamp01(rawScore)
    }

    private fun measureVocabularyAppropriateness(
        input: LangStateUpdateInput,
        validatedSignals: List<CorrectionLearningSignal>
    ): Double? {
        // 현재 별도 Type C AI 점수가 없으므로, 어휘 다양성과 교정 밀도를 함께 본다.
        // 교정이 적고 표현 폭이 넓을수록 문맥에 맞는 어휘 선택으로 간주하는 MVP 근사치다.
        val lexicalDiversity = measureLexicalDiversity(input) ?: return null
        // userTurns가 비어 있으면 correctionPenalty를 계산해도 의미가 없으므로 중단한다.
        val userTurns = input.analyzableUserTurns()
        if (userTurns.isEmpty()) return null

        // 교정이 많을수록 문맥 적합성이 낮았다고 보는 보수적 근사치다.
        val correctionCount = input.effectiveCorrectionCountForLongTermScore(validatedSignals)
        val correctionPenalty = correctionCount.toDouble() / (userTurns.size.toDouble() * 2.0)
        val correctionScore = 1.0 - correctionPenalty
        return clamp01((lexicalDiversity * 0.6) + (correctionScore * 0.4))
    }

    private fun measureLexicalDiversity(input: LangStateUpdateInput): Double? {
        // lexical diversity는 "고유 token 수 / 전체 token 수"로 계산한다.
        // SessionMemory에 정교한 형태소 정보가 없기 때문에 텍스트 tokenization은 domain 내부의 단순 규칙으로 제한한다.
        // token이 없으면 ratio 자체가 성립하지 않으므로 null로 돌려 기존 값을 유지하게 한다.
        val tokens = input.userWordTokens()
        if (tokens.isEmpty()) return null

        return clamp01(tokens.toSet().size.toDouble() / tokens.size.toDouble())
    }

    private fun measureSentenceComplexity(input: LangStateUpdateInput): Double? {
        // 복잡도는 긴 발화와 접속/절 단서를 함께 본다.
        // 문법 파서가 아직 없으므로, 데모/초기 real 데이터에서 0으로 고정되지 않게 만드는 안정적인 근사치다.
        val userTurns = input.analyzableUserTurns()
        if (userTurns.isEmpty()) return null

        // 긴 문장만으로는 과평가될 수 있어서 구조 단서도 같이 섞는다.
        val averageTokenCount = userTurns.averageTokenCount()
        val lengthScore = averageTokenCount / SENTENCE_COMPLEXITY_TARGET_TOKENS
        val structureHints = userTurns.sumOf { turn ->
            CONNECTOR_REGEX.findAll(turn.text).count() + CLAUSE_PUNCTUATION_REGEX.findAll(turn.text).count()
        }
        val structureScore = structureHints.toDouble() / (userTurns.size.toDouble() * STRUCTURE_HINTS_TARGET)
        return clamp01((lengthScore * 0.65) + (structureScore * 0.35))
    }

    private fun measureSpeechRate(input: LangStateUpdateInput): Double? {
        // 발화 토큰 수와 지속 시간을 이용해 대략적인 속도를 만든다.
        // 속도 계산은 음성 입력 데이터가 실제로 있을 때만 가능하다.
        val totalTokens = input.recentUserTurns.sumOf { it.tokenCount ?: 0 }
        val totalDurationMs = input.recentUserTurns.sumOf { it.durationMs ?: 0L }
        if (totalTokens <= 0 || totalDurationMs <= 0L) return null

        // 초당 토큰 수를 바로 점수로 쓰지 않고, 전체 범위에 맞게 normalize한다.
        val tokensPerSecond = totalTokens.toDouble() / (totalDurationMs.toDouble() / 1_000.0)
        return clamp01(tokensPerSecond / 4.0)
    }

    private fun measureAvgUtteranceLength(input: LangStateUpdateInput): Double? {
        // 내부 지표는 raw token count가 아니라 0~1 점수로 저장한다.
        // 평균 14 token 정도를 안정적인 발화 길이 기준으로 보고 normalize한다.
        // 긴 답변이 항상 좋은 것은 아니므로 상한을 둔 정규화만 한다.
        val userTurns = input.analyzableUserTurns()
        if (userTurns.isEmpty()) return null

        return clamp01(userTurns.averageTokenCount() / AVG_UTTERANCE_TARGET_TOKENS)
    }

    private fun measureReviewRetention(input: LangStateUpdateInput): Double? {
        // 복습 정답 비율을 그대로 retention 후보로 쓴다.
        // flashcard 이벤트가 없으면 복습 효과를 판단할 재료가 없으므로 null을 돌린다.
        val reviews = input.flashcardReviewEvents
        if (reviews.isEmpty()) return null

        val correctRate = reviews.count { it.isCorrect }.toDouble() / reviews.size.toDouble()
        return clamp01(correctRate)
    }

    private fun measureNaturalness(
        input: LangStateUpdateInput,
        validatedSignals: List<CorrectionLearningSignal>
    ): Double? {
        // 교정이 적을수록 자연스럽다고 간주하는 MVP용 근사치다.
        // userTurns가 없으면 naturalness도 측정할 수 없으므로 null을 반환한다.
        val userTurns = input.recentUserTurns.count { it.speaker == TurnSpeaker.USER }
        if (userTurns <= 0) return null

        // correctionCount가 많을수록 자연스러움이 낮다고 보는 단순 근사치다.
        val correctionCount = input.effectiveCorrectionCountForLongTermScore(validatedSignals)
        val rawScore = 1.0 - (correctionCount.toDouble() / (userTurns.toDouble() * 2.0))
        return clamp01(rawScore)
    }

    private fun measureNaturalExpressionUsage(
        input: LangStateUpdateInput,
        measuredNaturalness: Double?,
        measuredVocabularyAppropriateness: Double?
    ): Double? {
        // Type C AI가 아직 naturalExpressionUsage를 직접 주지 않으므로,
        // 교정 결과가 있는 batch에서는 자연스러움과 어휘 적절성을 결합해 최소 값을 만든다.
        if (input.analyzableUserTurns().isEmpty()) return null

        // 둘 중 하나만 있으면 그 값을 그대로 쓰고, 둘 다 있으면 균형값으로 섞는다.
        val naturalness = measuredNaturalness ?: return measuredVocabularyAppropriateness
        val vocabularyAppropriateness = measuredVocabularyAppropriateness ?: return naturalness
        return clamp01((naturalness * 0.7) + (vocabularyAppropriateness * 0.3))
    }

    private fun measureErrorRecurrence(
        input: LangStateUpdateInput,
        validatedSignals: List<CorrectionLearningSignal>
    ): Double? {
        // 진짜 "반복 오류"는 이전 correction signature와 현재 오류를 비교해야 한다.
        // 현재 모델에는 signature 저장소가 없으므로, correction density를 낮은 신뢰도의 proxy로만 저장한다.
        // future signal이 들어오면 이 helper를 대체하면 된다.
        val userTurns = input.analyzableUserTurns()
        if (userTurns.isEmpty() || input.correctionResult == null) return null

        val correctionCount = input.effectiveCorrectionCountForLongTermScore(validatedSignals)
        return clamp01(correctionCount.toDouble() / (userTurns.size.toDouble() * 2.0))
    }

    private fun LangStateUpdateInput.effectiveCorrectionCountForLongTermScore(
        validatedSignals: List<CorrectionLearningSignal>
    ): Int {
        val result = correctionResult ?: return 0
        val signals = result.learningSignals
        if (signals.isEmpty()) return result.correctionCount

        // learning signal이 명시적으로 들어온 경우에는 의미 보존이 확인된 signal만
        // 장기 score 이동에 사용한다. 의미가 바뀐 rewrite나 과도한 확장을
        // 사용자의 실제 능력 변화로 오해하지 않기 위해서다.
        val longTermEligibleCount = validatedSignals.count { signal ->
            signal.isLongTermScoreEligible(currentState)
        }
        return longTermEligibleCount.coerceAtMost(result.correctionCount.coerceAtLeast(0))
    }

    private fun LangStateUpdateInput.validatedCorrectionSignals(): List<CorrectionLearningSignal> {
        val signals = correctionResult?.learningSignals.orEmpty()
        if (signals.isEmpty()) return emptyList()

        return signals.mapNotNull { signal ->
            // confidence가 범위를 벗어나면 raw AI 판단이 오염된 상태라 signal 전체를 버린다.
            if (signal.confidence != null && signal.confidence !in 0.0..1.0) return@mapNotNull null
            // 필수 문자열이 비어 있으면 source/corrected 비교와 focus 추적이 모두 불가능하다.
            if (signal.candidateId.isBlank() || signal.sourceText.isBlank() || signal.correctedText.isBlank()) {
                return@mapNotNull null
            }
            // sourceTurnIndex는 sourceTurnId가 없을 때의 fallback이라 음수면 추적 근거로 쓸 수 없다.
            if (signal.sourceTurnIndex < 0) return@mapNotNull null

            val features = signal.languageFeatures
                .asSequence()
                // 언어가 다르면 다른 selectedLang의 feature가 섞인 것이므로 해당 feature만 제외한다.
                .filter { feature -> feature.lang == lang }
                // namespace가 없는 값은 prompt/focus에 안전하게 변환할 수 없다.
                .filter { feature -> feature.featureKey.startsWith("${lang.name}.", ignoreCase = true) }
                .filter { feature -> feature.featureKey.substringAfter('.', missingDelimiterValue = "").isNotBlank() }
                .take(MAX_SIGNAL_ITEMS)
                .toList()
            val editSpans = signal.editSpans
                .asSequence()
                // fragment가 비어 있으면 rewrite guard 근거가 아니라 noise가 되므로 제외한다.
                .filter { span -> span.sourceFragment.isNotBlank() && span.correctedFragment.isNotBlank() }
                .take(MAX_SIGNAL_ITEMS)
                .toList()

            signal.copy(
                issueCategories = signal.issueCategories.take(MAX_SIGNAL_ITEMS),
                languageFeatures = features,
                improvementTypes = signal.improvementTypes.take(MAX_SIGNAL_ITEMS),
                editSpans = editSpans
            )
        }
    }

    private fun updateAnalysisMeta(
        current: LangStateAnalysisMeta,
        currentState: LangState,
        signals: List<CorrectionLearningSignal>,
        observedAt: Long
    ): LangStateAnalysisMeta {
        if (signals.isEmpty()) return current

        val nextEvidence = signals.fold(current.metricEvidence) { evidence, signal ->
            val metricUpdates = signal.metricUpdates(currentState)
            metricUpdates.fold(evidence) { acc, update ->
                acc + (update.key to mergeEvidence(
                    previous = acc[update.key],
                    direction = update.direction,
                    confidence = signal.weightedConfidence(),
                    observedAt = observedAt
                ))
            }
        }
        val nextFocus = mergeFocuses(
            current = current.activeFocus,
            signals = signals,
            observedAt = observedAt
        )

        return current.copy(
            metricEvidence = nextEvidence,
            activeFocus = nextFocus,
            lastSignalAt = observedAt
        )
    }

    private fun CorrectionLearningSignal.metricUpdates(currentState: LangState): List<MetricDirectionUpdate> {
        val issueDrivenUpdates = issueCategories.flatMap { category ->
            when (category) {
                CorrectionIssueCategory.GrammarForm -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.GrammarAccuracy, EvidenceDirection.Down)
                )
                CorrectionIssueCategory.WordOrder -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.GrammarAccuracy, EvidenceDirection.Down),
                    MetricDirectionUpdate(LearningMetricKey.SentenceComplexity, EvidenceDirection.Down)
                )
                CorrectionIssueCategory.SentenceCompleteness,
                CorrectionIssueCategory.MissingContext -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.SentenceComplexity, EvidenceDirection.Down)
                )
                CorrectionIssueCategory.VocabularyChoice -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.VocabularyAppropriateness, EvidenceDirection.Down)
                )
                CorrectionIssueCategory.Collocation -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.NaturalExpressionUsage, EvidenceDirection.Down),
                    MetricDirectionUpdate(LearningMetricKey.VocabularyAppropriateness, EvidenceDirection.Down)
                )
                CorrectionIssueCategory.Register -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.SpokenNaturalness, EvidenceDirection.Down)
                )
                CorrectionIssueCategory.MeaningMismatch -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.GrammarAccuracy, EvidenceDirection.Down),
                    MetricDirectionUpdate(LearningMetricKey.SentenceComplexity, EvidenceDirection.Down)
                )
            }
        }
        val improvementDrivenUpdates = improvementTypes.flatMap { improvement ->
            when (improvement) {
                CorrectionImprovementType.GrammarFixed -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.GrammarAccuracy, EvidenceDirection.Up)
                )
                CorrectionImprovementType.StructureExpanded -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.SentenceComplexity, EvidenceDirection.Up)
                )
                CorrectionImprovementType.MoreNaturalVerb,
                CorrectionImprovementType.BetterCollocation,
                CorrectionImprovementType.SpokenExpressionAdded -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.NaturalExpressionUsage, EvidenceDirection.Up),
                    MetricDirectionUpdate(LearningMetricKey.VocabularyAppropriateness, EvidenceDirection.Up)
                )
                CorrectionImprovementType.ShortenedForClarity -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.SentenceComplexity, EvidenceDirection.Stable)
                )
                CorrectionImprovementType.MadeMoreCasual,
                CorrectionImprovementType.MadeMorePolite -> listOf(
                    MetricDirectionUpdate(LearningMetricKey.SpokenNaturalness, EvidenceDirection.Up)
                )
            }
        }

        // 의미가 보존되지 않은 correction은 "좋아졌다"는 근거로 쓰지 않는다.
        // 과도하게 확장된 correction도 사용자가 실제로 그 수준을 구사했다는 뜻은 아니므로
        // improvement 방향 evidence는 잠시 막고, 관찰된 issue/focus만 남긴다.
        val rawUpdates = if (meaningPreserved && !isOverExpandedForCurrentAbility(currentState)) {
            issueDrivenUpdates + improvementDrivenUpdates
        } else {
            issueDrivenUpdates
        }
        // 하나의 correction 후보 안에서 같은 metric의 issue와 improvement가 동시에 관측될 수 있다.
        // 이때 observedCount를 두 번 올리면 한 후보가 두 관측처럼 과대 반영되므로 metric별 1회로 합친다.
        return rawUpdates
            .groupBy { update -> update.key }
            .map { (key, updates) ->
                MetricDirectionUpdate(
                    key = key,
                    direction = mergeDirectionsWithinSignal(updates.map { update -> update.direction })
                )
            }
    }

    private fun mergeDirectionsWithinSignal(directions: List<EvidenceDirection>): EvidenceDirection {
        val meaningfulDirections = directions.filter { direction -> direction != EvidenceDirection.Stable }.distinct()
        return when {
            meaningfulDirections.isEmpty() -> EvidenceDirection.Stable
            meaningfulDirections.size == 1 -> meaningfulDirections.first()
            else -> EvidenceDirection.Mixed
        }
    }

    private fun mergeEvidence(
        previous: MetricEvidence?,
        direction: EvidenceDirection,
        confidence: Double,
        observedAt: Long
    ): MetricEvidence {
        if (previous == null) {
            return MetricEvidence(
                observedCount = 1,
                confidence = confidence,
                sourceTypes = setOf(LearningSignalSource.CorrectionSignal),
                direction = direction,
                directionCount = 1,
                lastObservedAt = observedAt
            )
        }

        val mergedDirection = when {
            previous.direction == direction -> direction
            previous.direction == EvidenceDirection.Mixed -> EvidenceDirection.Mixed
            direction == EvidenceDirection.Stable -> previous.direction
            previous.direction == EvidenceDirection.Stable -> direction
            else -> EvidenceDirection.Mixed
        }
        val directionCount = if (previous.direction == direction) previous.directionCount + 1 else 1
        val observedCount = previous.observedCount + 1
        val mergedConfidence = ((previous.confidence * previous.observedCount) + confidence) / observedCount

        return previous.copy(
            observedCount = observedCount,
            confidence = clamp01(mergedConfidence),
            sourceTypes = previous.sourceTypes + LearningSignalSource.CorrectionSignal,
            direction = mergedDirection,
            directionCount = directionCount,
            lastObservedAt = observedAt
        )
    }

    private fun mergeFocuses(
        current: List<LearningFocus>,
        signals: List<CorrectionLearningSignal>,
        observedAt: Long
    ): List<LearningFocus> {
        val focusUpdates = signals.flatMap { signal -> signal.focusUpdates() }
        if (focusUpdates.isEmpty()) return current

        val byType = current.associateBy { it.type }.toMutableMap()
        focusUpdates.forEach { update ->
            val previous = byType[update.type]
            if (previous == null) {
                byType[update.type] = LearningFocus(
                    type = update.type,
                    observedCount = 1,
                    confidence = update.confidence,
                    firstObservedAt = observedAt,
                    lastObservedAt = observedAt
                )
            } else {
                val observedCount = previous.observedCount + 1
                val mergedConfidence = ((previous.confidence * previous.observedCount) + update.confidence) / observedCount
                byType[update.type] = previous.copy(
                    observedCount = observedCount,
                    confidence = clamp01(mergedConfidence),
                    lastObservedAt = observedAt
                )
            }
        }

        // active focus는 prompt/profile에 이어지므로 오래된 전체 목록을 무한히 키우지 않는다.
        return byType.values.sortedWith(
            compareByDescending<LearningFocus> { it.confidence }
                .thenByDescending { it.observedCount }
                .thenByDescending { it.lastObservedAt }
        ).take(MAX_ACTIVE_FOCUS_COUNT)
    }

    private fun CorrectionLearningSignal.focusUpdates(): List<FocusUpdate> {
        val categoryFocuses = issueCategories.mapNotNull { category ->
            when (category) {
                CorrectionIssueCategory.GrammarForm -> null
                CorrectionIssueCategory.WordOrder -> LearningFocusType.WordOrder
                CorrectionIssueCategory.SentenceCompleteness -> LearningFocusType.SentenceFragment
                CorrectionIssueCategory.VocabularyChoice -> LearningFocusType.VocabularyChoice
                CorrectionIssueCategory.Collocation -> LearningFocusType.UnnaturalCollocation
                CorrectionIssueCategory.Register -> LearningFocusType.TooFormal
                CorrectionIssueCategory.MissingContext -> LearningFocusType.MissingContext
                CorrectionIssueCategory.MeaningMismatch -> LearningFocusType.MissingContext
            }
        }
        val featureFocuses = languageFeatures.mapNotNull { feature ->
            when {
                feature.featureKey.equals("${feature.lang.name}.Article", ignoreCase = true) -> LearningFocusType.Article
                feature.featureKey.equals("${feature.lang.name}.Tense", ignoreCase = true) -> LearningFocusType.Tense
                feature.featureKey.equals("${feature.lang.name}.Preposition", ignoreCase = true) -> LearningFocusType.Preposition
                else -> null
            }
        }
        val confidence = weightedConfidence()

        return (categoryFocuses + featureFocuses)
            .distinct()
            .map { focusType -> FocusUpdate(type = focusType, confidence = confidence) }
    }

    private fun CorrectionLearningSignal.isLongTermScoreEligible(currentState: LangState): Boolean {
        // 의미가 바뀐 correction은 사용자 실력 변화가 아니라 correction 품질/해석 위험 신호로만 본다.
        if (!meaningPreserved) return false
        // 낮은 confidence signal은 focus 후보로는 쓸 수 있지만 장기 score를 움직이기에는 근거가 약하다.
        if (normalizedConfidence() < MIN_SCORE_SIGNAL_CONFIDENCE) return false
        // source/corrected 비교로 과도한 확장이 감지되면 장기 score 반영을 막는다.
        // 이 guard는 Correction AI가 difficultyDelta를 직접 판단하지 않게 하려는 문서 계약을 코드화한 것이다.
        if (isOverExpandedForCurrentAbility(currentState)) return false

        return true
    }

    private fun CorrectionLearningSignal.isOverExpandedForCurrentAbility(currentState: LangState): Boolean {
        val sourceTokenCount = sourceText.tokenCount()
        val correctedTokenCount = correctedText.tokenCount()
        // source/corrected token이 없으면 guard를 추정으로 작동시키지 않는다.
        // validation에서 blank는 이미 제거되지만, tokenization 실패 가능성까지 방어한다.
        if (sourceTokenCount <= 0 || correctedTokenCount <= 0) return false

        val addedTokenCount = correctedTokenCount - sourceTokenCount
        val growthRatio = addedTokenCount.toDouble() / sourceTokenCount.toDouble()
        val hasStretchIntent = improvementTypes.any { improvement ->
            improvement == CorrectionImprovementType.StructureExpanded ||
                improvement == CorrectionImprovementType.BetterCollocation ||
                improvement == CorrectionImprovementType.SpokenExpressionAdded
        }
        val editExpansionCount = editSpans.count { span ->
            span.correctedFragment.tokenCount() > span.sourceFragment.tokenCount()
        }
        val userNeedsBasicSupport = currentState.internal.basicSupportAverage() < LOW_ABILITY_GUARD_THRESHOLD
        val confidenceIsNotStrong = normalizedConfidence() < STRONG_SIGNAL_CONFIDENCE
        val textExpandedTooMuch = addedTokenCount >= MIN_OVER_EXPANSION_TOKENS && growthRatio >= OVER_EXPANSION_RATIO
        val editSpansShowExpansion = editSpans.size >= MAX_SIGNAL_ITEMS && editExpansionCount >= 2

        // 큰 문장 확장과 stretch intent가 함께 있을 때만 guard를 건다.
        // 단순 시제/관사 수정처럼 길이가 조금 늘어난 교정을 과도하게 막지 않기 위해서다.
        return hasStretchIntent &&
            (textExpandedTooMuch || editSpansShowExpansion) &&
            (userNeedsBasicSupport || confidenceIsNotStrong)
    }

    private fun CorrectionLearningSignal.weightedConfidence(): Double {
        val base = normalizedConfidence()
        val severityWeight = when (severity) {
            CorrectionSeverity.BlockingMeaning -> 1.0
            CorrectionSeverity.MajorPattern -> 0.9
            CorrectionSeverity.MinorForm -> 0.75
            CorrectionSeverity.NaturalnessOnly -> 0.65
        }
        val meaningWeight = if (meaningPreserved) 1.0 else 0.45
        return clamp01(base * severityWeight * meaningWeight)
    }

    private fun CorrectionLearningSignal.normalizedConfidence(): Double {
        // null confidence는 신호를 버릴 정도는 아니지만 high confidence로 보지 않는다.
        return confidence ?: DEFAULT_SIGNAL_CONFIDENCE
    }

    private fun InternalMetrics.basicSupportAverage(): Double {
        // correction 확장 guard는 사용자의 말하기 전반이 아직 낮은지 판단할 때만 강하게 작동한다.
        // 이 평균은 외부 표시 점수가 아니라 내부 metric만 사용해 중복 projection을 피한다.
        return listOf(
            grammarAccuracy,
            vocabularyAppropriateness,
            sentenceComplexity,
            spokenNaturalness,
            naturalExpressionUsage
        ).average().coerceIn(0.0, 1.0)
    }

    private fun calculateExpressionRange(
        previous: Int,
        input: LangStateUpdateInput
    ): Int {
        // expressionRange는 외부 통계에 직접 노출되는 표현 폭 지표다.
        // 별도 signature 저장소가 생기기 전까지는 "누적 신규 단어 수"로 계산하지 않는다.
        // 대신 현재까지 관찰된 batch-level 고유 token 수의 최대값으로 관리해 반복 표현이 값을 계속 부풀리지 않게 한다.
        // previous보다 작아지는 경우는 의도치 않은 회귀이므로 기존 값을 보존한다.
        val uniqueTokenCount = input.userWordTokens().toSet().size
        if (uniqueTokenCount <= 0) return previous.coerceAtLeast(0)

        return maxOf(previous, uniqueTokenCount).coerceAtLeast(0)
    }

    private fun estimateVocabularyLevel(
        expressionRange: Int,
        lexicalDiversity: Double?,
        sentenceComplexity: Double?
    ): VocabLevel {
        // CEFR 사전 매핑이 붙기 전까지는 표현 폭을 중심으로 등급 후보를 만든다.
        // 다양성과 문장 복잡도는 표현 폭만으로 과소평가되는 경우를 보정하는 보조 신호다.
        // 단일 신호가 극단적으로 높아도 한 단계만 이동시키는 상위 정책과 맞물려 사용한다.
        val diversity = lexicalDiversity ?: 0.0
        val complexity = sentenceComplexity ?: 0.0
        return when {
            expressionRange >= 80 || complexity >= 0.85 -> VocabLevel.C2
            expressionRange >= 55 || complexity >= 0.72 -> VocabLevel.C1
            expressionRange >= 35 || complexity >= 0.58 -> VocabLevel.B2
            expressionRange >= 20 || complexity >= 0.44 -> VocabLevel.B1
            expressionRange >= 8 || diversity >= 0.45 -> VocabLevel.A2
            else -> VocabLevel.A1
        }
    }

    private fun moveVocabularyLevelOneStep(
        previous: VocabLevel,
        measured: VocabLevel
    ): VocabLevel {
        // 문서 정책상 vocabularyLevel은 한 번의 분석으로 급격히 뛰지 않는다.
        // 측정 후보가 여러 단계 차이 나더라도 update 1회당 최대 1단계만 이동시킨다.
        // 이렇게 해야 review/summary 숫자가 한 번에 튀지 않는다.
        val delta = measured.ordinal - previous.ordinal
        return when {
            delta > 0 -> VocabLevel.entries[previous.ordinal + 1]
            delta < 0 -> VocabLevel.entries[previous.ordinal - 1]
            else -> previous
        }
    }

    private fun smoothMetric(previous: Double, measured: Double?): Double {
        // 측정값이 없으면 기존 값을 유지한다.
        // null을 0으로 바꾸면 "관측 실패"가 "실력 하락"으로 오해되므로 절대 그렇게 하지 않는다.
        if (measured == null) return previous
        return (previous * 0.8) + (clamp01(measured) * 0.2)
    }

    private fun calculateFluencyScore(metrics: InternalMetrics): Double {
        // 유창성은 속도, 끊김, 발화 길이를 평균낸다.
        // pauseFrequency는 낮을수록 좋으므로 반전해서 사용한다.
        val pauseScore = 1.0 - metrics.pauseFrequency
        return ((metrics.speechRate + pauseScore + metrics.avgUtteranceLength) / 3.0)
            .coerceIn(0.0, 1.0)
    }

    private fun calculateNaturalnessScore(metrics: InternalMetrics): Double {
        // 자연스러움은 구어체와 표현 선택을 함께 본다.
        // spokenNaturalness와 naturalExpressionUsage가 같이 좋아져야 score도 오른다.
        return ((metrics.spokenNaturalness + metrics.naturalExpressionUsage) / 2.0)
            .coerceIn(0.0, 1.0)
    }

    private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    private fun LangStateUpdateInput.analyzableUserTurns(): List<ConversationTurn> {
        // AI turn은 문맥용이고, 능력 측정은 사용자 발화만 기준으로 삼는다.
        // 비어 있는 텍스트는 token 계산과 ratio 계산을 모두 망가뜨리므로 제외한다.
        return recentUserTurns.filter { turn ->
            turn.speaker == TurnSpeaker.USER && turn.text.isNotBlank()
        }
    }

    private fun LangStateUpdateInput.userWordTokens(): List<String> {
        // 언어별 형태소 분석기는 아직 없으므로, Unicode letter/number token만 공통 추출한다.
        // 이렇게 하면 영어/한국어/일본어 입력을 같은 규칙으로 최소한 평가할 수 있다.
        return analyzableUserTurns().flatMap { turn ->
            WORD_TOKEN_REGEX.findAll(turn.text.lowercase()).map { match -> match.value }.toList()
        }
    }

    private fun List<ConversationTurn>.averageTokenCount(): Double {
        // AI Chat에서 tokenCount를 주면 그 값을 우선하고, 없으면 텍스트 token 수로 fallback한다.
        // fallback이 없으면 tokenCount가 빠진 turn이 모두 사라지게 된다.
        val counts = map { turn ->
            turn.tokenCount?.takeIf { it > 0 }
                ?: WORD_TOKEN_REGEX.findAll(turn.text).count()
        }.filter { it > 0 }
        if (counts.isEmpty()) return 0.0

        return counts.average()
    }

    private fun String.tokenCount(): Int {
        // source/corrected 비교는 같은 tokenizer를 써야 길이 증가 판단이 일관된다.
        return WORD_TOKEN_REGEX.findAll(this).count()
    }

    private data class MetricDirectionUpdate(
        val key: LearningMetricKey,
        val direction: EvidenceDirection
    )

    private data class FocusUpdate(
        val type: LearningFocusType,
        val confidence: Double
    )

    private companion object {
        private const val AVG_UTTERANCE_TARGET_TOKENS = 14.0
        private const val SENTENCE_COMPLEXITY_TARGET_TOKENS = 18.0
        private const val STRUCTURE_HINTS_TARGET = 2.0
        private const val DEFAULT_SIGNAL_CONFIDENCE = 0.55
        private const val MIN_SCORE_SIGNAL_CONFIDENCE = 0.35
        private const val STRONG_SIGNAL_CONFIDENCE = 0.7
        private const val LOW_ABILITY_GUARD_THRESHOLD = 0.45
        private const val MIN_OVER_EXPANSION_TOKENS = 4
        private const val OVER_EXPANSION_RATIO = 0.75
        private const val MAX_SIGNAL_ITEMS = 3
        private const val MAX_ACTIVE_FOCUS_COUNT = 5

        private val WORD_TOKEN_REGEX = Regex("[\\p{L}\\p{N}']+")
        private val CLAUSE_PUNCTUATION_REGEX = Regex("[,;:]")
        private val CONNECTOR_REGEX = Regex(
            pattern = "\\b(and|but|because|when|while|if|although|though|that|which|who|where|so|however|therefore)\\b",
            option = RegexOption.IGNORE_CASE
        )
    }
}
