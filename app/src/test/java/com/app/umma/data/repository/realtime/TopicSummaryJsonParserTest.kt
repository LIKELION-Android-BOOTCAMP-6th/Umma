package com.app.umma.data.repository.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicSummaryJsonParserTest {

    @Test
    fun `parses title and summary arrays`() {
        val parsed = TopicSummaryJsonParser.parse(
            """{"titles":["여행 계획"],"summaries":["사용자가 주말 여행 계획을 이야기했다."]}"""
        )

        assertEquals(listOf("여행 계획"), parsed.titles)
        assertEquals(listOf("사용자가 주말 여행 계획을 이야기했다."), parsed.summaries)
    }

    @Test
    fun `returns empty title when title is blank`() {
        val parsed = TopicSummaryJsonParser.parse(
            """{"titles":["   "],"summaries":["요약은 정상입니다."]}"""
        )

        assertTrue(parsed.titles.isEmpty())
        assertEquals(listOf("요약은 정상입니다."), parsed.summaries)
    }

    @Test
    fun `returns empty values for malformed json`() {
        val parsed = TopicSummaryJsonParser.parse("""{"titles":""")

        assertTrue(parsed.titles.isEmpty())
        assertTrue(parsed.summaries.isEmpty())
    }

    @Test
    fun `limits long title to dashboard friendly length`() {
        val parsed = TopicSummaryJsonParser.parse(
            """{"titles":["this title has too many words for a dashboard chip"],"summaries":["summary"]}"""
        )

        assertEquals(listOf("this title has too many"), parsed.titles)
    }
}
