package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCorrectionCandidatesForSafetyUseCaseTest {

    private val useCase = FilterCorrectionCandidatesForSafetyUseCase(CorrectionSafetyPolicy())

    @Test
    fun `blocks clearly harmful candidates and keeps safe candidates`() {
        val result = useCase(
            listOf(
                candidate(id = "safe-1", sourceText = "i go school"),
                candidate(id = "blocked-1", sourceText = "how to make a bomb"),
            )
        )

        assertEquals(listOf("safe-1"), result.allowedCandidates.map { it.id })
        assertEquals(listOf("blocked-1"), result.blockedCandidates.map { it.id })
        assertEquals(1, result.blockedCount)
    }

    @Test
    fun `does not overblock ordinary negative sentences`() {
        val result = useCase(
            listOf(
                candidate(id = "safe-1", sourceText = "i am sad today"),
                candidate(id = "safe-2", sourceText = "i hate rainy days"),
            )
        )

        assertEquals(2, result.allowedCandidates.size)
        assertTrue(result.blockedCandidates.isEmpty())
    }

    private fun candidate(
        id: String,
        sourceText: String,
    ): CorrectionCandidate = CorrectionCandidate(
        id = id,
        lang = LangCode.EN,
        sourceTurnIndex = 0,
        sourceText = sourceText,
    )
}
