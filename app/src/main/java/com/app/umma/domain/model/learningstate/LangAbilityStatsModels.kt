package com.app.umma.domain.model.learningstate

import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence

/**
 * LangState가 누적한 대화 능력 요약 묶음.
 *
 * Statistics와 prompt 정책은 raw Chat/Correction evidence를 다시 해석하지 않고,
 * 이 bundle만 읽어도 현재 능력 상태를 안전하게 읽을 수 있어야 한다.
 */
data class LangAbilityStats(
    // 대화 band와 그 신뢰도.
    val conversation: ConversationBandStats,
    // 발화 속도/끊김/흐름을 합쳐 본 말하기 흐름.
    val speaking: SpeakingFlowStats,
    // 교정 신호를 누적한 문법 지표.
    val grammar: GrammarAbilityStats,
    // 학습언어 입력을 이해하고 반응하는 정도.
    val comprehension: ComprehensionStats,
    // 아직 신뢰도 있게 수치화하지 않는 지표는 준비 상태만 유지한다.
    val vocabulary: AbilityReadinessState = AbilityReadinessState.Preparing,
    // 표현력도 아직 준비 상태로 둔다.
    val expression: AbilityReadinessState = AbilityReadinessState.Preparing
) {
    companion object {
        fun initial(): LangAbilityStats {
            return LangAbilityStats(
                conversation = ConversationBandStats.initial(),
                speaking = SpeakingFlowStats.initial(),
                grammar = GrammarAbilityStats.initial(),
                comprehension = ComprehensionStats.initial(),
                vocabulary = AbilityReadinessState.Preparing,
                expression = AbilityReadinessState.Preparing
            )
        }
    }
}

/**
 * 현재 conversation band와 관측 신뢰도.
 */
data class ConversationBandStats(
    val currentBand: ConversationAbilityBand?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
) {
    companion object {
        fun initial(): ConversationBandStats {
            return ConversationBandStats(
                currentBand = null,
                confidence = ProfileConfidence.Low,
                observedCount = 0,
                lastObservedAt = null
            )
        }
    }
}

/**
 * 말하기 흐름 점수.
 */
data class SpeakingFlowStats(
    val score: Double?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
) {
    companion object {
        fun initial(): SpeakingFlowStats {
            return SpeakingFlowStats(
                score = null,
                confidence = ProfileConfidence.Low,
                observedCount = 0,
                lastObservedAt = null
            )
        }
    }
}

/**
 * 문법 능력 요약.
 */
data class GrammarAbilityStats(
    val score: Double?,
    val weightedErrorDensity: Double?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
) {
    companion object {
        fun initial(): GrammarAbilityStats {
            return GrammarAbilityStats(
                score = null,
                weightedErrorDensity = null,
                confidence = ProfileConfidence.Low,
                observedCount = 0,
                lastObservedAt = null
            )
        }
    }
}

/**
 * 이해력 요약.
 */
data class ComprehensionStats(
    val score: Double?,
    val confidence: ProfileConfidence,
    val observedCount: Int,
    val lastObservedAt: Long?
) {
    companion object {
        fun initial(): ComprehensionStats {
            return ComprehensionStats(
                score = null,
                confidence = ProfileConfidence.Low,
                observedCount = 0,
                lastObservedAt = null
            )
        }
    }
}

/**
 * 아직 수치화하지 않는 지표의 준비 상태.
 */
enum class AbilityReadinessState {
    Ready,
    Preparing
}

/**
 * LearnerAdaptationProfile와 LangState를 통계용 bundle로 압축한다.
 *
 * 이 bundle은 저장용 DTO가 아니라 read model이므로,
 * Statistics와 profile policy가 같은 source를 보게 하는 데 목적이 있다.
 */
fun LangState.toLangAbilityStats(profile: LearnerAdaptationProfile): LangAbilityStats {
    val chatSummary = analysisMeta.chatEvidenceSummary
    val conversationObservedCount = chatSummary?.observedCount ?: 0
    val conversationLastObservedAt = chatSummary?.lastObservedAt
    val grammarEvidenceCount = analysisMeta.metricEvidence[LearningMetricKey.GrammarAccuracy]?.observedCount ?: 0
    val grammarLastObservedAt = analysisMeta.metricEvidence[LearningMetricKey.GrammarAccuracy]?.lastObservedAt
    val hasConversationEvidence = conversationObservedCount > 0
    val hasGrammarEvidence = grammarEvidenceCount > 0
    val speakingScore = if (hasConversationEvidence) {
        listOf(
            internal.speechRate,
            (1.0 - internal.pauseFrequency).coerceIn(0.0, 1.0),
            internal.spokenNaturalness.coerceIn(0.0, 1.0),
            internal.naturalExpressionUsage.coerceIn(0.0, 1.0)
        ).average().coerceIn(0.0, 1.0)
    } else {
        null
    }
    val comprehensionScore = chatSummary?.targetLanguageComprehension?.toScore()
        ?.takeIf { hasConversationEvidence }
    val grammarScore = if (hasGrammarEvidence) {
        internal.grammarAccuracy.coerceIn(0.0, 1.0)
    } else {
        null
    }

    return LangAbilityStats(
        conversation = ConversationBandStats(
            currentBand = if (hasConversationEvidence) profile.chatPolicy.conversationBand else null,
            confidence = if (hasConversationEvidence) profile.core.levelConfidence else ProfileConfidence.Low,
            observedCount = conversationObservedCount,
            lastObservedAt = conversationLastObservedAt
        ),
        speaking = SpeakingFlowStats(
            score = speakingScore,
            confidence = if (hasConversationEvidence) profile.core.fluencyStage.toProfileConfidence() else ProfileConfidence.Low,
            observedCount = conversationObservedCount,
            lastObservedAt = conversationLastObservedAt
        ),
        grammar = GrammarAbilityStats(
            score = grammarScore,
            weightedErrorDensity = grammarScore?.let { 1.0 - it },
            confidence = if (hasGrammarEvidence) profile.core.grammarStage.toProfileConfidence() else ProfileConfidence.Low,
            observedCount = grammarEvidenceCount,
            lastObservedAt = grammarLastObservedAt
        ),
        comprehension = ComprehensionStats(
            score = comprehensionScore,
            confidence = if (hasConversationEvidence) {
                chatSummary?.confidence ?: profile.core.levelConfidence
            } else {
                ProfileConfidence.Low
            },
            observedCount = conversationObservedCount,
            lastObservedAt = conversationLastObservedAt
        ),
        vocabulary = AbilityReadinessState.Preparing,
        expression = AbilityReadinessState.Preparing
    )
}

private fun TargetLanguageComprehensionEvidence.toScore(): Double {
    return when (this) {
        TargetLanguageComprehensionEvidence.None -> 0.0
        TargetLanguageComprehensionEvidence.WordLevel -> 0.33
        TargetLanguageComprehensionEvidence.SimpleSentence -> 0.66
        TargetLanguageComprehensionEvidence.NaturalFlow -> 1.0
    }
}

private fun SkillStage.toProfileConfidence(): ProfileConfidence {
    return when (this) {
        SkillStage.Foundation -> ProfileConfidence.Low
        SkillStage.Developing -> ProfileConfidence.Low
        SkillStage.Stable -> ProfileConfidence.Medium
        SkillStage.Expanding -> ProfileConfidence.Medium
        SkillStage.Refined -> ProfileConfidence.High
    }
}
