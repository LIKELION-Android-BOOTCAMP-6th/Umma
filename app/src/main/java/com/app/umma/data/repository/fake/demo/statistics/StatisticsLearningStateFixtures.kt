package com.app.umma.data.repository.fake.demo.statistics

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.learningstate.VocabLevel

/**
 * Statistics 화면 preset에서 필요한 LearningState 입력 fixture다.
 *
 * 통계 화면은 current LangState.external과 selected language를 기준으로 overview를 구성하므로,
 * 이 파일은 해당 입력 상태만 도메인별로 관리한다.
 */
object StatisticsLearningStateFixtures {

    fun forPreset(preset: StatisticsDemoPreset): GlobalLangState {
        return when (preset) {
            StatisticsDemoPreset.InitialExternalMetrics -> normal().copy(
                langStates = normal().langStates + (
                    LangCode.EN to LangState.initial(LangCode.EN).copy(updatedAt = STALE_TIMESTAMP)
                )
            )
            StatisticsDemoPreset.MissingSelectedLanguage -> normal().copy(userPref = null)
            StatisticsDemoPreset.MissingCurrentLangState -> normal().copy(langStates = emptyMap())
            else -> normal()
        }
    }

    fun normal(): GlobalLangState {
        val primary = LangCode.EN
        return GlobalLangState(
            userPref = UserLangPref(
                nativeLang = LangCode.KO,
                primaryLang = primary,
                selectedLang = primary,
                learningLangs = listOf(LangCode.EN, LangCode.JA),
                updatedAt = STALE_TIMESTAMP
            ),
            langStates = mapOf(
                primary to LangState.initial(primary).copy(
                    external = ExternalMetrics(
                        vocabularyLevel = VocabLevel.C1,
                        grammarAccuracy = 0.88,
                        expressionRange = 8,
                        fluencyScore = 0.81,
                        naturalnessScore = 0.78
                    ),
                    updatedAt = STALE_TIMESTAMP
                ),
                LangCode.JA to LangState.initial(LangCode.JA).copy(
                    external = ExternalMetrics(
                        vocabularyLevel = VocabLevel.B1,
                        grammarAccuracy = 0.70,
                        expressionRange = 5,
                        fluencyScore = 0.61,
                        naturalnessScore = 0.64
                    ),
                    updatedAt = STALE_TIMESTAMP
                )
            ),
            dashSummaries = mapOf(
                primary to DashSummary(
                    lang = primary,
                    recentMinutes = 30,
                    recentTopic = "Travel",
                    correctionAvailable = true,
                    dueFlashcards = 12,
                    savedFlashcards = 84,
                    grammarDelta = 8,
                    fluencyDelta = 12,
                    vocabDelta = 5,
                    naturalnessDelta = 15,
                    updatedAt = STALE_TIMESTAMP
                ),
                LangCode.JA to DashSummary(
                    lang = LangCode.JA,
                    recentMinutes = 15,
                    recentTopic = "日常会話",
                    correctionAvailable = true,
                    dueFlashcards = 5,
                    savedFlashcards = 22,
                    grammarDelta = 3,
                    fluencyDelta = 4,
                    vocabDelta = 2,
                    naturalnessDelta = 6,
                    updatedAt = STALE_TIMESTAMP
                )
            ),
            sessionSummaries = mapOf(
                primary to SessionSummary(
                    lang = primary,
                    correctionAvailable = true,
                    recentMinutes = 30,
                    recentTopic = "Travel",
                    updatedAt = STALE_TIMESTAMP
                ),
                LangCode.JA to SessionSummary(
                    lang = LangCode.JA,
                    correctionAvailable = true,
                    recentMinutes = 15,
                    recentTopic = "日常会話",
                    updatedAt = STALE_TIMESTAMP
                )
            ),
            flashcardSummaries = mapOf(
                primary to FlashcardSummary(
                    lang = primary,
                    dueFlashcards = 12,
                    savedFlashcards = 84,
                    updatedAt = STALE_TIMESTAMP
                ),
                LangCode.JA to FlashcardSummary(
                    lang = LangCode.JA,
                    dueFlashcards = 5,
                    savedFlashcards = 22,
                    updatedAt = STALE_TIMESTAMP
                )
            ),
            isPreloaded = true
        )
    }

    private const val STALE_TIMESTAMP: Long = 1_700_000_000_000L
}
