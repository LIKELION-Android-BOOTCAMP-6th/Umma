package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
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
 * 기존 prepareNextState 로직을 그대로 옮긴 기본 LangState 분석 정책.
 *
 * CHAT-TUNE-001-B 1차 범위는 정책 분리 후 결과 보존이다. 그래서 evidence/focus나
 * difficulty guard는 아직 적용하지 않고, 기존 MVP 휴리스틱 계산만 유지한다.
 */
class DefaultLangStateAnalysisPolicy @Inject constructor() : LangStateAnalysisPolicy {
    override fun analyze(input: LangStateUpdateInput): LangState {
        // 계산은 현재 저장된 snapshot을 기준으로만 시작한다.
        // 여기서 별도 DB 조회를 하면 policy가 저장소 의존성을 다시 가져버린다.
        val current = input.currentState
        // update 시점은 저장 결과와 분석 결과가 같은 timestamp를 보게 맞춘다.
        val now = input.analyzedAt

        // 이번 batch에서 추정 가능한 측정값만 뽑는다.
        // null인 측정값은 이후 smoothMetric에서 기존 상태를 유지하게 만든다.
        // 각 helper는 "이번 입력에서 이 지표를 읽을 수 있는가"를 먼저 판단한다.
        val measuredGrammarAccuracy = measureGrammarAccuracy(input)
        val measuredVocabularyAppropriateness = measureVocabularyAppropriateness(input)
        val measuredLexicalDiversity = measureLexicalDiversity(input)
        val measuredSentenceComplexity = measureSentenceComplexity(input)
        val measuredSpeechRate = measureSpeechRate(input)
        val measuredAvgUtteranceLength = measureAvgUtteranceLength(input)
        val measuredReviewRetention = measureReviewRetention(input)
        val measuredNaturalness = measureNaturalness(input)
        val measuredNaturalExpressionUsage = measureNaturalExpressionUsage(
            input = input,
            measuredNaturalness = measuredNaturalness,
            measuredVocabularyAppropriateness = measuredVocabularyAppropriateness
        )
        val measuredErrorRecurrence = measureErrorRecurrence(input)
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

        return current.copy(
            internal = nextInternal,
            external = nextExternal,
            updatedAt = now,
            lastAnalyzedAt = now,
            // 같은 분석 이벤트가 다시 오더라도 상위 use case가 먼저 걸러내고,
            // 여기서는 계산 결과에 event id를 다시 실어 저장 경계를 맞춘다.
            lastAnalysisEventId = input.analysisEventId ?: current.lastAnalysisEventId
        )
    }

    private fun measureGrammarAccuracy(input: LangStateUpdateInput): Double? {
        // 교정 수가 많을수록 정확도는 낮다고 본다.
        // userTurns 수가 0이면 비교 기준이 없으므로 null을 돌려 기존 값을 유지하게 한다.
        val userTurns = input.recentUserTurns.count { it.speaker == TurnSpeaker.USER }
        if (userTurns <= 0) return null

        // correctionCount는 실제 교정 밀도의 대용값이므로, 현재는 단순 비율로만 본다.
        val correctionCount = input.correctionResult?.correctionCount ?: 0
        val rawScore = 1.0 - (correctionCount.toDouble() / userTurns.toDouble())
        return clamp01(rawScore)
    }

    private fun measureVocabularyAppropriateness(input: LangStateUpdateInput): Double? {
        // 현재 별도 Type C AI 점수가 없으므로, 어휘 다양성과 교정 밀도를 함께 본다.
        // 교정이 적고 표현 폭이 넓을수록 문맥에 맞는 어휘 선택으로 간주하는 MVP 근사치다.
        val lexicalDiversity = measureLexicalDiversity(input) ?: return null
        // userTurns가 비어 있으면 correctionPenalty를 계산해도 의미가 없으므로 중단한다.
        val userTurns = input.analyzableUserTurns()
        if (userTurns.isEmpty()) return null

        // 교정이 많을수록 문맥 적합성이 낮았다고 보는 보수적 근사치다.
        val correctionCount = input.correctionResult?.correctionCount ?: 0
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

    private fun measureNaturalness(input: LangStateUpdateInput): Double? {
        // 교정이 적을수록 자연스럽다고 간주하는 MVP용 근사치다.
        // userTurns가 없으면 naturalness도 측정할 수 없으므로 null을 반환한다.
        val userTurns = input.recentUserTurns.count { it.speaker == TurnSpeaker.USER }
        if (userTurns <= 0) return null

        // correctionCount가 많을수록 자연스러움이 낮다고 보는 단순 근사치다.
        val correctionCount = input.correctionResult?.correctionCount ?: 0
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

    private fun measureErrorRecurrence(input: LangStateUpdateInput): Double? {
        // 진짜 "반복 오류"는 이전 correction signature와 현재 오류를 비교해야 한다.
        // 현재 모델에는 signature 저장소가 없으므로, correction density를 낮은 신뢰도의 proxy로만 저장한다.
        // future signal이 들어오면 이 helper를 대체하면 된다.
        val userTurns = input.analyzableUserTurns()
        if (userTurns.isEmpty() || input.correctionResult == null) return null

        val correctionCount = input.correctionResult.correctionCount
        return clamp01(correctionCount.toDouble() / (userTurns.size.toDouble() * 2.0))
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

    private companion object {
        private const val AVG_UTTERANCE_TARGET_TOKENS = 14.0
        private const val SENTENCE_COMPLEXITY_TARGET_TOKENS = 18.0
        private const val STRUCTURE_HINTS_TARGET = 2.0

        private val WORD_TOKEN_REGEX = Regex("[\\p{L}\\p{N}']+")
        private val CLAUSE_PUNCTUATION_REGEX = Regex("[,;:]")
        private val CONNECTOR_REGEX = Regex(
            pattern = "\\b(and|but|because|when|while|if|although|though|that|which|who|where|so|however|therefore)\\b",
            option = RegexOption.IGNORE_CASE
        )
    }
}
