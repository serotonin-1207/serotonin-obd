package com.eunho.leafobd.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 삭제 사전조건 검증.
 *
 * 안전과 직결되는 로직이므로 조건이 하나라도 빠지면 삭제가 막히는지 개별로 확인한다.
 */
class ClearEligibilityTest {

    private val allMet = ClearEligibility(
        connected = true,
        initialized = true,
        dtcRead = true,
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
        val default = ClearEligibility()

        assertFalse(default.eligible)
        assertEquals(6, default.unmetReasons.size)
    }

    @Test
    fun `연결되지 않으면 삭제할 수 없다`() {
        assertFalse(allMet.copy(connected = false).eligible)
    }

    @Test
    fun `초기화되지 않으면 삭제할 수 없다`() {
        assertFalse(allMet.copy(initialized = false).eligible)
    }

    @Test
    fun `코드를 읽지 않았으면 삭제할 수 없다`() {
        assertFalse(allMet.copy(dtcRead = false).eligible)
    }

    @Test
    fun `로그가 저장되지 않았으면 삭제할 수 없다`() {
        val state = allMet.copy(logSaved = false)

        assertFalse(state.eligible)
        assertTrue(state.unmetReasons.any { it.contains("저장") })
    }

    @Test
    fun `안전 확인을 하지 않으면 삭제할 수 없다`() {
        assertFalse(allMet.copy(safetyChecked = false).eligible)
    }

    @Test
    fun `확인 문구를 입력하지 않으면 삭제할 수 없다`() {
        assertFalse(allMet.copy(confirmationTyped = false).eligible)
    }

    @Test
    fun `이미 삭제했으면 다시 삭제할 수 없다`() {
        val state = allMet.copy(alreadyCleared = true)

        assertFalse(state.eligible)
        assertTrue(state.unmetReasons.any { it.contains("반복 삭제") })
    }

    @Test
    fun `확인 문구는 정확히 일치해야 한다`() {
        assertTrue(ClearConfirmation.matches("오류코드를 저장했습니다"))
        assertTrue(ClearConfirmation.matches("  오류코드를 저장했습니다  "))
        assertFalse(ClearConfirmation.matches("오류코드를 저장했습니다."))
        assertFalse(ClearConfirmation.matches("오류코드를저장했습니다"))
        assertFalse(ClearConfirmation.matches(""))
        assertFalse(ClearConfirmation.matches("저장했습니다"))
    }

    @Test
    fun `체크리스트는 3개다`() {
        assertEquals(3, ClearConfirmation.CHECKLIST.size)
        assertTrue(ClearConfirmation.SERVICE_CENTER_NOTICE.contains("주행 제한"))
        assertTrue(ClearConfirmation.SERVICE_CENTER_NOTICE.contains("후속 점검"))
    }
}
