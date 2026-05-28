package com.app.umma.data.repository.fake.demo.correction

import com.app.umma.data.repository.fake.demo.statistics.StatisticsLearningStateFixtures
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SessionSummary

object CorrectionLearningStateFixtures {

    fun forPreset(preset: CorrectionDemoPreset): GlobalLangState {
        return when (preset) {
            CorrectionDemoPreset.EmptyInitial -> correctionUnavailable()
            CorrectionDemoPreset.TopicTitleSuccess -> withTopic("여행 계획")
            CorrectionDemoPreset.TopicTitleEmpty -> withTopic(null)
            else -> correctionAvailable()
        }
    }

    fun correctionAvailable(): GlobalLangState {
        val state = StatisticsLearningStateFixtures.normal()
        val lang = state.userPref?.selectedLang ?: LangCode.EN
        return state.withCorrectionSummary(
            lang = lang,
            correctionAvailable = true,
            recentTopic = "여행 계획"
        )
    }

    private fun correctionUnavailable(): GlobalLangState {
        val state = StatisticsLearningStateFixtures.normal()
        val lang = state.userPref?.selectedLang ?: LangCode.EN
        return state.withCorrectionSummary(
            lang = lang,
            correctionAvailable = false,
            recentTopic = null
        )
    }

    private fun withTopic(topic: String?): GlobalLangState {
        val state = correctionAvailable()
        val lang = state.userPref?.selectedLang ?: LangCode.EN
        return state.withCorrectionSummary(
            lang = lang,
            correctionAvailable = true,
            recentTopic = topic
        )
    }

    private fun GlobalLangState.withCorrectionSummary(
        lang: LangCode,
        correctionAvailable: Boolean,
        recentTopic: String?
    ): GlobalLangState {
        val previousDash = dashSummaries[lang] ?: DashSummary.initial(lang)
        val previousSession = sessionSummaries[lang] ?: SessionSummary.initial(lang)
        val nextDash = previousDash.copy(
            correctionAvailable = correctionAvailable,
            recentTopic = recentTopic,
            updatedAt = UPDATED_AT
        )
        val nextSession = previousSession.copy(
            correctionAvailable = correctionAvailable,
            recentTopic = recentTopic,
            updatedAt = UPDATED_AT
        )
        return copy(
            dashSummaries = dashSummaries + (lang to nextDash),
            sessionSummaries = sessionSummaries + (lang to nextSession),
            isPreloaded = true
        )
    }

    private const val UPDATED_AT: Long = 1_700_000_000_000L
}
