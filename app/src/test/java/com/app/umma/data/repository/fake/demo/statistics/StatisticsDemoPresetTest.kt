package com.app.umma.data.repository.fake.demo.statistics

import com.app.umma.data.repository.fake.FakeLearningStateRepo
import com.app.umma.data.repository.fake.FakeStatisticsRepository
import com.app.umma.data.repository.fake.demo.DemoLearningStateProvider
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.statistics.StatisticsHistoryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEMO_FLOW_STATISTICS.md에 적힌 Statistics preset 준비 상태를 문서 순서대로 검증한다.
 *
 * FakeStatisticsRepositoryTest는 저장소 계약 회귀 테스트만 담당하고,
 * 이 파일은 mockDebug 데모 시나리오에서 선택할 preset seed/failure/delay 구성을 담당한다.
 */
class StatisticsDemoPresetTest {

    @Test
    fun `01 NormalStatistics provides default overview state and chart histories`() = runBlocking {
        // 기본 진입 시나리오는 LearningState overview와 Statistics history가 모두 준비되어 있어야 한다.
        // LearningState preset은 Statistics 화면의 상단 언어/카드 값을 만들기 위한 입력이다.
        val state = learningStateFor(StatisticsDemoPreset.NormalStatistics)
        // StatisticsRepository preset은 차트가 읽을 history stream을 만들기 위한 입력이다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.NormalStatistics)

        // 문서의 기본 진입 시나리오는 EN 현재 언어와 JA 전환 후보를 함께 가진다.
        val english = repository.observeHistory("user-1", LangCode.EN).first()
        // JA history도 존재해야 언어 전환 전 일반 mock 실행에서 빈 화면만 보이지 않는다.
        val japanese = repository.observeHistory("user-1", LangCode.JA).first()

        // 기본 preset의 selected language는 EN이어야 한다.
        assertEquals(LangCode.EN, state.userPref?.selectedLang)
        // EN/JA LangState가 모두 있어야 Dashboard 언어 selector와 Statistics overview가 같은 입력을 본다.
        assertTrue(state.langStates.containsKey(LangCode.EN))
        assertTrue(state.langStates.containsKey(LangCode.JA))
        // 기본 차트 확인용 EN history가 content로 내려와야 한다.
        assertTrue(english is StatisticsHistoryState.Content)
        // 언어 전환 후보인 JA도 일반 content를 제공해야 한다.
        assertTrue(japanese is StatisticsHistoryState.Content)
    }

    @Test
    fun `02 ExpressionRangeOverflow provides expression histories over default axis max`() = runBlocking {
        // 표현 폭 y축 확장은 repository가 10 초과 history를 제공할 때만 수동 QA가 가능하다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.ExpressionRangeOverflow)

        // 표현 폭 확장 시나리오는 현재 사용자 EN 차트를 기준으로 확인한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // y축 확장 여부를 보기 위해 Content 상태만 유효한 입력으로 인정한다.
        val content = state as StatisticsHistoryState.Content
        // 10을 넘는 값이 하나라도 있어야 차트의 기본 0~10 범위 초과 케이스가 재현된다.
        assertTrue(content.histories.any { it.expressionRange > DEFAULT_EXPRESSION_AXIS_MAX })
    }

    @Test
    fun `03 DelayedLanguageSwitch keeps next language history empty while delaying previous language`() = runBlocking {
        // 언어 전환 시나리오는 EN 응답을 늦추고 JA history를 비워 stale result 방어를 눈으로 확인한다.
        // LearningState에는 JA LangState가 있어야 언어 변경 자체는 성공한 상태로 재현된다.
        val learningState = learningStateFor(StatisticsDemoPreset.DelayedLanguageSwitch)
        // Statistics history는 EN만 남기고 JA는 비워 새 언어 Empty 상태를 확인하게 한다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.DelayedLanguageSwitch)

        // EN 요청은 일부러 지연된다. timeout은 preset이 무한 대기 상태가 되지 않는지 지키는 안전장치다.
        val english = withTimeout(DELAYED_HISTORY_TIMEOUT_MS) {
            repository.observeHistory("user-1", LangCode.EN).first()
        }
        // JA는 history가 없어야 언어 전환 후 새 언어 Empty 상태를 볼 수 있다.
        val japanese = repository.observeHistory("user-1", LangCode.JA).first()

        // JA LangState가 없으면 Empty history가 아니라 overview 구성 실패 시나리오가 되어버린다.
        assertTrue(learningState.langStates.containsKey(LangCode.JA))
        // 지연 후 EN은 정상 content로 도착해야 stale 응답을 재현할 수 있다.
        assertTrue(english is StatisticsHistoryState.Content)
        // 새 언어인 JA는 Empty 상태여야 문서의 "이전 언어 통계가 남지 않음"을 확인할 수 있다.
        assertTrue(japanese is StatisticsHistoryState.Empty)
    }

    @Test
    fun `04 ShortHistory provides only one current language history`() = runBlocking {
        // history 1건은 Statistics 화면 진입은 가능하지만 chart는 Empty 상태로 내려가야 하는 입력이다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.ShortHistory)

        // ShortHistory는 현재 사용자/현재 언어에 history를 1건만 제공한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // repository 상태는 Content여야 하고, chart Empty 판단은 presentation에서 point 개수로 처리된다.
        val content = state as StatisticsHistoryState.Content
        // 1건이면 line chart를 그릴 수 없으므로 문서의 history 부족 시나리오 입력이 된다.
        assertEquals(1, content.histories.size)
    }

    @Test
    fun `05 EmptyHistory provides no chart source`() = runBlocking {
        // history가 전혀 없는 경우 chart source 없음 분기를 확인할 수 있어야 한다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.EmptyHistory)

        // 현재 사용자/현재 언어 기준으로도 history가 없어야 한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // EmptyHistory는 repository 단계에서 바로 Empty를 내려 chart source 없음 상태를 만든다.
        assertTrue(state is StatisticsHistoryState.Empty)
    }

    @Test
    fun `06 PendingSyncFailure keeps pending histories visible after failed sync`() = runBlocking {
        // pending sync 실패는 화면 차단이 아니라 local pending row 유지로 확인해야 한다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.PendingSyncFailure)

        // pending write-back 실패를 먼저 발생시켜 non-blocking 실패 경로를 만든다.
        val syncResult = repository.syncPendingHistories("user-1")
        // refresh는 no-op으로 성공해야 pending row가 SYNCED로 바뀌지 않는다.
        val refreshResult = repository.refreshHistory("user-1", LangCode.EN)
        // 화면이 다시 observe할 때 pending row가 그대로 남아 있어야 한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // sync 자체는 실패해야 문서의 pending sync 실패 시나리오가 된다.
        assertTrue(syncResult.isFailure)
        // refresh는 화면 차단 실패가 아니므로 성공으로 처리된다.
        assertTrue(refreshResult.isSuccess)
        val content = state as StatisticsHistoryState.Content
        // 모든 row가 PENDING이어야 "실패했는데 사라진 것처럼 보임"을 막는 preset이 된다.
        assertTrue(content.histories.all { it.syncStatus == SyncStatus.PENDING })
    }

    @Test
    fun `07 FetchFailure provides retry history state`() = runBlocking {
        // 조회 실패 preset은 chart Error dialog와 sync 보조 에러 문구를 재현하기 위한 입력이다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.FetchFailure)

        // observe 단계에서 Retry가 내려와야 Statistics 화면의 조회 실패 UI를 확인할 수 있다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // Retry는 사용자가 다시 시도 가능한 history 조회 실패를 뜻한다.
        assertTrue(state is StatisticsHistoryState.Retry)
    }

    @Test
    fun `08 RefreshFailure preserves existing local histories`() = runBlocking {
        // background refresh 실패는 기존 local history를 지우지 않아야 수동 QA에서 화면 유지 상태를 볼 수 있다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.RefreshFailure)

        // refresh 실패는 background 작업 실패로만 발생해야 한다.
        val refreshResult = repository.refreshHistory("user-1", LangCode.EN)
        // 실패 이후에도 local cache observe는 계속 content를 제공해야 한다.
        val state = repository.observeHistory("user-1", LangCode.EN).first()

        // refresh는 실패해야 문서의 background refresh 실패 시나리오가 된다.
        assertTrue(refreshResult.isFailure)
        // 기존 local history가 유지되어야 화면이 빈 상태로 무너지지 않는다.
        assertTrue(state is StatisticsHistoryState.Content)
    }

    @Test
    fun `09 InitialExternalMetrics provides empty overview metrics`() = runBlocking {
        // overview Empty 상태는 LearningState external snapshot이 초기값일 때만 재현된다.
        val state = learningStateFor(StatisticsDemoPreset.InitialExternalMetrics)
        // Statistics overview는 selected language인 EN의 external metrics를 카드 입력으로 사용한다.
        val external = state.langStates.getValue(LangCode.EN).external

        // 점수형 지표가 0.0이면 카드 mapper가 Empty 상태로 표시할 수 있다.
        assertEquals(0.0, external.grammarAccuracy, 0.0)
        // 표현 폭도 0이어야 초기 external snapshot과 일치한다.
        assertEquals(0, external.expressionRange)
        assertEquals(0.0, external.fluencyScore, 0.0)
        assertEquals(0.0, external.naturalnessScore, 0.0)
    }

    @Test
    fun `10A MissingSelectedLanguage removes selected language context`() = runBlocking {
        // selected language 누락은 Statistics overview 조립 실패 Error UI를 확인하기 위한 입력이다.
        val state = learningStateFor(StatisticsDemoPreset.MissingSelectedLanguage)

        // userPref가 없으면 selected language를 얻을 수 없어 overview 조립이 실패한다.
        assertNull(state.userPref)
    }

    @Test
    fun `10B MissingCurrentLangState removes current LangState context`() = runBlocking {
        // selected language는 있지만 LangState가 없을 때도 overview 조립은 실패해야 한다.
        val state = learningStateFor(StatisticsDemoPreset.MissingCurrentLangState)

        // selected language는 남겨두어 "언어 없음"이 아니라 "현재 LangState 없음" 실패를 분리한다.
        assertEquals(LangCode.EN, state.userPref?.selectedLang)
        // langStates가 비어 있어야 currentLangState()가 null이 되는 경계를 재현한다.
        assertTrue(state.langStates.isEmpty())
    }

    @Test
    fun `11 DelayedMetricSwitch delays history response while keeping chart data available`() = runBlocking {
        // repository는 metric type을 받지 않으므로, 빠른 지표 전환 preset은 history 응답 지연으로 stale 결과를 만든다.
        val repository = statisticsRepositoryFor(StatisticsDemoPreset.DelayedMetricSwitch)

        // 지연이 있더라도 timeout 안에 Content가 와야 실제 화면에서 마지막 선택 chart를 확인할 수 있다.
        val state = withTimeout(DELAYED_HISTORY_TIMEOUT_MS) {
            repository.observeHistory("user-1", LangCode.EN).first()
        }

        // 이 테스트는 UI의 마지막 metric 선택 검증이 아니라, delayed history 입력 준비만 보장한다.
        assertTrue(state is StatisticsHistoryState.Content)
    }

    private fun statisticsRepositoryFor(preset: StatisticsDemoPreset): FakeStatisticsRepository {
        // FakeStatisticsRepository 생성자는 기본 activePreset을 적용하므로,
        // 테스트에서는 원하는 preset을 명시적으로 다시 적용해 문서 순서와 독립적으로 검증한다.
        return FakeStatisticsRepository().apply {
            applyPreset(preset)
        }
    }

    private suspend fun learningStateFor(preset: StatisticsDemoPreset): GlobalLangState {
        // provider는 도메인별 fixture를 조립하고, repository는 완성된 state만 observe한다.
        val provider = DemoLearningStateProvider()
        val repository = FakeLearningStateRepo(provider).apply {
            // LearningState fake는 도메인별 preset 타입을 직접 알지 않도록 완성 state만 주입한다.
            seedStateForTest(provider.statisticsStateFor(preset))
        }
        // 테스트는 UI 없이 repository observe 결과만 확인하므로 첫 snapshot만 읽는다.
        return repository.observeLearningState().first()
    }

    private companion object {
        private const val DEFAULT_EXPRESSION_AXIS_MAX = 10
        private const val DELAYED_HISTORY_TIMEOUT_MS = 2_000L
    }
}
