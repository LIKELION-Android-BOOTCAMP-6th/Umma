package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
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

private fun prepareNextState(input: LangStateUpdateInput): LangState {
    val current = input.currentState
    val now = input.analyzedAt

    // 이번 배치에서 추정 가능한 측정값만 뽑는다.
    val measuredGrammarAccuracy = measureGrammarAccuracy(input)
    val measuredSpeechRate = measureSpeechRate(input)
    val measuredReviewRetention = measureReviewRetention(input)
    val measuredNaturalness = measureNaturalness(input)

    // 기존 값에서 급격히 흔들리지 않도록 이동 평균을 적용한다.
    val nextInternal = current.internal.copy(
        grammarAccuracy = smoothMetric(current.internal.grammarAccuracy, measuredGrammarAccuracy),
        speechRate = smoothMetric(current.internal.speechRate, measuredSpeechRate),
        reviewRetention = smoothMetric(current.internal.reviewRetention, measuredReviewRetention),
        spokenNaturalness = smoothMetric(current.internal.spokenNaturalness, measuredNaturalness)
    )

    // 외부 노출용 점수는 내부 지표를 다시 묶어서 계산한다.
    val nextExternal = current.external.copy(
        grammarAccuracy = nextInternal.grammarAccuracy,
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

private fun measureSpeechRate(input: LangStateUpdateInput): Double? {
    // 발화 토큰 수와 지속 시간을 이용해 대략적인 속도를 만든다.
    val totalTokens = input.recentUserTurns.sumOf { it.tokenCount ?: 0 }
    val totalDurationMs = input.recentUserTurns.sumOf { it.durationMs ?: 0L }
    if (totalTokens <= 0 || totalDurationMs <= 0L) return null

    val tokensPerSecond = totalTokens.toDouble() / (totalDurationMs.toDouble() / 1_000.0)
    return clamp01(tokensPerSecond / 4.0)
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

private fun smoothMetric(previous: Double, measured: Double?): Double {
    // 측정값이 없으면 기존 값을 유지한다.
    if (measured == null) return previous
    return (previous * 0.8) + (measured * 0.2)
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
