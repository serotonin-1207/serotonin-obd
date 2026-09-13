package com.eunho.leafobd.log

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CodeHistoryTest {
    private fun record(id: String, day: Int, vararg codes: CodeObservation) = CodeHistoryRecord(id, LocalDateTime.of(2026, 9, day, 12, 0), codes.toList())
    private fun code(ecu: String = "7EC", value: String = "P33ED-00", phase: String = "조회") = CodeObservation(value, ecu, phase, "0x08")

    @Test fun repeatsWithinOneSessionAreNotRecurrence() {
        val text = CodeHistory.describe(listOf(record("a", 1, code(), code(phase = "삭제 후")), record("b", 2)))
        assertFalse(text.contains("2건에서 반복 수신"))
        assertTrue(text.contains("진단 1건에서 수신"))
    }
    @Test fun differentEcusAndSuffixesDoNotMerge() {
        val text = CodeHistory.describe(listOf(record("a", 1, code()), record("b", 2, code(ecu = "7E8"), code(value = "P33ED-01"))))
        assertFalse(text.contains("2건에서 반복 수신"))
    }
    @Test fun emptyIntermediateRecordDoesNotProveDisappearance() {
        val text = CodeHistory.describe(listOf(record("c", 3, code()), record("b", 2), record("a", 1, code())))
        assertTrue(text.contains("2건에서 반복 수신"))
        assertTrue(text.indexOf("2026-09-01") < text.indexOf("2026-09-03"))
        assertTrue(text.contains("표시되지 않은 시점은 코드 없음으로 간주하지 않습니다"))
    }
    @Test fun unknownEcuIsExcluded() {
        val text = CodeHistory.describe(listOf(record("a", 1, code(ecu = "미확인")), record("b", 2, code(ecu = "미확인"))))
        assertTrue(text.contains("제외한 수신 항목: 2건"))
        assertTrue(text.contains("비교 가능한 수신 코드 없음"))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateSessionsAreRejected() {
        CodeHistory.describe(listOf(record("a", 1), record("a", 1)))
    }
}
