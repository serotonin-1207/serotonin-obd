package com.eunho.leafobd.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class UnknownCodeQueueTest {
    private fun record(id: String, day: Int, vararg observations: CodeObservation) = CodeHistoryRecord(
        id = id,
        time = LocalDateTime.of(2026, 9, day, 10, 15),
        observations = observations.toList()
    )
    private fun catalogRecord(id: String, day: Int, coverageId: String, coverageLabel: String, vararg observations: CodeObservation) =
        record(id, day, *observations).copy(coverageId = coverageId, coverageLabel = coverageLabel)

    @Test fun `등록 코드와 미분류 및 손상 기록을 제외한다`() {
        val result = UnknownCodeQueue.analyze(
            listOf(
                UnknownCodeInput("내 차", record("a", 1,
                    CodeObservation("P0300", "7E8", "표준", "저장"),
                    CodeObservation("P1234", "7E8", "표준", "보류"))),
                UnknownCodeInput(null, record("b", 2, CodeObservation("P2222", "7E9", "표준", "저장"))),
                UnknownCodeInput("내 차", null)
            ),
            setOf("P0300")
        )
        assertEquals(listOf("P1234"), result.candidates.map { it.code })
        assertEquals(1, result.knownCodeObservations)
        assertEquals(1, result.unclassifiedRecords)
        assertEquals(1, result.skippedRecords)
    }

    @Test fun `같은 세션 중복은 한 번으로 세고 다른 세션 반복은 합친다`() {
        val result = UnknownCodeQueue.analyze(
            listOf(
                UnknownCodeInput("차량 A", record("a", 1,
                    CodeObservation("P1234", "7E8", "최초", "저장"),
                    CodeObservation("P1234", "7E8", "삭제 전", "저장"))),
                UnknownCodeInput("차량 A", record("b", 3, CodeObservation("P1234", "7E8", "최초", "저장")))
            ), emptySet()
        )
        val candidate = result.candidates.single()
        assertEquals(2, candidate.sessionCount)
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 15), candidate.firstSeen)
        assertEquals(LocalDateTime.of(2026, 9, 3, 10, 15), candidate.lastSeen)
    }

    @Test fun `차량과 ECU가 다르면 별도 후보로 유지한다`() {
        val observations = arrayOf(CodeObservation("P1234", "7E8", "최초", "저장"))
        val result = UnknownCodeQueue.analyze(listOf(
            UnknownCodeInput("차량 A", record("a", 1, *observations)),
            UnknownCodeInput("차량 B", record("b", 1, *observations)),
            UnknownCodeInput("차량 A", record("c", 1, CodeObservation("P1234", "7E9", "최초", "저장")))
        ), emptySet())
        assertEquals(3, result.candidates.size)
    }

    @Test fun `내보내기는 별칭과 정확한 시각을 제외한다`() {
        val result = UnknownCodeQueue.analyze(listOf(
            UnknownCodeInput("은호의 비밀 차량", catalogRecord("session-secret", 1, "kr-hyundai-avante-family", "대한민국 현대 아반떼 계열",
                CodeObservation("P1234", "7E8", "최초", "저장")))
        ), emptySet())
        val text = UnknownCodeQueue.export(result)
        assertTrue(text.contains("차량 1 · 대한민국 현대 아반떼 계열"))
        assertTrue(text.contains("P1234 · ECU 7E8"))
        assertFalse(text.contains("은호의 비밀 차량"))
        assertFalse(text.contains("session-secret"))
        assertFalse(text.contains("10:15"))
    }

    @Test fun `같은 모델 계열의 여러 차량을 계열 요약으로 모은다`() {
        val result = UnknownCodeQueue.analyze(listOf(
            UnknownCodeInput("아반떼 A", catalogRecord("a", 1, "kr-hyundai-avante-family", "대한민국 현대 아반떼 계열",
                CodeObservation("P1234", "7E8", "최초", "저장")).copy(protocolIdentified = true, standardDataObserved = true, udsRespondingEcuCount = 0)),
            UnknownCodeInput("아반떼 B", catalogRecord("b", 2, "kr-hyundai-avante-family", "대한민국 현대 아반떼 계열",
                CodeObservation("P1234", "7E8", "최초", "저장"), CodeObservation("P2222", "7E8", "최초", "저장")).copy(
                protocolIdentified = false, standardDataObserved = false, udsRespondingEcuCount = 3))
        ), emptySet())
        val family = result.familySummaries.single()
        assertEquals(2, family.vehicleCount)
        assertEquals(2, family.recordCount)
        assertEquals(3, family.candidateCount)
        assertEquals(3, family.codeSessionCount)
        assertEquals(1, family.protocolObservedCount)
        assertEquals(2, family.protocolAssessedCount)
        assertEquals(1, family.standardDataObservedCount)
        assertEquals(1, family.udsResponseObservedCount)
    }

    @Test fun `기존 기록은 현재 프로필로 계열을 추정하지 않는다`() {
        val result = UnknownCodeQueue.analyze(listOf(
            UnknownCodeInput("내 차", record("old", 1, CodeObservation("P1234", "7E8", "최초", "저장")))
        ), emptySet())
        assertEquals(1, result.uncataloguedRecords)
        assertTrue(result.familySummaries.isEmpty())
        assertTrue(UnknownCodeQueue.export(result).contains("기존 기록 · 차량 계열 미저장"))
    }

    @Test fun `과도한 기록은 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            UnknownCodeQueue.analyze(List(501) { UnknownCodeInput(null, null) }, emptySet())
        }
    }
}
