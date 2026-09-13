package com.eunho.leafobd.obd

import com.eunho.leafobd.viewmodel.UdsClearConfirmation
import com.eunho.leafobd.viewmodel.UdsClearEligibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsClearTest {

    // ---------- 사전조건 ----------

    private val allMet = UdsClearEligibility(
        connected = true,
        ecuFound = true,
        codesRead = true,
        logSaved = true,
        safetyChecked = true,
        confirmationTyped = true,
        alreadyCleared = false
    )

    @Test
    fun `모든 조건이 충족되면 삭제할 수 있다`() {
        assertTrue(allMet.eligible)
        assertTrue(allMet.unmetReasons.isEmpty())
    }

    @Test
    fun `기본값은 삭제 불가다`() {
        val default = UdsClearEligibility()

        assertFalse(default.eligible)
        assertEquals(6, default.unmetReasons.size)
    }

    @Test
    fun `읽기 전이면 삭제할 수 없다`() {
        assertFalse(allMet.copy(codesRead = false).eligible)
    }

    @Test
    fun `저장 전이면 삭제할 수 없다`() {
        val state = allMet.copy(logSaved = false)

        assertFalse(state.eligible)
        assertTrue(state.unmetReasons.any { it.contains("저장") })
    }

    @Test
    fun `이미 삭제했으면 다시 삭제할 수 없다`() {
        val state = allMet.copy(alreadyCleared = true)

        assertFalse(state.eligible)
        assertTrue(state.unmetReasons.any { it.contains("반복 삭제") })
    }

    @Test
    fun `확인 문구는 정확히 일치해야 한다`() {
        assertTrue(UdsClearConfirmation.matches("오류코드를 저장했습니다"))
        assertTrue(UdsClearConfirmation.matches("  오류코드를 저장했습니다  "))
        assertFalse(UdsClearConfirmation.matches("오류코드를 저장했습니다."))
        assertFalse(UdsClearConfirmation.matches(""))
    }

    @Test
    fun `경고 문구가 삭제와 수리를 구분한다`() {
        assertTrue(UdsClearConfirmation.WARNING.contains("삭제는 수리가 아닙니다"))
        assertTrue(UdsClearConfirmation.WARNING.contains("고장 감지 기능을 끄는 것이 아닙니다"))
        assertTrue(UdsClearConfirmation.SERVICE_CENTER_NOTICE.contains("서비스센터"))
        assertTrue(UdsClearConfirmation.SERVICE_CENTER_NOTICE.contains("다시 읽어"))
        assertEquals(4, UdsClearConfirmation.CHECKLIST.size)
    }

    // ---------- 전후 비교 ----------

    private fun code(value: String, ecu: String = "79A") =
        UdsDtcCode(value, 0x97, 0x0B, "31 80 97 0B", ecu)

    @Test
    fun `삭제 후 남은 코드를 재발로 분류한다`() {
        val result = UdsClearResult(verificationComplete = true,
            before = listOf(code("P3180"), code("P317E")),
            after = listOf(code("P3180"))
        )

        assertEquals(listOf("P317E-97"), result.cleared.map { it.fullCode })
        assertEquals(listOf("P3180-97"), result.remaining.map { it.fullCode })
        assertTrue(result.hasRecurrence)
        // 상태 0x0B 는 현재 고장 비트가 켜져 있다
        assertEquals(listOf("P3180-97"), result.stillFailing.map { it.fullCode })
        assertTrue(result.summary.contains("현재 고장 상태"))
    }

    @Test
    fun `코드가 남아도 현재 고장이 아니면 구분한다`() {
        // 삭제 후 상태가 0x0A 로 바뀌면 현재 고장 비트가 꺼진 것이다.
        val after = UdsDtcCode("P3180", 0x97, 0x0A, "31 80 97 0A", "79A")
        val result = UdsClearResult(verificationComplete = true, before = listOf(code("P3180")), after = listOf(after))

        assertTrue(result.remaining.isNotEmpty())
        assertTrue(result.stillFailing.isEmpty())
        assertTrue(result.summary.contains("현재 고장 상태는 아닙니다"))
        // "반복해도 소용없다"는 안내는 현재 고장일 때만 나와야 한다
        assertFalse(result.summary.contains("반복해서 지워도"))
    }

    @Test
    fun `상태 비트 변화를 잡아낸다`() {
        // 2026-08-04 실차: 삭제 후 P3180 이 0B -> 0A 로 바뀌었다.
        val after = UdsDtcCode("P3180", 0x97, 0x0A, "31 80 97 0A", "79A")
        val result = UdsClearResult(verificationComplete = true, before = listOf(code("P3180")), after = listOf(after))

        assertEquals(1, result.statusChanged.size)
        val (changed, was, now) = result.statusChanged[0]
        assertEquals("P3180-97", changed.fullCode)
        assertEquals(0x0B, was)
        assertEquals(0x0A, now)
    }

    @Test
    fun `모두 사라지면 재발이 아니다`() {
        val result = UdsClearResult(verificationComplete = true,
            before = listOf(code("P3180")),
            after = emptyList()
        )

        assertFalse(result.hasRecurrence)
        assertEquals(1, result.cleared.size)
        // 그래도 원인이 남아 있을 수 있다는 점을 안내한다
        assertTrue(result.summary.contains("다시 나타납니다"))
    }

    @Test
    fun `새로 나타난 코드를 구분한다`() {
        val result = UdsClearResult(verificationComplete = true,
            before = listOf(code("P3180")),
            after = listOf(code("P33ED", ecu = "7BB"))
        )

        assertEquals(listOf("P33ED-97"), result.appeared.map { it.fullCode })
        assertTrue(result.hasRecurrence)
    }
}
