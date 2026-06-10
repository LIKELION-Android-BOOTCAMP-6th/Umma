package com.app.umma.domain.usecase.statistics

import com.app.umma.domain.model.learningstate.currentLangState
import com.app.umma.domain.model.learningstate.selectedLang
import com.app.umma.domain.model.learningstate.toLangAbilityStats
import com.app.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.app.umma.domain.model.statistics.StatisticsMetricType
import com.app.umma.domain.model.statistics.StatisticsOverview
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Statistics 화면의 초기 진입 컨텍스트를 조립하는 얇은 UseCase다.
 *
 * 이 UseCase는 history 자체를 조회하지 않는다.
 * 대신 현재 선택 언어와 LangState.external, 그리고 history 조회 가능 여부만 준비한다.
 */
class GetStatisticsOverviewUseCase @Inject constructor(
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
    private val observeLearningStateUseCase: ObserveLearningStateUseCase,
    private val buildLearnerAdaptationProfileUseCase: BuildLearnerAdaptationProfileUseCase
) {
    suspend operator fun invoke(): Result<StatisticsOverview> = runCatching {
        // Statistics 화면은 로그인된 사용자 기준으로만 동작한다.
        val userId = getCurrentUserUidUseCase.getCurrentUserUid()
            ?: throw IllegalStateException("현재 로그인된 사용자를 찾을 수 없습니다.")

        // local cache를 먼저 읽고, 현재 선택 언어와 LangState를 한 번에 조립한다.
        val globalState = observeLearningStateUseCase().firstOrNull()
            ?: throw IllegalStateException("현재 학습 상태를 불러오지 못했습니다.")

        // 화면은 selected language 기준으로 시작해야 하므로,
        // userPref -> selectedLang -> 현재 LangState 순서로 한 번에 조립한다.
        val selectedLanguage = globalState.selectedLang
            ?: throw IllegalStateException("현재 선택된 학습 언어가 없습니다.")

        val currentLangState = globalState.currentLangState()
            ?: throw IllegalStateException("현재 선택 언어의 LangState가 없습니다.")
        val learnerProfile = buildLearnerAdaptationProfileUseCase(currentLangState)

        StatisticsOverview(
            userId = userId,
            selectedLearningLanguage = selectedLanguage,
            currentLangState = currentLangState,
            // 화면에서 바로 보여줄 수 있는 외부 지표만 미리 꺼내 둔다.
            currentExternalMetrics = currentLangState.external,
            // LangState 누적 bundle은 Statistics와 policy가 같은 source를 읽게 하기 위해 같이 준비한다.
            currentLangAbilityStats = currentLangState.toLangAbilityStats(learnerProfile),
            // 카드/차트 순서는 종합 레벨 -> 말하기 -> 문법 -> 이해력 -> 어휘 -> 표현력으로 고정한다.
            availableMetricTypes = statisticsMetricOrder(),
            historyQueryState = StatisticsHistoryQueryState.Ready(
                userId = userId,
                language = selectedLanguage
            )
        )
    }

    private fun statisticsMetricOrder(): List<StatisticsMetricType> {
        return listOf(
            StatisticsMetricType.ConversationBand,
            StatisticsMetricType.FluencyScore,
            StatisticsMetricType.GrammarAccuracy,
            StatisticsMetricType.NaturalnessScore,
            StatisticsMetricType.VocabularyLevel,
            StatisticsMetricType.ExpressionRange
        )
    }
}
