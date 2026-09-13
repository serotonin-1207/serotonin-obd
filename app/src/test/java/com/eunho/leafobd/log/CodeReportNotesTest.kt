package com.eunho.leafobd.log

import org.junit.Assert.*
import org.junit.Test

class CodeReportNotesTest {
    @Test fun suffixMismatchDoesNotInheritAnotherFaultType() {
        val report = CodeReportNotes.create(listOf("P33ED-01"))
        assertTrue(report.contains("근거 미확보"))
        assertFalse(report.contains("배터리 교체"))
    }

    @Test fun excludesInvalidFreeTextAndDeduplicatesCodes() {
        val report = CodeReportNotes.create(listOf("PRIVATE_IDENTIFIER", "P3180-97", " p3180-97 "))
        assertFalse(report.contains("PRIVATE_IDENTIFIER"))
        assertEquals(1, Regex("(?m)^P3180-97$").findAll(report).count())
        assertTrue(report.contains("-97 정의 미확인"))
    }

    @Test fun unverifiedDefinitionDoesNotAcquireAnOemSource() {
        val report = CodeReportNotes.create(listOf("P317E-97"))
        assertTrue(report.contains("정의 미검증"))
        assertFalse(report.contains("https://"))
    }
}
