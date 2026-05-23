package com.example.umma.domain.usecase.statistics

import com.example.umma.domain.model.learningstate.LearningStateUpdateResult
import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.learningstate.LangCode
import javax.inject.Inject

/**
 * LearningState 저장 완료 결과를 StatisticsHistory snapshot으로 바꾼다.
 *
 * LS-006이 계산한 외부 점수와 sourceEventId만 이어받고,
 * Statistics 쪽은 history 식별자와 저장 스냅샷 의미만 책임진다.
 */
class BuildStatisticsHistoryUseCase @Inject constructor() {

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

        return StatisticsHistory(
            id = buildHistoryId(
                userId = userId,
                language = savedState.lang,
                sourceEventId = sourceEventId
            ),
            userId = userId,
            language = savedState.lang,
            recordedAt = updateResult.updatedAt,
            vocabularyLevel = savedState.external.vocabularyLevel,
            grammarAccuracy = savedState.external.grammarAccuracy,
            expressionRange = savedState.external.expressionRange,
            fluencyScore = savedState.external.fluencyScore,
            naturalnessScore = savedState.external.naturalnessScore,
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
