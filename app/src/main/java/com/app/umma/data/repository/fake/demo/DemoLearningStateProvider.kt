package com.app.umma.data.repository.fake.demo

import com.app.umma.data.repository.fake.demo.correction.CorrectionDemoPreset
import com.app.umma.data.repository.fake.demo.correction.CorrectionDemoPresetConfig
import com.app.umma.data.repository.fake.demo.correction.CorrectionLearningStateFixtures
import com.app.umma.data.repository.fake.demo.statistics.StatisticsDemoPreset
import com.app.umma.data.repository.fake.demo.statistics.StatisticsDemoPresetConfig
import com.app.umma.data.repository.fake.demo.statistics.StatisticsLearningStateFixtures
import com.app.umma.domain.model.learningstate.GlobalLangState
import javax.inject.Inject

/**
 * mockDebug에서 FakeLearningStateRepo에 주입할 초기 state를 조립한다.
 *
 * Statistics preset이 기본값이 아니면 Statistics 시나리오를 우선한다.
 * Statistics가 기본값이면 Correction preset을 적용한다.
 */
class DemoLearningStateProvider @Inject constructor() {

    fun initialState(): GlobalLangState {
        if (StatisticsDemoPresetConfig.activePreset != StatisticsDemoPreset.NormalStatistics) {
            return statisticsStateFor(StatisticsDemoPresetConfig.activePreset)
        }
        return correctionStateFor(CorrectionDemoPresetConfig.activePreset)
    }

    fun correctionStateFor(preset: CorrectionDemoPreset): GlobalLangState {
        return CorrectionLearningStateFixtures.forPreset(preset)
    }

    fun statisticsStateFor(preset: StatisticsDemoPreset): GlobalLangState {
        return StatisticsLearningStateFixtures.forPreset(preset)
    }
}
