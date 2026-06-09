package com.app.umma.domain.usecase.statistics

import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.toLangAbilityStats
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import javax.inject.Inject

/**
 * LearningState 저장 완료 결과를 StatisticsHistory snapshot으로 바꾼다.
 *
 * Statistics 카드는 LangState의 통계용 능력 집계값을 보여주므로,
 * history도 같은 집계값을 저장해야 차트의 마지막 점이 카드의 현재값과 일치한다.
 */
class BuildStatisticsHistoryUseCase @Inject constructor(
    private val buildLearnerAdaptationProfileUseCase: BuildLearnerAdaptationProfileUseCase
) {

    operator fun invoke(
        userId: String,
        updateResult: LearningStateUpdateResult
    ): StatisticsHistory {
        val savedState = updateResult.savedState
        // correction flow 가 분석 이벤트 id 를 넘겨주면 그대로 쓰고,
        // 비어 있으면 저장 시각 기반의 fallback id 를 만들어 중복 판정을 유지한다.
        val sourceEventId = updateResult.sourceEventId.ifBlank {
            "${savedState.lang.code}:${updateResult.updatedAt}"
        }
        // Profile은 LangState를 같은 정책으로 해석하기 위한 중간 read model이다.
        // 카드와 history가 서로 다른 계산식을 쓰면 최신 차트 점과 카드 값이 달라지므로,
        // 여기서도 StatisticsOverview와 같은 profile -> LangAbilityStats 경로를 사용한다.
        val learnerProfile = buildLearnerAdaptationProfileUseCase(savedState)
        val abilityStats = savedState.toLangAbilityStats(learnerProfile)

        return StatisticsHistory(
            id = buildHistoryId(
                userId = userId,
                language = savedState.lang,
                sourceEventId = sourceEventId
            ),
            userId = userId,
            language = savedState.lang,
            recordedAt = updateResult.updatedAt,
            // 종합 레벨은 card와 동일하게 "근거가 있는 currentBand"만 저장한다.
            // 근거가 없으면 null로 남겨 초기/빈 분석을 실제 레벨처럼 차트에 찍지 않는다.
            conversationBand = abilityStats.conversation.currentBand,
            // 어휘/표현력은 아직 측정 준비 중이지만 기존 history schema 호환을 위해 외부 값을 보존한다.
            vocabularyLevel = savedState.external.vocabularyLevel,
            expressionRange = savedState.external.expressionRange,
            // 문법/말하기/이해력은 카드가 읽는 LangAbilityStats 값을 history에도 저장한다.
            // score가 null인 초기 상태는 기존 non-null schema를 유지하기 위해 external fallback을 사용하되,
            // UI의 availability gate가 해당 차트를 열지 않으므로 사용자에게 측정값처럼 노출되지 않는다.
            grammarAccuracy = abilityStats.grammar.score ?: savedState.external.grammarAccuracy,
            fluencyScore = abilityStats.speaking.score ?: savedState.external.fluencyScore,
            // legacy 필드명은 naturalnessScore지만, 새 Statistics 표시에서는 "이해력" 점수로 사용한다.
            naturalnessScore = abilityStats.comprehension.score ?: savedState.external.naturalnessScore,
            sourceEventId = sourceEventId,
            syncStatus = SyncStatus.PENDING
        )
    }

    private fun buildHistoryId(
        userId: String,
        language: LangCode,
        sourceEventId: String
    ): String {
        return "${userId}_${language.code}_$sourceEventId"
    }
}
