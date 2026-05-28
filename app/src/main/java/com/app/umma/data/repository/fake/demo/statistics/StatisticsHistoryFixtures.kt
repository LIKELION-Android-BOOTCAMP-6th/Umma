package com.app.umma.data.repository.fake.demo.statistics

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.statistics.StatisticsHistory

/**
 * StatisticsRepository fake가 사용할 history fixture다.
 *
 * history seed는 통계 차트/동기화 데모 전용 데이터이므로 repository 본문이 아니라
 * 통계 도메인 fixture 파일에서 관리한다.
 */
object StatisticsHistoryFixtures {

    fun normalHistories(): List<StatisticsHistory> {
        return listOf(
            history("stats-en-1", "user-1", LangCode.EN, JAN_01_2025_KST, VocabLevel.A1, 0.41, 2, 0.33, 0.37, "event-en-1"),
            history("stats-en-2", "user-1", LangCode.EN, JAN_07_2025_KST, VocabLevel.A2, 0.53, 3, 0.46, 0.48, "event-en-2", SyncStatus.PENDING),
            history("stats-en-3", "user-1", LangCode.EN, JAN_14_2025_KST, VocabLevel.B1, 0.64, 5, 0.58, 0.57, "event-en-3"),
            history("stats-en-4", "user-1", LangCode.EN, JAN_21_2025_KST, VocabLevel.B2, 0.71, 6, 0.65, 0.63, "event-en-4", SyncStatus.PENDING),
            history("stats-en-5", "user-1", LangCode.EN, JAN_28_2025_KST, VocabLevel.C1, 0.88, 8, 0.81, 0.78, "event-en-5"),
            history("stats-en-6", "user-1", LangCode.EN, FEB_04_2025_KST, VocabLevel.C1, 0.89, 8, 0.83, 0.80, "event-en-6"),
            history("stats-en-7", "user-1", LangCode.EN, FEB_11_2025_KST, VocabLevel.C1, 0.90, 9, 0.84, 0.82, "event-en-7", SyncStatus.PENDING),
            history("stats-en-8", "user-1", LangCode.EN, FEB_18_2025_KST, VocabLevel.C1, 0.91, 9, 0.86, 0.84, "event-en-8"),
            history("stats-en-9", "user-1", LangCode.EN, FEB_25_2025_KST, VocabLevel.C2, 0.92, 9, 0.88, 0.86, "event-en-9", SyncStatus.PENDING),
            history("stats-en-10", "user-1", LangCode.EN, MAR_04_2025_KST, VocabLevel.C1, 0.88, 8, 0.81, 0.78, "event-en-10"),
            history("stats-ko-1", "user-1", LangCode.KO, JAN_03_2025_KST, VocabLevel.A1, 0.44, 3, 0.39, 0.41, "event-ko-1"),
            history("stats-ko-2", "user-1", LangCode.KO, JAN_10_2025_KST, VocabLevel.A2, 0.58, 4, 0.52, 0.49, "event-ko-2"),
            history("stats-ko-3", "user-1", LangCode.KO, JAN_17_2025_KST, VocabLevel.A2, 0.64, 5, 0.57, 0.55, "event-ko-3", SyncStatus.PENDING),
            history("stats-ja-1", "user-1", LangCode.JA, JAN_05_2025_KST, VocabLevel.B1, 0.66, 5, 0.57, 0.59, "event-ja-1"),
            history("stats-ja-2", "user-1", LangCode.JA, JAN_12_2025_KST, VocabLevel.B1, 0.70, 5, 0.61, 0.64, "event-ja-2"),
            history("stats-es-1", "user-1", LangCode.ES, JAN_08_2025_KST, VocabLevel.A2, 0.51, 4, 0.46, 0.43, "event-es-1", SyncStatus.PENDING),
            history("stats-es-2", "user-1", LangCode.ES, JAN_15_2025_KST, VocabLevel.B1, 0.60, 5, 0.52, 0.50, "event-es-2"),
            history("stats-user-2-ja", "user-2", LangCode.JA, FEB_01_2025_KST, VocabLevel.A1, 0.30, 2, 0.25, 0.20, "event-user2-ja")
        )
    }

    fun expressionRangeOverflowHistories(): List<StatisticsHistory> {
        // 표현 폭 차트의 y축 상한 확장을 확인하기 위해 EN 데이터만 10을 넘도록 만든다.
        return normalHistories().mapIndexed { index, history ->
            if (history.language == LangCode.EN) {
                history.copy(expressionRange = 4 + index)
            } else {
                history
            }
        }
    }

    fun pendingHistories(): List<StatisticsHistory> {
        // pending sync 실패 후 local row가 유지되는지 확인할 수 있도록 EN 데이터를 모두 PENDING으로 둔다.
        return normalHistories()
            .filter { it.userId == "user-1" && it.language == LangCode.EN }
            .map { it.copy(syncStatus = SyncStatus.PENDING) }
    }

    private fun history(
        id: String,
        userId: String,
        language: LangCode,
        recordedAt: Long,
        vocabularyLevel: VocabLevel,
        grammarAccuracy: Double,
        expressionRange: Int,
        fluencyScore: Double,
        naturalnessScore: Double,
        sourceEventId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ): StatisticsHistory {
        return StatisticsHistory(
            id = id,
            userId = userId,
            language = language,
            recordedAt = recordedAt,
            vocabularyLevel = vocabularyLevel,
            grammarAccuracy = grammarAccuracy,
            expressionRange = expressionRange,
            fluencyScore = fluencyScore,
            naturalnessScore = naturalnessScore,
            sourceEventId = sourceEventId,
            syncStatus = syncStatus
        )
    }

    private const val DAY = 24 * 60 * 60 * 1_000L
    private const val JAN_01_2025_KST = 1_735_657_200_000L
    private const val JAN_03_2025_KST = JAN_01_2025_KST + 2 * DAY
    private const val JAN_05_2025_KST = JAN_01_2025_KST + 4 * DAY
    private const val JAN_07_2025_KST = JAN_01_2025_KST + 6 * DAY
    private const val JAN_08_2025_KST = JAN_01_2025_KST + 7 * DAY
    private const val JAN_10_2025_KST = JAN_01_2025_KST + 9 * DAY
    private const val JAN_12_2025_KST = JAN_01_2025_KST + 11 * DAY
    private const val JAN_14_2025_KST = JAN_01_2025_KST + 13 * DAY
    private const val JAN_15_2025_KST = JAN_01_2025_KST + 14 * DAY
    private const val JAN_17_2025_KST = JAN_01_2025_KST + 16 * DAY
    private const val JAN_21_2025_KST = JAN_01_2025_KST + 20 * DAY
    private const val JAN_28_2025_KST = JAN_01_2025_KST + 27 * DAY
    private const val FEB_01_2025_KST = JAN_01_2025_KST + 31 * DAY
    private const val FEB_04_2025_KST = JAN_01_2025_KST + 34 * DAY
    private const val FEB_11_2025_KST = JAN_01_2025_KST + 41 * DAY
    private const val FEB_18_2025_KST = JAN_01_2025_KST + 48 * DAY
    private const val FEB_25_2025_KST = JAN_01_2025_KST + 55 * DAY
    private const val MAR_04_2025_KST = JAN_01_2025_KST + 62 * DAY
}
