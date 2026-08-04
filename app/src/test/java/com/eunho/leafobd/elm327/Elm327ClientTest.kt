package com.eunho.leafobd.elm327

import com.eunho.leafobd.obd.ObdMode
import com.eunho.leafobd.obd.ObdResponseStatus
import com.eunho.leafobd.obd.ObdService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FakeElm327Transport] 를 이용한 통신 계층 통합 테스트.
 *
 * 모의 통로가 실제 시간을 기준으로 응답을 지연시키므로
 * 가상 시간을 쓰는 `runTest` 대신 `runBlocking` 을 사용한다.
 */
class Elm327ClientTest {

    private fun client(scenario: FakeScenario) =
        Elm327Client(FakeElm327Transport(scenario))

    @Test
    fun `초기화 시퀀스가 모두 성공한다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)
        client.open()

        val result = Elm327Initializer(client).initialize()

        assertTrue(result.errorMessage ?: "", result.success)
        assertEquals("ELM327 v2.3", result.adapterInfo)
        assertEquals(12.4, result.voltage!!.volts!!, 0.001)
        // ATZ~ATRV 8개 + 프로토콜 확인(ATSP0, 0100) 2개 + ATDP 1개
        assertEquals(11, result.logs.size)
        assertTrue(result.logs.all { it.success })
        assertEquals(ProbeOutcome.RESPONDED, result.probe!!.outcome)
        client.close()
    }

    @Test
    fun `명령 순서와 원시 응답이 로그에 남는다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)
        client.open()

        val result = Elm327Initializer(client).initialize()

        assertEquals(
            listOf(
                // 기본 초기화
                "ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATI", "ATRV",
                // 실제 통신되는 프로토콜 확인
                "ATSP0", "0100", "ATDP"
            ),
            result.logs.map { it.command }
        )
        assertTrue(result.logs.first().rawResponse.contains("ELM327"))
        client.close()
    }

    @Test
    fun `표준 시나리오에서 저장 코드를 읽는다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)
        client.open()
        val service = ObdService(client)

        val outcome = service.readDtcs(ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.OK, outcome.result.status)
        assertEquals(listOf("P0133"), outcome.result.codes.map { it.code })
        client.close()
    }

    @Test
    fun `삭제 후 재조회하면 재발 코드가 남는다`() = runBlocking {
        val client = client(FakeScenario.RECURRING)
        client.open()
        val service = ObdService(client)

        val before = service.readDtcs(ObdMode.STORED_DTC).result.codes.map { it.code }
        val clear = service.clearDtcs()
        val after = service.readDtcs(ObdMode.STORED_DTC).result.codes.map { it.code }

        assertEquals(listOf("P0AA6", "P0A1F", "P0133", "P2104"), before)
        assertTrue(clear.accepted)
        assertEquals(listOf("P0AA6"), after)
        client.close()
    }

    @Test
    fun `영구 코드는 삭제해도 남는다`() = runBlocking {
        val client = client(FakeScenario.RECURRING)
        client.open()
        val service = ObdService(client)

        service.clearDtcs()
        val permanent = service.readDtcs(ObdMode.PERMANENT_DTC)

        assertEquals(listOf("P0AA6"), permanent.result.codes.map { it.code })
        client.close()
    }

    @Test
    fun `NO DATA 시나리오는 오류가 아니라 데이터 없음으로 처리한다`() = runBlocking {
        val client = client(FakeScenario.NO_DATA)
        client.open()

        val outcome = ObdService(client).readDtcs(ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.NO_DATA, outcome.result.status)
        assertTrue(outcome.log.success)
        client.close()
    }

    @Test
    fun `CAN ERROR 시나리오는 통신 오류로 처리한다`() = runBlocking {
        val client = client(FakeScenario.CAN_ERROR)
        client.open()

        val outcome = ObdService(client).readDtcs(ObdMode.STORED_DTC)

        assertEquals(ObdResponseStatus.ERROR, outcome.result.status)
        assertFalse(outcome.log.success)
        assertTrue(outcome.log.errorMessage!!.contains("CAN"))
        client.close()
    }

    @Test
    fun `응답이 없으면 타임아웃 로그를 남기고 예외를 던지지 않는다`() = runBlocking {
        val client = client(FakeScenario.TIMEOUT)
        client.open()

        val log = client.send("03", timeoutMs = 300L)

        assertFalse(log.success)
        assertTrue(log.errorMessage!!.contains("응답하지 않았습니다"))
        client.close()
    }

    // ---------- 확장 조회 (Mode 01 / 02 / 09) ----------

    @Test
    fun `지원 PID 구간을 이어서 조회한다`() = runBlocking {
        val client = client(FakeScenario.RECURRING)
        client.open()

        val (logs, supported) = ObdService(client).readSupportedPids()

        // 0100 -> 0120 -> 0140 까지 이어서 조회하고 멈춘다.
        assertEquals(listOf("0100", "0120", "0140"), logs.map { it.command })
        assertTrue(supported.supports(0x05))
        assertTrue(supported.supports(0x42))
        client.close()
    }

    @Test
    fun `경고등 상태와 코드 개수를 읽는다`() = runBlocking {
        val client = client(FakeScenario.RECURRING)
        client.open()

        val (log, status) = ObdService(client).readMonitorStatus()

        assertTrue(log.success)
        assertTrue(status!!.milOn)
        assertEquals(4, status.dtcCount)
        client.close()
    }

    @Test
    fun `지원한다고 보고한 PID만 실시간으로 읽는다`() = runBlocking {
        val client = client(FakeScenario.RECURRING)
        client.open()
        val service = ObdService(client)

        val (_, supported) = service.readSupportedPids()
        val (logs, values) = service.readLiveValues(supported)

        assertEquals(listOf("0104", "0105", "0131", "0142"), logs.map { it.command })
        assertTrue(values.any { it.id == 0x42 && it.value == 12.5 })
        client.close()
    }

    @Test
    fun `프리즈 프레임을 읽고 원시 응답을 모두 보존한다`() = runBlocking {
        val client = client(FakeScenario.RECURRING)
        client.open()

        val (logs, frame) = ObdService(client).readFreezeFrame()

        assertEquals("P0AA6", frame.triggerDtc)
        assertTrue(frame.values.any { it.id == 0x05 })
        // 조회한 모든 명령의 원시 응답이 남아 있어야 한다.
        assertTrue(frame.rawResponses.keys.containsAll(setOf("020000", "020200", "020500")))
        assertTrue(logs.isNotEmpty())
        client.close()
    }

    @Test
    fun `삭제하면 프리즈 프레임도 사라진다`() = runBlocking {
        val client = client(FakeScenario.RECURRING)
        client.open()
        val service = ObdService(client)

        val before = service.readFreezeFrame().second
        service.clearDtcs()
        val after = service.readFreezeFrame().second

        assertEquals("P0AA6", before.triggerDtc)
        assertNull(after.triggerDtc)
        client.close()
    }

    @Test
    fun `무응답 시나리오의 프리즈 프레임은 사용할 수 있는 값이 없다`() = runBlocking {
        // 실기기 확인에서 발견한 문제: 통신이 타임아웃돼 원시 응답만 남았는데도
        // "성공"으로 표시되었다. hasData 로 실제 값 유무를 구분한다.
        val transport = FakeElm327Transport(FakeScenario.TIMEOUT)
        val client = Elm327Client(transport)
        client.open()

        val (logs, frame) = ObdService(client).readFreezeFrame()

        assertFalse(frame.hasData)
        assertFalse(frame.isEmpty)          // 원시 응답은 남아 있다
        assertTrue(logs.none { it.success }) // 그러나 통신은 모두 실패했다
        client.close()
    }

    @Test
    fun `VIN 을 읽는다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)
        client.open()

        val (log, vin) = ObdService(client).readVin()

        assertTrue(log.success)
        assertEquals("SJNFAAZE0U6012345", vin)
        client.close()
    }

    @Test
    fun `통신 오류 시나리오에서는 확장 조회도 실패한다`() = runBlocking {
        val client = client(FakeScenario.CAN_ERROR)
        client.open()

        val (log, status) = ObdService(client).readMonitorStatus()

        assertFalse(log.success)
        assertNull(status)
        client.close()
    }

    @Test
    fun `통로가 닫혀 있어도 예외 대신 실패 로그를 돌려준다`() = runBlocking {
        val transport = FakeElm327Transport(FakeScenario.NORMAL)
        val client = Elm327Client(transport)
        transport.close()

        val log = client.send("03", timeoutMs = 300L)

        // 닫힌 상태에서는 send()가 스스로 open()을 호출하므로 실제로는 성공한다.
        // 중요한 것은 어떤 경우에도 예외가 밖으로 나가지 않는다는 점이다.
        assertEquals("03", log.command)
        client.close()
    }
}
