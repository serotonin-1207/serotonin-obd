package com.eunho.leafobd.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoltageParserTest {

    @Test
    fun `표준 응답에서 전압을 읽는다`() {
        val reading = VoltageParser.parse("12.4V\r\r>")

        assertEquals(12.4, reading.volts!!, 0.001)
        assertEquals("12.4 V", reading.display)
    }

    @Test
    fun `명령 echo가 있어도 전압을 읽는다`() {
        val reading = VoltageParser.parse("ATRV\r13.8V\r\r>")

        assertEquals(13.8, reading.volts!!, 0.001)
    }

    @Test
    fun `공백이 섞여 있어도 전압을 읽는다`() {
        val reading = VoltageParser.parse("12 . 0\r11.9 V\r>")

        assertEquals(11.9, reading.volts!!, 0.001)
    }

    @Test
    fun `해석할 수 없으면 volts가 null이다`() {
        val reading = VoltageParser.parse("NO DATA\r>")

        assertNull(reading.volts)
    }

    @Test
    fun `빈 응답이어도 예외가 발생하지 않는다`() {
        val reading = VoltageParser.parse("")

        assertNull(reading.volts)
        assertEquals("확인 불가", reading.display)
    }

    @Test
    fun `전압이 낮으면 참고 안내를 제공한다`() {
        assertNotNull(VoltageParser.parse("10.5V\r>").hint)
        assertNotNull(VoltageParser.parse("11.6V\r>").hint)
    }

    @Test
    fun `정상 범위에서는 안내가 없다`() {
        assertNull(VoltageParser.parse("12.6V\r>").hint)
    }
}
