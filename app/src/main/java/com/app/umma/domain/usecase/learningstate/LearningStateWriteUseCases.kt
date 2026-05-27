package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.repository.LearningStateRepo
import javax.inject.Inject

/**
 * 학습 상태를 바꾸는 UseCase 묶음.
 */
class ChangeSelectedLangUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    // 선택 언어만 바꾸고 나머지 상태는 유지한다.
    suspend operator fun invoke(lang: LangCode): Result<Unit> = repo.changeSelectedLang(lang)
}

class ApplyLanguageStateUpdateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(input: LangStateUpdateInput): Result<LearningStateUpdateResult> {
        // 같은 analysisEventId를 다시 받으면 이동평균을 한 번 더 적용하지 않는다.
        // 이 early return 덕분에 Correction 완료 재시도나 화면 재진입이 점수를 왜곡하지 않는다.
        if (!input.forceReanalysis &&
            input.analysisEventId != null &&
            input.currentState.lastAnalysisEventId == input.analysisEventId
        ) {
            return Result.success(
                LearningStateUpdateResult(
                    lang = input.lang,
                    savedState = input.currentState,
                    sourceEventId = input.analysisEventId,
                    applied = false,
                    updatedAt = input.currentState.updatedAt ?: input.analyzedAt
                )
            )
        }

        // UseCase가 다음 상태를 먼저 계산하고 저장소는 그 결과만 저장한다.
        val preparedState = input.preparedState ?: prepareNextState(input)
        return repo.updateLanguageState(input.copy(preparedState = preparedState))
    }
}

class ApplyFlashcardSummaryUpdateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(
        input: FlashcardSummaryUpdateInput
    ): Result<FlashcardSummaryUpdateResult> {
        // due count 계산은 SRS가 끝낸 상태로 넘어온다.
        // LS는 음수처럼 화면을 깨는 값만 막고, 전역 Summary 반영을 담당한다.
        if (input.dueFlashcards < 0 || input.savedFlashcards < 0) {
            return Result.failure(
                IllegalArgumentException("flashcard summary counts must not be negative")
            )
        }
        return repo.updateFlashcardSummary(input)
    }
}

class ApplyCorrectionSignalUpdateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    /**
     * Chat final turn 이후 correctionAvailable 만 빠르게 갱신한다.
     *
     * full LangState 분석 batch 를 다시 돌리지 않고, 세션/대시보드 요약만
     * 같은 의미로 맞춰두는 경계다.
     *
     * 이 UseCase는 "신호를 받을 자격이 있는 입력인가"만 먼저 확인하고,
     * 실제 summary 반영은 repository 계약으로 넘긴다.
     */
    suspend operator fun invoke(
        input: CorrectionSignalUpdateInput
    ): Result<CorrectionSignalUpdateResult> {
        // uid / scope / event key 가 비어 있으면 idempotent 판단 자체가 불가능하다.
        if (input.uid.isBlank()) {
            return Result.failure(IllegalArgumentException("uid must not be blank"))
        }
        if (input.sessionMemoryKey.isBlank()) {
            return Result.failure(IllegalArgumentException("sessionMemoryKey must not be blank"))
        }
        if (input.sourceEventId.isBlank()) {
            return Result.failure(IllegalArgumentException("sourceEventId must not be blank"))
        }
        if (input.recentMinutes != null && input.recentMinutes < 0) {
            return Result.failure(IllegalArgumentException("recentMinutes must not be negative"))
        }

        return repo.updateCorrectionSignal(input)
    }
}

class InitLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    suspend operator fun invoke(
        userUid: String,
        userPref: UserLangPref,
        langState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit> {
        // 신규 사용자 최초 진입 시 기본 스냅샷을 만든다.
        return repo.createInitial(
            userUid = userUid,
            userPref = userPref,
            langState = langState,
            dashSummary = dashSummary,
            sessionSummary = sessionSummary,
            flashcardSummary = flashcardSummary
        )
    }
}

class ClearLearningStateUseCase @Inject constructor(
    private val repo: LearningStateRepo
) {
    // 로그아웃 시 전역 상태를 비운다.
    suspend operator fun invoke(): Result<Unit> = repo.clear()
}

/**
 * LangState의 다음 snapshot을 계산한다.
 *
 * Repository는 저장만 담당해야 하므로, 교정 결과와 세션 turn에서 유도할 수 있는
 * 학습 지표 계산은 이 domain UseCase 경계에 둔다. 여기의 계산은 AI 정밀 분석이
 * 붙기 전까지 사용할 MVP 휴리스틱이며, 값이 없으면 기존 상태를 유지한다.
 */
private fun prepareNextState(input: LangStateUpdateInput): LangState {
    val current = input.currentState
    val now = input.analyzedAt

    // 이번 배치에서 추정 가능한 측정값만 뽑는다.
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
        lastAnalysisEventId = input.analysisEventId ?: current.lastAnalysisEventId
    )
}

private fun measureGrammarAccuracy(input: LangStateUpdateInput): Double? {
    // 교정 수가 많을수록 정확도는 낮다고 본다.
    val userTurns = input.recentUserTurns.count { it.speaker == TurnSpeaker.USER }
    if (userTurns <= 0) return null

    val correctionCount = input.correctionResult?.correctionCount ?: 0
    val rawScore = 1.0 - (correctionCount.toDouble() / userTurns.toDouble())
    return clamp01(rawScore)
}

private fun measureVocabularyAppropriateness(input: LangStateUpdateInput): Double? {
    // 현재 별도 Type C AI 점수가 없으므로, 어휘 다양성과 교정 밀도를 함께 본다.
    // 교정이 적고 표현 폭이 넓을수록 문맥에 맞는 어휘 선택으로 간주하는 MVP 근사치다.
    val lexicalDiversity = measureLexicalDiversity(input) ?: return null
    val userTurns = input.analyzableUserTurns()
    if (userTurns.isEmpty()) return null

    val correctionCount = input.correctionResult?.correctionCount ?: 0
    val correctionPenalty = correctionCount.toDouble() / (userTurns.size.toDouble() * 2.0)
    val correctionScore = 1.0 - correctionPenalty
    return clamp01((lexicalDiversity * 0.6) + (correctionScore * 0.4))
}

private fun measureLexicalDiversity(input: LangStateUpdateInput): Double? {
    // lexical diversity는 "고유 token 수 / 전체 token 수"로 계산한다.
    // SessionMemory에 정교한 형태소 정보가 없기 때문에 텍스트 tokenization은 domain 내부의 단순 규칙으로 제한한다.
    val tokens = input.userWordTokens()
    if (tokens.isEmpty()) return null

    return clamp01(tokens.toSet().size.toDouble() / tokens.size.toDouble())
}

private fun measureSentenceComplexity(input: LangStateUpdateInput): Double? {
    // 복잡도는 긴 발화와 접속/절 단서를 함께 본다.
    // 문법 파서가 아직 없으므로, 데모/초기 real 데이터에서 0으로 고정되지 않게 만드는 안정적인 근사치다.
    val userTurns = input.analyzableUserTurns()
    if (userTurns.isEmpty()) return null

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
    val totalTokens = input.recentUserTurns.sumOf { it.tokenCount ?: 0 }
    val totalDurationMs = input.recentUserTurns.sumOf { it.durationMs ?: 0L }
    if (totalTokens <= 0 || totalDurationMs <= 0L) return null

    val tokensPerSecond = totalTokens.toDouble() / (totalDurationMs.toDouble() / 1_000.0)
    return clamp01(tokensPerSecond / 4.0)
}

private fun measureAvgUtteranceLength(input: LangStateUpdateInput): Double? {
    // 내부 지표는 raw token count가 아니라 0~1 점수로 저장한다.
    // 평균 14 token 정도를 안정적인 발화 길이 기준으로 보고 normalize한다.
    val userTurns = input.analyzableUserTurns()
    if (userTurns.isEmpty()) return null

    return clamp01(userTurns.averageTokenCount() / AVG_UTTERANCE_TARGET_TOKENS)
}

private fun measureReviewRetention(input: LangStateUpdateInput): Double? {
    // 복습 정답 비율을 그대로 retention 후보로 쓴다.
    val reviews = input.flashcardReviewEvents
    if (reviews.isEmpty()) return null

    val correctRate = reviews.count { it.isCorrect }.toDouble() / reviews.size.toDouble()
    return clamp01(correctRate)
}

private fun measureNaturalness(input: LangStateUpdateInput): Double? {
    // 교정이 적을수록 자연스럽다고 간주하는 MVP용 근사치다.
    val userTurns = input.recentUserTurns.count { it.speaker == TurnSpeaker.USER }
    if (userTurns <= 0) return null

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

    val naturalness = measuredNaturalness ?: return measuredVocabularyAppropriateness
    val vocabularyAppropriateness = measuredVocabularyAppropriateness ?: return naturalness
    return clamp01((naturalness * 0.7) + (vocabularyAppropriateness * 0.3))
}

private fun measureErrorRecurrence(input: LangStateUpdateInput): Double? {
    // 진짜 "반복 오류"는 이전 correction signature와 현재 오류를 비교해야 한다.
    // 현재 모델에는 signature 저장소가 없으므로, correction density를 낮은 신뢰도의 proxy로만 저장한다.
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
    val delta = measured.ordinal - previous.ordinal
    return when {
        delta > 0 -> VocabLevel.entries[previous.ordinal + 1]
        delta < 0 -> VocabLevel.entries[previous.ordinal - 1]
        else -> previous
    }
}

private fun smoothMetric(previous: Double, measured: Double?): Double {
    // 측정값이 없으면 기존 값을 유지한다.
    if (measured == null) return previous
    return (previous * 0.8) + (clamp01(measured) * 0.2)
}

private fun calculateFluencyScore(metrics: InternalMetrics): Double {
    // 유창성은 속도, 끊김, 발화 길이를 평균낸다.
    val pauseScore = 1.0 - metrics.pauseFrequency
    return ((metrics.speechRate + pauseScore + metrics.avgUtteranceLength) / 3.0)
        .coerceIn(0.0, 1.0)
}

private fun calculateNaturalnessScore(metrics: InternalMetrics): Double {
    // 자연스러움은 구어체와 표현 선택을 함께 본다.
    return ((metrics.spokenNaturalness + metrics.naturalExpressionUsage) / 2.0)
        .coerceIn(0.0, 1.0)
}

private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

private fun LangStateUpdateInput.analyzableUserTurns(): List<ConversationTurn> {
    // AI turn은 문맥용이고, 능력 측정은 사용자 발화만 기준으로 삼는다.
    return recentUserTurns.filter { turn ->
        turn.speaker == TurnSpeaker.USER && turn.text.isNotBlank()
    }
}

private fun LangStateUpdateInput.userWordTokens(): List<String> {
    // 언어별 형태소 분석기는 아직 없으므로, Unicode letter/number token만 공통 추출한다.
    return analyzableUserTurns().flatMap { turn ->
        WORD_TOKEN_REGEX.findAll(turn.text.lowercase()).map { match -> match.value }.toList()
    }
}

private fun List<ConversationTurn>.averageTokenCount(): Double {
    // AI Chat에서 tokenCount를 주면 그 값을 우선하고, 없으면 텍스트 token 수로 fallback한다.
    val counts = map { turn ->
        turn.tokenCount?.takeIf { it > 0 }
            ?: WORD_TOKEN_REGEX.findAll(turn.text).count()
    }.filter { it > 0 }
    if (counts.isEmpty()) return 0.0

    return counts.average()
}

private const val AVG_UTTERANCE_TARGET_TOKENS = 14.0
private const val SENTENCE_COMPLEXITY_TARGET_TOKENS = 18.0
private const val STRUCTURE_HINTS_TARGET = 2.0

private val WORD_TOKEN_REGEX = Regex("[\\p{L}\\p{N}']+")
private val CLAUSE_PUNCTUATION_REGEX = Regex("[,;:]")
private val CONNECTOR_REGEX = Regex(
    pattern = "\\b(and|but|because|when|while|if|although|though|that|which|who|where|so|however|therefore)\\b",
    option = RegexOption.IGNORE_CASE
)
