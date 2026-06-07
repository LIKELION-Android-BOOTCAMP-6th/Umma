package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildSessionCompressionPayloadUseCaseTest {

    private val useCase = BuildSessionCompressionPayloadUseCase()

    @Test
    fun `builds compression command from correction suggestions and user turns`() {
        // Correction 완료 후 Session Memory 에 남길 최소 압축 재료를 만든다.
        // 핵심 문장은 교정된 afterText, topic 은 최근 user turn 을 우선해 구성되는지 확인한다.
        val result = useCase(
            language = LangCode.EN,
            selectedSuggestions = listOf(baseSuggestion()),
            recentUserTurns = listOf(
                ConversationTurn(
                    speaker = TurnSpeaker.USER,
                    text = "I want to talk about travel and museum plans."
                )
            ),
            compressedAt = 2_000L
        )

        val command = result.getOrThrow()

        assertNotNull(command)
        assertEquals(LangCode.EN, command!!.language)
        assertEquals(2_000L, command.compressedAt)
        assertEquals(listOf("I went to the museum yesterday."), command.topicKeySentences)
        assertTrue(command.topicSummaries.first().contains("I go to museum yesterday."))
        assertTrue(command.recentTopics.contains("travel"))
    }

    @Test
    fun `returns null when there is no meaningful compression payload`() {
        // 빈 payload 로 RT-003 compression 을 호출하면 원문 buffer 만 지워질 수 있다.
        // 그래서 요약/핵심 문장이 모두 비어 있으면 command 대신 null 을 돌려야 한다.
        val result = useCase(
            language = LangCode.EN,
            selectedSuggestions = listOf(
                baseSuggestion().copy(
                    beforeText = " ",
                    afterText = " ",
                    explanation = " "
                )
            ),
            recentUserTurns = emptyList(),
            compressedAt = 2_000L
        )

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `limits repeated summaries and key sentences`() {
        // 같은 교정 결과가 여러 번 들어와도 압축 metadata 가 불필요하게 커지지 않아야 한다.
        // distinct + limit 정책이 유지되는지 반복 입력으로 확인한다.
        val repeated = baseSuggestion()
        val result = useCase(
            language = LangCode.EN,
            selectedSuggestions = List(8) { repeated.copy(id = "s-$it") },
            recentUserTurns = emptyList(),
            compressedAt = 2_000L
        )

        val command = result.getOrThrow()

        assertNotNull(command)
        assertEquals(1, command!!.topicSummaries.size)
        assertEquals(1, command.topicKeySentences.size)
    }

    @Test
    fun `prefers AI-mapped recentTopics and topicSummaries over code-based extraction (SSOT)`() {
        // COR-TUNE-010: topicSummaries/recentTopics 의 SSOT 는 SummarizeRecentTopicsUseCase 의 AI 매핑 결과다.
        // AI 값이 있으면 코드 기반 단어빈도/before->after 요약은 전혀 쓰이지 않아야 한다.
        val result = useCase(
            language = LangCode.EN,
            selectedSuggestions = listOf(baseSuggestion()),
            recentUserTurns = listOf(
                ConversationTurn(
                    speaker = TurnSpeaker.USER,
                    text = "I want to talk about travel and museum plans."
                )
            ),
            compressedAt = 2_000L,
            aiRecentTopics = listOf("museum visit", "daily routine"),
            aiTopicSummaries = listOf("Learner practiced past-tense museum stories.")
        )

        val command = result.getOrThrow()

        assertNotNull(command)
        assertEquals(listOf("museum visit", "daily routine"), command!!.recentTopics)
        assertEquals(listOf("Learner practiced past-tense museum stories."), command.topicSummaries)
        // 코드 기반 폴백 산출물(단어빈도 "travel"/before->after 텍스트)이 섞여 들어오지 않아야 한다.
        assertTrue("코드 기반 recentTopics 가 AI 결과를 덮어씀", "travel" !in command.recentTopics)
        assertTrue(
            "코드 기반 topicSummaries 가 AI 결과를 덮어씀",
            command.topicSummaries.none { it.contains("I go to museum yesterday.") }
        )
    }

    @Test
    fun `falls back to code-based extraction when AI mapping is empty`() {
        // AI 매핑이 비어 있으면(실패·미적용) 기존 코드 기반 폴백으로 압축 흐름을 계속 진행해야 한다(pending only).
        val result = useCase(
            language = LangCode.EN,
            selectedSuggestions = listOf(baseSuggestion()),
            recentUserTurns = listOf(
                ConversationTurn(
                    speaker = TurnSpeaker.USER,
                    text = "I want to talk about travel and museum plans."
                )
            ),
            compressedAt = 2_000L,
            aiRecentTopics = emptyList(),
            aiTopicSummaries = emptyList()
        )

        val command = result.getOrThrow()

        assertNotNull(command)
        assertTrue("AI 가 비었는데 코드 기반 recentTopics 폴백이 동작하지 않음", command!!.recentTopics.contains("travel"))
        assertTrue(
            "AI 가 비었는데 코드 기반 topicSummaries 폴백이 동작하지 않음",
            command.topicSummaries.first().contains("I go to museum yesterday.")
        )
    }

    private fun baseSuggestion(): CorrectionSuggestion {
        return CorrectionSuggestion(
            id = "s-1",
            lang = LangCode.EN,
            sourceCandidateIds = listOf("c-1"),
            sourceTurnIndex = 0,
            beforeText = "I go to museum yesterday.",
            nativeText = "나는 어제 박물관에 갔다.",
            afterText = "I went to the museum yesterday.",
            explanation = "Use past tense for yesterday."
        )
    }
}
