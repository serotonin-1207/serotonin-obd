package com.eunho.leafobd.obd

import com.eunho.leafobd.util.VinMasking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode 01 · Mode 02 · Mode 09 파서 테스트. */
class VehicleInfoParserTest {

    // ---------- 0100 계열: 지원 PID 비트맵 ----------

    @Test
    fun `지원 PID 비트맵을 해석한다`() {
        // 0x98 = 1001 1000 -> PID 01, 04, 05 / 마지막 바이트 0x01 -> PID 0x20(다음 구간)
        val result = Mode01Parser.parseSupportedPids(
            rawResponse = "41 00 98 00 00 01\r\r>",
            base = 0x00,
            command = "0100"
        )

        assertTrue(result.supports(0x01))
        assertTrue(result.supports(0x04))
        assertTrue(result.supports(0x05))
        assertTrue(result.supports(0x20))
        assertFalse(result.supports(0x02))
        assertFalse(result.supports(0x0C))
    }

    @Test
    fun `두 번째 구간은 시작값을 더해 계산한다`() {
        // 0x20 구간에서 세 번째 바이트 0x80 -> 최상위 비트 -> PID 0x31
        val result = Mode01Parser.parseSupportedPids(
            rawResponse = "41 20 00 00 80 01\r>",
            base = 0x20,
            command = "0120"
        )

        assertTrue(result.supports(0x31))
        assertTrue(result.supports(0x40))
        assertFalse(result.supports(0x21))
    }

    @Test
    fun `프리즈 프레임 비트맵은 프레임 번호를 건너뛴다`() {
        // 42 00 00 <비트맵> : PID 뒤에 프레임 번호가 한 바이트 더 있다.
        val result = Mode01Parser.parseSupportedPids(
            rawResponse = "42 00 00 48 00 00 00\r>",
            base = 0x00,
            command = "020000",
            responsePrefix = 0x42,
            extraSkip = 1
        )

        // 0x48 = 0100 1000 -> PID 02, 05
        assertTrue(result.supports(0x02))
        assertTrue(result.supports(0x05))
        assertFalse(result.supports(0x01))
    }

    @Test
    fun `응답이 없으면 빈 목록이다`() {
        val result = Mode01Parser.parseSupportedPids("NO DATA\r>", 0x00, "0100")

        assertTrue(result.ids.isEmpty())
    }

    // ---------- 0101: 경고등과 DTC 개수 ----------

    @Test
    fun `경고등 점등과 DTC 개수를 읽는다`() {
        // 0x84 = 1000 0100 -> MIL 점등, 코드 4건
        val status = Mode01Parser.parseMonitorStatus("41 01 84 07 E5 00\r\r>")!!

        assertTrue(status.milOn)
        assertEquals(4, status.dtcCount)
        assertEquals("점등", status.milLabel)
    }

    @Test
    fun `경고등이 꺼져 있으면 소등으로 표시한다`() {
        val status = Mode01Parser.parseMonitorStatus("41 01 00 07 E5 00\r>")!!

        assertFalse(status.milOn)
        assertEquals(0, status.dtcCount)
        assertEquals("소등", status.milLabel)
    }

    @Test
    fun `헤더 모드에서 응답 ECU를 기록한다`() {
        val status = Mode01Parser.parseMonitorStatus(
            "7E8064101840 7E500\r>",
            headersOn = true
        )!!

        assertEquals("7E8", status.ecu)
        assertTrue(status.milOn)
    }

    @Test
    fun `해석할 수 없으면 null 이다`() {
        assertNull(Mode01Parser.parseMonitorStatus("NO DATA\r>"))
        assertNull(Mode01Parser.parseMonitorStatus(""))
    }

    // ---------- PID 값 ----------

    @Test
    fun `제어 모듈 전압을 계산한다`() {
        val value = Mode01Parser.parsePidValue("41 42 30 D4\r>", 0x42, "0142")!!

        assertEquals(12.5, value.value!!, 0.001)
        assertEquals("12.50 V", value.display)
    }

    @Test
    fun `냉각수 온도는 40을 뺀다`() {
        val value = Mode01Parser.parsePidValue("41 05 5A\r>", 0x05, "0105")!!

        assertEquals(50.0, value.value!!, 0.001)
    }

    @Test
    fun `엔진 회전수는 4로 나눈다`() {
        val value = Mode01Parser.parsePidValue("41 0C 1A F8\r>", 0x0C, "010C")!!

        assertEquals(1726.0, value.value!!, 0.001)
    }

    @Test
    fun `프리즈 프레임 PID는 프레임 번호를 건너뛴다`() {
        val value = Mode01Parser.parsePidValue(
            rawResponse = "42 05 00 5A\r>",
            pidId = 0x05,
            command = "020500",
            requestMode = 0x02
        )!!

        assertEquals(50.0, value.value!!, 0.001)
    }

    @Test
    fun `알 수 없는 PID는 원시 바이트를 보존한다`() {
        val value = PidDecoder.decode(0x7F, listOf(0xAB, 0xCD))

        assertNull(value.value)
        assertEquals("AB CD", value.rawHex)
        assertEquals("AB CD", value.display)
    }

    // ---------- 프리즈 프레임 원인 DTC ----------

    @Test
    fun `프리즈 프레임 원인 DTC를 읽는다`() {
        val dtc = FreezeFrameParser.parseTriggerDtc("42 02 00 0A A6\r\r>", "020200")

        assertEquals("P0AA6", dtc)
    }

    @Test
    fun `원인 DTC가 0000 이면 저장된 프리즈 프레임이 없다`() {
        assertNull(FreezeFrameParser.parseTriggerDtc("42 02 00 00 00\r>", "020200"))
    }

    @Test
    fun `프리즈 프레임 응답이 없으면 null 이다`() {
        assertNull(FreezeFrameParser.parseTriggerDtc("NO DATA\r>", "020200"))
    }

    // ---------- VIN ----------

    @Test
    fun `다중 프레임 VIN을 읽는다`() {
        val raw = "014\r0: 49 02 01 53 4A 4E\r1: 46 41 41 5A 45 30 55\r2: 36 30 31 32 33 34 35\r\r>"

        assertEquals("SJNFAAZE0U6012345", VinParser.parse(raw))
    }

    @Test
    fun `헤더 모드의 VIN도 읽는다`() {
        val result = VinParser.parse(
            "7E81014490201534A4E\r7E8214641415A453055\r7E82236303132333435\r>",
            headersOn = true
        )

        assertEquals("SJNFAAZE0U6012345", result)
    }

    @Test
    fun `VIN 응답이 없으면 null 이다`() {
        assertNull(VinParser.parse("NO DATA\r>"))
        assertNull(VinParser.parse(""))
    }

    @Test
    fun `VIN 은 앞 3자리와 뒤 4자리만 남기고 가린다`() {
        assertEquals("SJN**********2345", VinMasking.mask("SJNFAAZE0U6012345"))
    }

    @Test
    fun `짧은 VIN 은 전부 가린다`() {
        assertEquals("*******", VinMasking.mask("ABCDEFG"))
        assertNull(VinMasking.mask(null))
        assertNull(VinMasking.mask(""))
    }
}
