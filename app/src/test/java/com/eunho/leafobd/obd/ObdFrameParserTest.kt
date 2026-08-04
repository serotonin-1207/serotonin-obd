package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CAN 헤더 표시(ATH1) 상태의 ISO-TP 프레임 재조립 테스트.
 *
 * 헤더를 켜면 ELM327 이 프레임을 합쳐 주지 않으므로
 * 앱이 PCI(흐름 제어 바이트)를 직접 해석해야 한다.
 */
class ObdFrameParserTest {

    private fun lines(raw: String, command: String? = null) =
        ResponseText.dataLines(raw, command)

    @Test
    fun `헤더가 꺼져 있으면 하나의 응답으로 합친다`() {
        val result = ObdFrameParser.parse(lines("43040AA60A1F01332104"), headersOn = false)

        assertEquals(1, result.size)
        assertNull(result[0].ecuId)
        assertEquals(10, result[0].bytes.size)
    }

    @Test
    fun `헤더가 꺼진 다중 프레임도 합친다`() {
        val raw = "00A\r0: 43 04 01 33 02 45\r1: 03 21 04 56\r\r>"

        val result = ObdFrameParser.parse(lines(raw, "03"), headersOn = false)

        assertEquals(1, result.size)
        assertEquals(listOf(0x43, 0x04, 0x01, 0x33, 0x02, 0x45, 0x03, 0x21, 0x04, 0x56), result[0].bytes)
    }

    @Test
    fun `11비트 헤더의 단일 프레임을 읽는다`() {
        // 7E8 = CAN ID, 06 = 단일 프레임 6바이트
        val result = ObdFrameParser.parse(listOf("7E80643020133 0245"), headersOn = true)

        assertEquals(1, result.size)
        assertEquals("7E8", result[0].ecuId)
        assertEquals(listOf(0x43, 0x02, 0x01, 0x33, 0x02, 0x45), result[0].bytes)
    }

    @Test
    fun `29비트 헤더도 처리한다`() {
        // 18DAF110 = 29비트 CAN ID (8자리)
        val result = ObdFrameParser.parse(listOf("18DAF1100643020133 0245"), headersOn = true)

        assertEquals(1, result.size)
        assertEquals("18DAF110", result[0].ecuId)
        assertEquals(listOf(0x43, 0x02, 0x01, 0x33, 0x02, 0x45), result[0].bytes)
    }

    @Test
    fun `최초 프레임과 연속 프레임을 순서대로 합친다`() {
        val result = ObdFrameParser.parse(
            listOf(
                "7E81014490201534A4E",   // 10 14 = 최초 프레임, 전체 20바이트
                "7E8214641415A453055",   // 21 = 연속 프레임 1
                "7E82236303132333435"    // 22 = 연속 프레임 2
            ),
            headersOn = true
        )

        assertEquals(1, result.size)
        assertEquals("7E8", result[0].ecuId)
        assertEquals(20, result[0].bytes.size)
        assertEquals(listOf(0x49, 0x02, 0x01), result[0].bytes.take(3))
    }

    @Test
    fun `선언된 길이보다 많이 오면 잘라 낸다`() {
        val result = ObdFrameParser.parse(
            listOf(
                "7E81008430201330245",   // 10 08 = 전체 8바이트인데 6바이트가 들어 있음
                "7E82103210400000000"    // 연속 프레임에 패딩이 붙어 있음
            ),
            headersOn = true
        )

        assertEquals(8, result[0].bytes.size)
    }

    @Test
    fun `ECU가 여러 곳이면 따로 나눈다`() {
        val result = ObdFrameParser.parse(
            listOf(
                "7E80643020133 0245",
                "7EA044301 2104"
            ),
            headersOn = true
        )

        assertEquals(2, result.size)
        assertEquals("7E8", result[0].ecuId)
        assertEquals("7EA", result[1].ecuId)
        assertEquals(listOf(0x43, 0x01, 0x21, 0x04), result[1].bytes)
    }

    @Test
    fun `헤더 모드에서 DTC에 응답 ECU가 기록된다`() {
        val raw = "7E80643020133 0245\r7EA044301 2104\r\r>"

        val result = DtcParser.parse(raw, ObdMode.STORED_DTC, headersOn = true)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(listOf("P0133", "P0245", "P2104"), result.codes.map { it.code })
        assertEquals(listOf("7E8", "7E8", "7EA"), result.codes.map { it.ecu })
        assertTrue(result.normalizedHex.contains("[7E8]"))
    }

    @Test
    fun `16진수가 아닌 줄은 건너뛴다`() {
        val result = ObdFrameParser.parse(listOf("SEARCHING", "7E80643020133 0245"), headersOn = true)

        assertEquals(1, result.size)
        assertEquals("7E8", result[0].ecuId)
    }

    @Test
    fun `빈 입력이면 빈 목록을 돌려준다`() {
        assertTrue(ObdFrameParser.parse(emptyList(), headersOn = true).isEmpty())
        assertTrue(ObdFrameParser.parse(emptyList(), headersOn = false).isEmpty())
    }
}
