package com.app.umma.domain.usecase.statistics

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuildStatisticsHistoryUseCaseTest {

    private val useCase = BuildStatisticsHistoryUseCase()

    @Test
    fun `builds history from saved language state result`() {
        val savedState = LangState.initial(LangCode.EN).copy(
            external = LangState.initial(LangCode.EN).external.copy(
                vocabularyLevel = VocabLevel.B2,
                grammarAccuracy = 0.73,
                expressionRange = 6,
                fluencyScore = 0.81,
                naturalnessScore = 0.68
            )
        )
        val updateResult = LearningStateUpdateResult(
            lang = LangCode.EN,
            savedState = savedState,
            sourceEventId = "analysis-123",
            applied = true,
            updatedAt = 1_700_000_000_000L
        )

        val history = useCase(userId = "user-1", updateResult = updateResult)

        // LS 저장 완료 결과를 그대로 history snapshot 으로 옮기는지 확인한다.
        // 여기서 값이 달라지면 Statistics 화면과 Correction 완료 시점의 의미가 어긋난다.
        assertEquals("user-1_en_analysis-123", history.id)
        assertEquals("user-1", history.userId)
        assertEquals(LangCode.EN, history.language)
        assertEquals(1_700_000_000_000L, history.recordedAt)
        assertEquals(VocabLevel.B2, history.vocabularyLevel)
        assertEquals(0.73, history.grammarAccuracy, 0.0)
        assertEquals(6, history.expressionRange)
        assertEquals(0.81, history.fluencyScore, 0.0)
        assertEquals(0.68, history.naturalnessScore, 0.0)
        assertEquals("analysis-123", history.sourceEventId)
        assertEquals(SyncStatus.PENDING, history.syncStatus)
        assertFalse(history.id.isBlank())
    }
}
