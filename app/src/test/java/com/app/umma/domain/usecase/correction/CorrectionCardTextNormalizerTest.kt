package com.app.umma.domain.usecase.correction

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CorrectionCardTextNormalizer] 정규화 규칙 단위 테스트.
 *
 * 대소문자·구두점·공백 변형이 비교에 영향을 주지 않는지 검증한다.
 */
class CorrectionCardTextNormalizerTest {

    private fun normalize(text: String) = CorrectionCardTextNormalizer.normalize(text)

    @Test
    fun `lowercases text`() {
        assertEquals("hello world", normalize("HELLO WORLD"))
    }

    @Test
    fun `collapses internal whitespace`() {
        assertEquals("hello world", normalize("hello  world"))
        assertEquals("a b c", normalize("a   b   c"))
    }

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals("hello", normalize("  hello  "))
        assertEquals("hello world", normalize("  hello world  "))
    }

    @Test
    fun `removes trailing punctuation`() {
        assertEquals("hello", normalize("hello."))
        assertEquals("hello", normalize("hello!"))
        assertEquals("hello", normalize("hello?"))
        assertEquals("hello", normalize("hello,"))
        assertEquals("hello", normalize("hello…"))
    }

    @Test
    fun `removes leading punctuation`() {
        assertEquals("hello", normalize(",hello"))
        assertEquals("hello", normalize(".hello"))
    }

    @Test
    fun `removes CJK trailing punctuation`() {
        assertEquals("학교에 가요", normalize("학교에 가요。"))
        assertEquals("학교에 가요", normalize("학교에 가요！"))
        assertEquals("학교에 가요", normalize("학교에 가요？"))
        assertEquals("오늘 날씨가 좋네요", normalize("오늘 날씨가 좋네요、"))
    }

    @Test
    fun `case and punctuation variants normalize to same string`() {
        // 대소문자/문장부호만 다른 교정 텍스트는 중복으로 판정해야 한다.
        assertEquals(normalize("I go to school."), normalize("i go to school"))
        assertEquals(normalize("Hello!"), normalize("hello"))
        assertEquals(normalize("She doesn't like apples."), normalize("she doesn't like apples"))
    }

    @Test
    fun `returns empty string for blank input`() {
        assertEquals("", normalize(""))
        assertEquals("", normalize("   "))
    }

    @Test
    fun `returns empty string for punctuation only input`() {
        assertEquals("", normalize("..."))
        assertEquals("", normalize("!?"))
        assertEquals("", normalize("。！？"))
    }

    @Test
    fun `preserves internal punctuation`() {
        // 양끝 구두점만 제거하고 문장 내부 구두점은 건드리지 않는다.
        assertEquals("it's a test", normalize("It's a test."))
        assertEquals("don't worry", normalize("Don't worry!"))
    }
}
