package com.eunho.leafobd.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UDS 오류코드 파서 테스트.
 *
 * 실제 2019 Leaf 에서 받은 응답을 그대로 검증 자료로 쓴다.
 */
class UdsDtcParserTest {

    // ---------- 실차 응답 ----------

    @Test
    fun `실차 7BB 단일 프레임 응답을 해석한다`() {
        // 2026-08-04 실제 수집: 59 02 0E 33 ED 00 0C
        val result = UdsDtcParser.parse("7BB0759020E33ED000C\r\r>", "7BB")

        assertEquals("7BB", result.ecu)
        assertEquals(1, result.codes.size)
        assertEquals("P33ED", result.codes[0].code)
        assertEquals(0x00, result.codes[0].failureType)
        assertEquals("P33ED-00", result.codes[0].fullCode)
        assertEquals(0x0E, result.statusAvailabilityMask)
        assertFalse(result.truncated)
    }

    @Test
    fun `코드가 없는 응답을 구분한다`() {
        // 59 02 09 만 있고 DTC 없음
        val result = UdsDtcParser.parse("70F03590209\r>", "70F")

        assertFalse(result.hasCodes)
        assertEquals(0x09, result.statusAvailabilityMask)
        assertTrue(result.message!!.contains("없습니다"))
    }

    @Test
    fun `다중 프레임이 잘리면 truncated로 표시한다`() {
        // 첫 프레임만 온 경우: 10 0B 59 02 0F 31 80 97 (상태 바이트가 없다)
        val result = UdsDtcParser.parse("79A100B59020F318097\r>", "79A")

        assertTrue(result.truncated)
        assertTrue(result.message!!.contains("끝까지 오지 않았습니다"))
    }

    @Test
    fun `완성된 다중 프레임에서 여러 코드를 읽는다`() {
        // 79A 응답이 흐름 제어로 완성된 경우
        //   첫 프레임 10 0B : 전체 11바이트, 데이터 59 02 0F 31 80 97
        //   연속 프레임 21  : 나머지 09 31 81 98 08
        //   => 59 02 0F | 31 80 97 09 | 31 81 98 08  (DTC 2건)
        val raw = "79A100B59020F318097\r79A210931819808\r>"

        val result = UdsDtcParser.parse(raw, "79A")

        assertEquals(2, result.codes.size)
        assertEquals("P3180", result.codes[0].code)
        assertEquals(0x97, result.codes[0].failureType)
        assertEquals(0x09, result.codes[0].statusByte)
        assertEquals("P3181", result.codes[1].code)
        assertFalse(result.truncated)
    }

    @Test
    fun `연속 프레임이 유실되면 그 앞까지만 해석한다`() {
        // 2026-08-04 실차에서 실제로 발생: 순번 25 프레임이 빠졌다.
        // 유실 지점 뒤 데이터는 정렬이 어긋나 엉뚱한 코드가 만들어진다.
        val raw = listOf(
            "7641033590219A63011",   // 10 33 : 전체 51바이트
            "7642110A6311510A7A0",   // 21
            "764227910A7A17910A7",   // 22
            "76423A27910A7A37910",   // 23
            "76424A7A47910A7A579",   // 24
            "764267910A7A87910A7",   // 26  ← 25 누락
            "76427A97910FFFFFFFF"    // 27
        ).joinToString("\r") + "\r>"

        val result = UdsDtcParser.parse(raw, "764")

        assertTrue("프레임 유실을 감지해야 한다", result.frameGap)
        assertTrue(result.needsRetry)
        assertTrue(result.message!!.contains("유실"))

        // 유실 지점 앞의 코드만 나와야 한다
        assertEquals(
            listOf("B2630", "B2631", "B27A0", "B27A1", "B27A2", "B27A3", "B27A4"),
            result.codes.map { it.code }
        )
        // 정렬이 어긋나 생기던 가짜 코드가 없어야 한다
        assertFalse(result.codes.any { it.code == "P10A7" })
        assertFalse(result.codes.any { it.code == "P10FF" })
    }

    @Test
    fun `프레임이 모두 오면 유실로 표시하지 않는다`() {
        val raw = "79A100B59020F318097\r79A210B317E970B0000\r>"

        val result = UdsDtcParser.parse(raw, "79A")

        assertFalse(result.frameGap)
        assertFalse(result.needsRetry)
        assertEquals(2, result.codes.size)
    }

    @Test
    fun `선언 길이보다 적게 오면 재시도 대상이다`() {
        // 10 0B = 11바이트라고 했는데 첫 프레임만 왔다
        val result = UdsDtcParser.parse("79A100B59020F318097\r>", "79A")

        assertTrue(result.needsRetry)
        assertEquals(11, result.declaredLength)
        assertEquals(6, result.receivedLength)
    }

    // ---------- 코드 변환 ----------

    @Test
    fun `3바이트 DTC 를 표준 문자 코드로 바꾼다`() {
        assertEquals("P33ED", UdsDtcParser.decode(0x33, 0xED))
        assertEquals("P0AA6", UdsDtcParser.decode(0x0A, 0xA6))
        assertEquals("P31E7", UdsDtcParser.decode(0x31, 0xE7))
        assertEquals("B2630", UdsDtcParser.decode(0xA6, 0x30))
    }

    // ---------- 상태 비트 ----------

    @Test
    fun `상태 비트를 한국어로 푼다`() {
        val code = UdsDtcCode("P33ED", 0x00, 0x0C, "33 ED 00 0C")

        assertTrue(code.confirmed)
        assertFalse(code.currentlyFailing)
        assertTrue(code.statusLabels.contains("보류 중"))
        assertTrue(code.statusLabels.contains("확정됨"))
    }

    @Test
    fun `현재 고장 상태를 구분한다`() {
        val active = UdsDtcCode("P0AA6", 0x1A, 0x09, "0A A6 1A 09")

        assertTrue(active.currentlyFailing)
        assertTrue(active.confirmed)
        assertTrue(active.statusLabels.contains("현재 고장 상태"))
    }

    @Test
    fun `경고등 점등 요청 비트를 읽는다`() {
        val code = UdsDtcCode("P0AA6", 0x00, 0x80, "0A A6 00 80")

        assertTrue(code.statusLabels.contains("경고등 점등 요청"))
    }

    @Test
    fun `계통 문자를 분류한다`() {
        assertEquals("파워트레인", UdsDtcCode("P33ED", 0, 0, "").systemLabel)
        assertEquals("바디", UdsDtcCode("B2630", 0, 0, "").systemLabel)
        assertEquals("네트워크/통신", UdsDtcCode("U1000", 0, 0, "").systemLabel)
    }

    // ---------- 비정상 응답 ----------

    @Test
    fun `NO DATA 는 코드가 없다`() {
        val result = UdsDtcParser.parse("NO DATA\r>", "7E0")

        assertFalse(result.hasCodes)
        assertEquals("응답 없음", result.message)
    }

    @Test
    fun `부정 응답은 오류코드 응답이 아니다`() {
        val result = UdsDtcParser.parse("72D037F1911\r>", "72D")

        assertFalse(result.hasCodes)
        assertTrue(result.message!!.contains("오류코드 응답이 아닙니다"))
    }

    // ---------- VIN ----------

    @Test
    fun `UDS 차대번호 응답을 읽는다`() {
        // 62 F1 90 + ASCII
        val raw = "700101462F1905341\r70021534A4E46414\r7002241415A453055\r>"

        val vin = UdsVinParser.parse(raw)

        assertTrue(vin!!.startsWith("SA"))
    }

    @Test
    fun `VIN 응답이 아니면 null 이다`() {
        assertNull(UdsVinParser.parse("700037F2231\r>"))
        assertNull(UdsVinParser.parse("NO DATA\r>"))
    }
}
