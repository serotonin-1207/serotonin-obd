package com.eunho.leafobd.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanMonitorParserTest {

    @Test
    fun `CAN ID별로 프레임을 집계한다`() {
        val raw = "358 00 08 80\r1DA 01 02 03 04 05 06 07\r358 00 08 80\r358 00 08 81\r"

        val result = CanMonitorParser.parse(raw, 10_000)

        assertTrue(result.hasTraffic)
        assertEquals(4, result.totalFrames)
        assertEquals(2, result.frames.size)
        // 많이 나온 순으로 정렬된다
        assertEquals("358", result.frames[0].id)
        assertEquals(3, result.frames[0].count)
        assertEquals("1DA", result.frames[1].id)
    }

    @Test
    fun `29비트 ID도 집계한다`() {
        val raw = "18DAF110 01 02 03 04\r18DAF110 05 06 07 08\r"

        val result = CanMonitorParser.parse(raw, 5_000)

        assertEquals(1, result.frames.size)
        assertEquals("18DAF110", result.frames[0].id)
        assertEquals(2, result.frames[0].count)
    }

    @Test
    fun `상태 문자열은 프레임으로 세지 않는다`() {
        val raw = "SEARCHING...\rNO DATA\rOK\r?\rBUFFER FULL\r"

        val result = CanMonitorParser.parse(raw, 3_000)

        assertFalse(result.hasTraffic)
        assertEquals(0, result.totalFrames)
    }

    @Test
    fun `프레임이 없으면 게이트웨이 가능성을 함께 안내한다`() {
        val result = CanMonitorParser.parse("", 10_000)

        assertFalse(result.hasTraffic)
        assertTrue(result.interpretation.contains("게이트웨이"))
        // 차량이 꺼졌다고 단정하지 않는다
        assertTrue(result.interpretation.contains("단정할 수는 없습니다"))
    }

    @Test
    fun `프레임이 있으면 차량이 깨어 있다고 판단한다`() {
        val result = CanMonitorParser.parse("358 00 08 80\r", 10_000)

        assertTrue(result.interpretation.contains("깨어 있습니다"))
    }

    @Test
    fun `오류 메시지가 있으면 그대로 보여 준다`() {
        val result = CanMonitorResult(errorMessage = "어댑터 연결이 끊어졌습니다.")

        assertEquals("어댑터 연결이 끊어졌습니다.", result.interpretation)
    }

    @Test
    fun `원시 데이터는 길이를 제한해 보관한다`() {
        val raw = (1..2000).joinToString("\r") { "358 00 08 80" }

        val result = CanMonitorParser.parse(raw, 10_000)

        assertTrue(result.totalFrames > 1000)
        assertTrue(result.raw.length <= 4000)
    }
}
