package com.eunho.leafobd.log

import org.junit.Assert.*
import org.junit.Test

class ReportComparisonTest {
    @Test fun missingRereadCannotProveClear() {
        val text = ReportComparison.describe(listOf(ReportCode("P317E-97", "79A", "0x0B")), emptyList(), false)
        assertTrue(text.contains("판정 불가"))
        assertFalse(text.contains("재조회에서 사라진 코드:"))
    }
    @Test fun sameCodeOnDifferentEcuIsComparedSeparately() {
        val before = listOf(ReportCode("P0300", "7E8", "저장"), ReportCode("P0300", "7E9", "저장"))
        val text = ReportComparison.describe(before, listOf(before[1]), true)
        assertTrue(text.contains("재조회에서 사라진 코드: 1건\n  P0300 · ECU 7E8"))
        assertTrue(text.contains("다시 확인된 코드: 1건\n  P0300 · ECU 7E9"))
    }
    @Test fun statusChangeIsNotDisappearance() {
        val before = ReportCode("P3180-97", "79A", "0x0B")
        val text = ReportComparison.describe(listOf(before), listOf(before.copy(status = "0x0A")), true)
        assertTrue(text.contains("재조회에서 사라진 코드: 0건"))
        assertTrue(text.contains("0x0B → 0x0A"))
    }
}
