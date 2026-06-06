package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.MetricEvidence

/**
 * source별 learning evidence를 같은 방식으로 누적하는 작은 정책.
 *
 * Correction과 Chat이 같은 [MetricEvidence] 저장 모델을 공유하므로, merge 규칙은 한 곳에 둔다.
 * source만 caller가 넘기게 해야 Chat 근거가 Correction 근거로 잘못 저장되지 않는다.
 */
object MetricEvidenceMergePolicy {
    fun merge(
        previous: MetricEvidence?,
        direction: EvidenceDirection,
        confidence: Double,
        source: LearningSignalSource,
        observedAt: Long
    ): MetricEvidence {
        if (previous == null) {
            // 첫 관찰은 그대로 저장한다. sourceTypes를 Set으로 시작해 이후 Correction/Chat 근거를 분리해 추적한다.
            return MetricEvidence(
                observedCount = 1,
                confidence = confidence.coerceIn(0.0, 1.0),
                sourceTypes = setOf(source),
                direction = direction,
                directionCount = 1,
                lastObservedAt = observedAt
            )
        }

        // 방향은 보수적으로 합친다.
        // Chat과 Correction이 서로 다른 방향을 주면 한쪽으로 덮지 않고 Mixed로 남겨 profile builder가 신중하게 해석하게 한다.
        val mergedDirection = when {
            previous.direction == direction -> direction
            previous.direction == EvidenceDirection.Mixed -> EvidenceDirection.Mixed
            direction == EvidenceDirection.Stable -> previous.direction
            previous.direction == EvidenceDirection.Stable -> direction
            else -> EvidenceDirection.Mixed
        }
        // directionCount는 같은 방향이 연속으로 반복된 강도를 보기 위한 값이다.
        // 방향이 바뀌면 새 방향의 연속 count를 다시 1부터 시작한다.
        val directionCount = if (previous.direction == direction) previous.directionCount + 1 else 1
        val observedCount = previous.observedCount + 1
        // confidence는 최근 값으로 덮지 않고 관찰 수 기반 평균으로 완만하게 이동시킨다.
        // 그래야 한 번의 과대/과소 평가가 누적 evidence 전체를 흔들지 않는다.
        val mergedConfidence = ((previous.confidence * previous.observedCount) + confidence) / observedCount

        return previous.copy(
            observedCount = observedCount,
            confidence = mergedConfidence.coerceIn(0.0, 1.0),
            sourceTypes = previous.sourceTypes + source,
            direction = mergedDirection,
            directionCount = directionCount,
            lastObservedAt = observedAt
        )
    }
}
