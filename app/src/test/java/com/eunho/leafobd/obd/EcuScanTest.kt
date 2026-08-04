package com.eunho.leafobd.obd

import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.FakeElm327Transport
import com.eunho.leafobd.elm327.FakeScenario
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuScanTest {

    private fun client(scenario: FakeScenario): Elm327Client {
        val transport = FakeElm327Transport(scenario)
        transport.open()
        return Elm327Client(transport)
    }

    // ---------- 응답 해석 ----------

    @Test
    fun `정상 응답을 인식한다`() {
        val hit = EcuScanParser.parse(
            rawResponse = "7E8 02 7E 00\r\r>",
            address = "7E0",
            request = EcuProbeRequest.TESTER_PRESENT,
            headersOn = true
        )!!

        assertEquals(EcuResponseKind.POSITIVE, hit.kind)
        assertEquals("7E0", hit.requestAddress)
        assertEquals("7E8", hit.respondingId)
    }

    @Test
    fun `부정 응답도 ECU 존재로 인식한다`() {
        val hit = EcuScanParser.parse(
            rawResponse = "7E8 03 7F 22 31\r\r>",
            address = "7E0",
            request = EcuProbeRequest.READ_VIN,
            headersOn = true
        )!!

        assertEquals(EcuResponseKind.NEGATIVE, hit.kind)
        assertEquals(0x31, hit.negativeCode)
        assertTrue(hit.negativeReason!!.contains("범위"))
    }

    @Test
    fun `NO DATA 는 응답이 아니다`() {
        assertNull(
            EcuScanParser.parse("NO DATA\r>", "7E1", EcuProbeRequest.TESTER_PRESENT, true)
        )
    }

    @Test
    fun `통신 오류는 응답이 아니다`() {
        assertNull(
            EcuScanParser.parse("CAN ERROR\r>", "7E1", EcuProbeRequest.TESTER_PRESENT, true)
        )
        assertNull(
            EcuScanParser.parse("", "7E1", EcuProbeRequest.TESTER_PRESENT, true)
        )
    }

    @Test
    fun `부정 응답 코드 설명이 표준을 따른다`() {
        assertEquals("지원하지 않는 서비스", UdsNegativeResponse.describe(0x11))
        assertEquals("보안 접근이 필요함", UdsNegativeResponse.describe(0x33))
        assertTrue(UdsNegativeResponse.describe(0xAA).contains("0xAA"))
    }

    // ---------- 주소 목록 ----------

    @Test
    fun `표준 주소는 7E0부터 7E7까지 여덟 개다`() {
        assertEquals(8, EcuAddresses.STANDARD.size)
        assertEquals("7E0", EcuAddresses.STANDARD.first())
        assertEquals("7E7", EcuAddresses.STANDARD.last())
    }

    @Test
    fun `전체 주소는 진단 구간을 모두 덮는다`() {
        assertEquals(0x7EF - 0x700 + 1, EcuAddresses.FULL.size)
        assertTrue(EcuAddresses.FULL.contains("7E0"))
        assertTrue(EcuAddresses.FULL.contains("700"))
    }

    // ---------- 스캔 동작 ----------

    @Test
    fun `응답하는 주소를 찾아낸다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)

        val (_, result) = EcuScanner(client).scan(EcuAddresses.STANDARD)

        assertTrue(result.found)
        assertEquals(listOf("7E0"), result.respondingAddresses)
        assertEquals(8, result.scannedAddresses)
        client.close()
    }

    @Test
    fun `응답한 주소에만 추가 요청을 보낸다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)

        val (logs, result) = EcuScanner(client).scan(EcuAddresses.STANDARD)

        // 존재 확인 1건 + 추가 요청 2건 = 3건이 7E0 에서 나와야 한다
        assertEquals(3, result.hits.size)
        assertEquals(EcuResponseKind.POSITIVE, result.hits[0].kind)
        assertEquals(EcuResponseKind.NEGATIVE, result.hits[1].kind)
        // 추가 요청은 응답한 주소에만 보낸다 (8개 전부에 보내지 않는다)
        assertEquals(1, logs.count { it.command == "22F190" })
        client.close()
    }

    @Test
    fun `아무도 응답하지 않으면 안내 문구를 돌려준다`() = runBlocking {
        val client = client(FakeScenario.NO_DATA)

        val (_, result) = EcuScanner(client).scan(EcuAddresses.STANDARD)

        assertFalse(result.found)
        assertTrue(result.summary.contains("게이트웨이"))
        client.close()
    }

    @Test
    fun `스캔 진행 상황을 보고한다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)
        val seen = mutableListOf<EcuScanProgress>()

        EcuScanner(client).scan(EcuAddresses.STANDARD) { seen.add(it) }

        assertEquals(8, seen.size)
        assertEquals("7E0", seen.first().address)
        assertEquals(8, seen.first().total)
        client.close()
    }

    @Test
    fun `스캔이 끝나면 요청 주소를 방송으로 되돌린다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)

        val (logs, _) = EcuScanner(client).scan(EcuAddresses.STANDARD)

        assertEquals("ATSH7DF", logs.last { it.command.startsWith("ATSH") }.command)
        client.close()
    }

    @Test
    fun `쓰기나 보안 접근 명령은 보내지 않는다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)

        val (logs, _) = EcuScanner(client).scan(EcuAddresses.STANDARD)

        val obdCommands = logs.map { it.command }.filterNot { it.startsWith("AT") }
        // 27(보안 접근), 2E(쓰기), 31(루틴), 11(리셋), 10(세션 제어) 금지
        listOf("27", "2E", "31", "11", "10").forEach { forbidden ->
            assertTrue(
                "금지된 서비스 $forbidden 가 전송되었다",
                obdCommands.none { it.startsWith(forbidden) }
            )
        }
        client.close()
    }
}
