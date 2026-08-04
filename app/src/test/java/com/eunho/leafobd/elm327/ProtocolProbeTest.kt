package com.eunho.leafobd.elm327

import com.eunho.leafobd.obd.ObdProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 프로토콜 탐색 테스트.
 *
 * 실제 차량에서 겪은 증상(자동 선택 실패 → UNABLE TO CONNECT 반복)을
 * [FakeScenario.PROTOCOL_SEARCH_FAIL] 로 재현해 검증한다.
 */
class ProtocolProbeTest {

    private fun client(scenario: FakeScenario): Elm327Client {
        val transport = FakeElm327Transport(scenario)
        transport.open()
        return Elm327Client(transport)
    }

    @Test
    fun `자동 선택으로 통신되면 그대로 사용한다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)

        val result = ProtocolProbe(client).probe(ObdProtocol.AUTO)

        assertEquals(ProbeOutcome.RESPONDED, result.outcome)
        assertEquals(ObdProtocol.AUTO, result.protocol)
        assertEquals(1, result.attempted.size)
        assertFalse(result.foundBySweep)
        client.close()
    }

    @Test
    fun `자동 선택이 실패하면 후보를 순서대로 시도해 찾아낸다`() = runBlocking {
        val client = client(FakeScenario.PROTOCOL_SEARCH_FAIL)

        val result = ProtocolProbe(client).probe(ObdProtocol.AUTO, sweepOnFailure = true)

        assertEquals(ProbeOutcome.RESPONDED, result.outcome)
        assertEquals(ObdProtocol.CAN_11B_500K, result.protocol)
        assertTrue(result.foundBySweep)
        // 자동 → CAN 11bit/500k 순으로 시도했다
        assertEquals(
            listOf(ObdProtocol.AUTO, ObdProtocol.CAN_11B_500K),
            result.attempted.map { it.first }
        )
        assertEquals(ProbeOutcome.FAILED, result.attempted[0].second)
        client.close()
    }

    @Test
    fun `프로토콜을 직접 지정하면 자동 선택을 건너뛴다`() = runBlocking {
        val client = client(FakeScenario.PROTOCOL_SEARCH_FAIL)

        val result = ProtocolProbe(client).probe(ObdProtocol.CAN_11B_500K)

        assertEquals(ProbeOutcome.RESPONDED, result.outcome)
        assertEquals(ObdProtocol.CAN_11B_500K, result.protocol)
        assertEquals(1, result.attempted.size)
        assertFalse(result.foundBySweep)
        client.close()
    }

    @Test
    fun `순차 시도를 끄면 자동 선택 실패에서 멈춘다`() = runBlocking {
        val client = client(FakeScenario.PROTOCOL_SEARCH_FAIL)

        val result = ProtocolProbe(client).probe(ObdProtocol.AUTO, sweepOnFailure = false)

        assertEquals(ProbeOutcome.FAILED, result.outcome)
        assertNull(result.protocol)
        assertEquals(1, result.attempted.size)
        client.close()
    }

    @Test
    fun `모든 프로토콜이 실패하면 실패로 보고한다`() = runBlocking {
        val client = client(FakeScenario.CAN_ERROR)

        val result = ProtocolProbe(client).probe(ObdProtocol.AUTO)

        assertEquals(ProbeOutcome.FAILED, result.outcome)
        assertNull(result.protocol)
        // 자동 + 후보 5개
        assertEquals(1 + ObdProtocol.SWEEP_CANDIDATES.size, result.attempted.size)
        client.close()
    }

    @Test
    fun `NO DATA 는 버스 연결됨으로 구분한다`() = runBlocking {
        val client = client(FakeScenario.NO_DATA)

        val result = ProtocolProbe(client).probe(ObdProtocol.AUTO)

        // 연결은 되었으나 데이터가 없는 상태 — 통신 실패와 구분해야 한다
        assertEquals(ProbeOutcome.CONNECTED_NO_DATA, result.outcome)
        assertEquals(ObdProtocol.AUTO, result.protocol)
        client.close()
    }

    @Test
    fun `초기화가 프로토콜 탐색 결과를 함께 돌려준다`() = runBlocking {
        val client = client(FakeScenario.PROTOCOL_SEARCH_FAIL)

        val result = Elm327Initializer(client, protocol = ObdProtocol.AUTO).initialize()

        assertTrue(result.success)
        assertEquals(ObdProtocol.CAN_11B_500K, result.probe?.protocol)
        assertTrue(result.probe!!.foundBySweep)
        client.close()
    }

    @Test
    fun `지정한 프로토콜이 초기화 명령에 반영된다`() = runBlocking {
        val client = client(FakeScenario.NORMAL)

        val result = Elm327Initializer(client, protocol = ObdProtocol.CAN_11B_500K).initialize()

        assertTrue(result.logs.any { it.command == "ATSP6" })
        assertFalse(result.logs.take(8).any { it.command == "ATSP0" })
        client.close()
    }

    @Test
    fun `프로토콜 번호와 명령이 표준과 일치한다`() {
        assertEquals("ATSP0", ObdProtocol.AUTO.setCommand)
        assertEquals("ATSP6", ObdProtocol.CAN_11B_500K.setCommand)
        assertEquals("ATSPA", ObdProtocol.J1939.setCommand)
        assertEquals(ObdProtocol.CAN_11B_500K, ObdProtocol.ofCode("6"))
        assertEquals(ObdProtocol.J1939, ObdProtocol.ofCode("a"))
        assertNull(ObdProtocol.ofCode("Z"))
    }
}
