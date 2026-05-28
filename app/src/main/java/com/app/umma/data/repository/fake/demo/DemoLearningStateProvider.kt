package com.app.umma.data.repository.fake.demo

import com.app.umma.data.repository.fake.demo.statistics.StatisticsDemoPreset
import com.app.umma.data.repository.fake.demo.statistics.StatisticsDemoPresetConfig
import com.app.umma.data.repository.fake.demo.statistics.StatisticsLearningStateFixtures
import com.app.umma.domain.model.learningstate.GlobalLangState
import javax.inject.Inject

/**
 * mockDebug에서 FakeLearningStateRepo에 주입할 초기 state를 조립하는 provider다.
 *
 * 공통 FakeLearningStateRepo는 repository 계약처럼 동작만 담당하고,
 * 도메인별 데모 데이터는 각 도메인 fixture package에서 만든다.
 */
class DemoLearningStateProvider @Inject constructor() {

    fun initialState(): GlobalLangState {
        // 현재는 통계 도메인 preset만 준비되어 있다.
        // 추후 SRS/Correction/Dashboard preset이 추가되면 이 provider에서 조합 우선순위를 명시한다.
        return statisticsStateFor(StatisticsDemoPresetConfig.activePreset)
    }

    fun statisticsStateFor(preset: StatisticsDemoPreset): GlobalLangState {
        return StatisticsLearningStateFixtures.forPreset(preset)
    }
}
