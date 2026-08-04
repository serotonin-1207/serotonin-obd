package com.eunho.leafobd.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseTextTest {

    @Test
    fun `프롬프트와 빈 줄을 제거한다`() {
        assertEquals(listOf("41 0C 1A F8"), ResponseText.lines("\r41 0C 1A F8\r\r>"))
    }

    @Test
    fun `명령 echo를 제거한다`() {
        assertEquals(listOf("OK"), ResponseText.dataLines("ATE0\rOK\r\r>", "ATE0"))
    }

    @Test
    fun `SEARCHING 표시를 제거한다`() {
        assertEquals(listOf("43 00"), ResponseText.dataLines("SEARCHING...\r43 00\r>", "03"))
    }

    @Test
    fun `NO DATA는 오류 키워드로 보지 않는다`() {
        assertTrue(ResponseText.isNoData("NO DATA\r>"))
        assertNull(ResponseText.errorKeyword("NO DATA\r>", "03"))
    }

    @Test
    fun `오류 키워드를 찾아 한국어로 바꾼다`() {
        val keyword = ResponseText.errorKeyword("CAN ERROR\r>", "03")

        assertEquals("CAN ERROR", keyword)
        assertTrue(ResponseText.errorMessageKorean(keyword!!).contains("CAN"))
    }

    @Test
    fun `물음표는 미지원 명령으로 본다`() {
        assertEquals("?", ResponseText.errorKeyword("?\r>", "0A"))
    }

    @Test
    fun `OK 응답을 인식한다`() {
        assertTrue(ResponseText.isOk("OK\r>", "ATH0"))
        assertFalse(ResponseText.isOk("?\r>", "ATH0"))
    }

    @Test
    fun `홀수 길이 16진 문자열은 마지막 반쪽 바이트를 버린다`() {
        assertEquals(listOf(0x43, 0x01), ResponseText.hexBytes(listOf("43013")))
    }

    @Test
    fun `16진수가 아닌 줄은 건너뛴다`() {
        assertEquals(listOf(0x43, 0x00), ResponseText.hexBytes(listOf("STOPPED", "4300")))
    }

    @Test
    fun `16진 바이트를 보기 좋게 표시한다`() {
        assertEquals("43 01 33", ResponseText.formatHex(listOf(0x43, 0x01, 0x33)))
    }
}
