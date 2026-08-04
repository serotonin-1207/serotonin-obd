package com.eunho.leafobd.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTC 파서 단위 테스트.
 *
 * 지시서 10장의 "최소 테스트 케이스" 목록을 모두 덮는다.
 */
class DtcParserTest {

    // ---------- 2바이트 → 표준 코드 변환 ----------

    @Test
    fun `첫 바이트 상위 2비트가 계통 문자를 결정한다`() {
        assertEquals("P0133", DtcParser.decode(0x01, 0x33))
        assertEquals("C0133", DtcParser.decode(0x41, 0x33))
        assertEquals("B0133", DtcParser.decode(0x81, 0x33))
        assertEquals("U0133", DtcParser.decode(0xC1, 0x33))
    }

    @Test
    fun `첫 바이트 다음 2비트가 첫 번째 숫자가 된다`() {
        assertEquals("P0000", DtcParser.decode(0x00, 0x00))
        assertEquals("P1234", DtcParser.decode(0x12, 0x34))
        assertEquals("P2104", DtcParser.decode(0x21, 0x04))
        assertEquals("P3FFF", DtcParser.decode(0x3F, 0xFF))
    }

    // ---------- 정상 응답 ----------

    @Test
    fun `정상 단일 코드를 읽는다`() {
        val result = DtcParser.parse("4301330000\r\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(1, result.codes.size)
        assertEquals("P0133", result.codes[0].code)
        assertEquals(DtcStatus.STORED, result.codes[0].status)
        assertEquals(DtcSource.MODE_03, result.codes[0].source)
        assertEquals("01 33", result.codes[0].rawBytes)
    }

    @Test
    fun `CAN 형식의 개수 바이트가 있는 다중 코드를 읽는다`() {
        // 43 04 = Mode 03 응답 + DTC 4건
        val result = DtcParser.parse("43040AA60A1F01332104\r\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(
            listOf("P0AA6", "P0A1F", "P0133", "P2104"),
            result.codes.map { it.code }
        )
    }

    @Test
    fun `개수 바이트가 없는 형식도 읽는다`() {
        // ISO 9141 / KWP 형식: 개수 바이트 없이 DTC가 바로 이어진다.
        val result = DtcParser.parse("4301330245\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(listOf("P0133", "P0245"), result.codes.map { it.code })
    }

    @Test
    fun `0000 패딩은 코드로 취급하지 않는다`() {
        val result = DtcParser.parse("43013300000000\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(1, result.codes.size)
        assertEquals("P0133", result.codes[0].code)
    }

    @Test
    fun `코드가 하나도 없으면 빈 목록과 안내 문구를 돌려준다`() {
        val result = DtcParser.parse("470000\r>", ObdMode.PENDING_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertTrue(result.codes.isEmpty())
        assertTrue(result.message!!.contains("검출된 코드가 없습니다"))
    }

    // ---------- 잡음 처리 ----------

    @Test
    fun `명령 echo를 제거한다`() {
        val result = DtcParser.parse("03\r4301330000\r\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(listOf("P0133"), result.codes.map { it.code })
    }

    @Test
    fun `SEARCHING 표시를 제거한다`() {
        val result = DtcParser.parse("SEARCHING...\r43 01 33 00 00\r\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(listOf("P0133"), result.codes.map { it.code })
    }

    @Test
    fun `공백이 섞여 있어도 읽는다`() {
        val result = DtcParser.parse("43 01 33 00 00\r>", ObdMode.STORED_DTC)

        assertEquals(listOf("P0133"), result.codes.map { it.code })
    }

    @Test
    fun `CRLF가 섞여 있어도 읽는다`() {
        val result = DtcParser.parse("\r\n43 02 01 33 02 45\r\n\r\n>", ObdMode.STORED_DTC)

        assertEquals(listOf("P0133", "P0245"), result.codes.map { it.code })
    }

    @Test
    fun `다중 프레임 응답의 프레임 번호와 길이 표시를 제거한다`() {
        val raw = "00A\r0: 43 04 01 33 02 45\r1: 03 21 04 56\r\r>"
        val result = DtcParser.parse(raw, ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(
            listOf("P0133", "P0245", "P0321", "P0456"),
            result.codes.map { it.code }
        )
    }

    // ---------- 비정상 응답 ----------

    @Test
    fun `빈 응답은 EMPTY로 처리한다`() {
        val result = DtcParser.parse("", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.EMPTY, result.status)
        assertTrue(result.codes.isEmpty())
    }

    @Test
    fun `프롬프트만 온 응답은 EMPTY로 처리한다`() {
        val result = DtcParser.parse("\r\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.EMPTY, result.status)
    }

    @Test
    fun `NO DATA는 오류가 아니라 데이터 없음으로 처리한다`() {
        val result = DtcParser.parse("NO DATA\r\r>", ObdMode.PERMANENT_DTC)

        assertEquals(ObdResponseStatus.NO_DATA, result.status)
        assertTrue(result.codes.isEmpty())
        assertTrue(result.message!!.contains("데이터 없음"))
    }

    @Test
    fun `물음표 응답은 지원되지 않음으로 처리한다`() {
        val result = DtcParser.parse("?\r\r>", ObdMode.PERMANENT_DTC)

        assertEquals(ObdResponseStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun `CAN ERROR는 통신 오류로 처리한다`() {
        val result = DtcParser.parse("CAN ERROR\r\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.ERROR, result.status)
        assertTrue(result.message!!.contains("CAN"))
    }

    @Test
    fun `UNABLE TO CONNECT는 통신 오류로 처리한다`() {
        val result = DtcParser.parse("UNABLE TO CONNECT\r\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.ERROR, result.status)
    }

    @Test
    fun `잘린 응답은 truncated로 표시한다`() {
        // 43 05 01 33 : 개수 바이트로 보기엔 길이가 맞지 않고, 남은 바이트 수가 홀수다.
        val result = DtcParser.parse("43050133\r>", ObdMode.STORED_DTC)

        assertTrue(result.truncated)
        assertTrue(result.message!!.contains("잘린"))
    }

    @Test
    fun `홀수 길이 16진 문자열에도 예외가 발생하지 않는다`() {
        val result = DtcParser.parse("4301333\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, result.status)
        assertEquals(listOf("P0133"), result.codes.map { it.code })
    }

    @Test
    fun `16진수가 아닌 응답은 EMPTY로 처리한다`() {
        val result = DtcParser.parse("STOPPED-XYZ\r>", ObdMode.STORED_DTC)

        assertTrue(result.status == ObdResponseStatus.ERROR || result.status == ObdResponseStatus.EMPTY)
        assertTrue(result.codes.isEmpty())
    }

    // ---------- 모드 구분 ----------

    @Test
    fun `모드가 다르면 응답 머리값이 맞지 않아 코드를 만들지 않는다`() {
        val result = DtcParser.parse("470000\r>", ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.EMPTY, result.status)
        assertTrue(result.codes.isEmpty())
    }

    @Test
    fun `Mode 07 응답은 보류 상태로 표시한다`() {
        val result = DtcParser.parse("47010133\r>", ObdMode.PENDING_DTC)

        assertEquals(1, result.codes.size)
        assertEquals(DtcStatus.PENDING, result.codes[0].status)
        assertEquals(DtcSource.MODE_07, result.codes[0].source)
    }

    @Test
    fun `Mode 0A 응답은 영구 상태로 표시한다`() {
        val result = DtcParser.parse("4A010AA6\r>", ObdMode.PERMANENT_DTC)

        assertEquals(1, result.codes.size)
        assertEquals("P0AA6", result.codes[0].code)
        assertEquals(DtcStatus.PERMANENT, result.codes[0].status)
        assertEquals(DtcSource.MODE_0A, result.codes[0].source)
    }
}
