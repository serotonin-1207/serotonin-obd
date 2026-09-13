package com.eunho.leafobd.obd

import com.eunho.leafobd.data.BatteryCsv
import com.eunho.leafobd.data.DiagnosticKnowledge
import com.eunho.leafobd.data.EvidenceGrade
import com.eunho.leafobd.data.KnowledgeCatalog
import com.eunho.leafobd.data.PublicKnowledgePackCodec
import org.junit.Assert.*
import org.junit.Test

class DiagnosticIntegrityTest {
    private fun reads() = ObdMode.entries.map { mode ->
        val raw = "%02X 00".format(mode.responsePrefix)
        DtcReadOutcome(mode, com.eunho.leafobd.log.CommandLog(
            timestamp = java.time.Instant.EPOCH, command = mode.command, rawResponse = raw,
            normalizedResponse = raw, elapsedMs = 1, success = true),
            DtcParseResult(ObdResponseStatus.OK, emptyList(), raw))
    }

    @Test fun `표준 코드 재조회 실패나 잘린 응답은 소거 확정 불가다`() {
        val complete = reads()
        assertTrue(ClearVerification.complete(complete))
        assertFalse(ClearVerification.complete(complete.drop(1)))
        assertFalse(ClearVerification.complete(complete.mapIndexed { index, read ->
            if (index == 0) read.copy(result = read.result.copy(truncated = true)) else read
        }))
        assertFalse(ClearVerification.complete(complete.mapIndexed { index, read ->
            if (index == 0) read.copy(log = read.log.copy(success = false)) else read
        }))
    }

    @Test fun `삭제 전 ECU를 재조회에서 못 찾으면 소거를 확정하지 않는다`() {
        val before = DtcCode("P0300", DtcStatus.STORED, DtcSource.MODE_03, "03 00", ecu = "7E8")
        assertFalse(ClearVerification.complete(reads(), listOf(before)))
    }
    private val csv = """
        #vehicle=시험 차량
        #captured_at=2026-09-11T10:00:00+09:00
        #source=시험 장비
        #expected_channels=2
        #soc=68
        channel,voltage_v,temperature_c
        1,3.702,25
        2,3.690,26
    """.trimIndent()

    @Test fun `동일 시각 전체 채널만 편차를 계산한다`() {
        val value = BatteryCsv.parse(csv)
        assertEquals(12.0, value.spreadMv!!, 0.0001)
        assertNull(value.reportedSoh)
        assertFalse(value.demo)
    }
    @Test fun `누락 채널을 정상 값으로 채우지 않는다`() {
        val value = BatteryCsv.parse(csv.replace("2,3.690,26", "2,,26"))
        assertNull(value.spreadMv)
        assertNull(value.channels[1].volts)
    }
    @Test fun `채널 행이 빠져도 전체 편차를 계산하지 않는다`() {
        assertNull(BatteryCsv.parse(csv.substringBefore("\n2,")).spreadMv)
    }
    @Test fun `잘못된 데이터와 출처 누락은 거절한다`() {
        listOf(csv.replace("3.690", "NaN"), csv.replace("3.690", "690"),
            csv.replace("#source=시험 장비\n", ""), csv.replace("2,3.690", "1,3.690"),
            csv.replace("+09:00", ""), csv.replace("#soc=68", "#soc=101")).forEach {
            assertTrue(runCatching { BatteryCsv.parse(it) }.isFailure)
        }
    }
    @Test fun `UDS 무응답은 고장 없음이 아니다`() {
        val value = UdsDtcParser.parse("NO DATA", "7E0")
        assertFalse(value.complete)
        assertTrue(UdsDiagnosticsResult(listOf(value)).summary.contains("확인 불가"))
    }
    @Test fun `삭제 후 확인 실패 시 사라진 코드로 보고하지 않는다`() {
        val code = UdsDtcCode("P0300", 0, 8, "03 00 00 08", "7E8")
        val value = UdsClearResult(before = listOf(code))
        assertTrue(value.cleared.isEmpty())
        assertTrue(value.summary.contains("확인 불가"))
    }
    @Test fun `ECU별 동일 코드는 별개로 비교한다`() {
        val first = UdsDtcCode("P0300", 0, 8, "03 00 00 08", "7E8")
        val second = first.copy(ecu = "7E9")
        val value = UdsClearResult(before = listOf(first, second), after = listOf(second), verificationComplete = true)
        assertEquals(listOf(first), value.cleared)
        assertEquals(listOf(second), value.remaining)
    }
    @Test fun `검색은 대소문자를 구분하지 않고 미등록 코드를 만들지 않는다`() {
        assertEquals("P0300", DiagnosticKnowledge.search(" p0300 ").single().code)
        assertTrue(DiagnosticKnowledge.search("P3999").isEmpty())
        assertTrue(DiagnosticKnowledge.validCode("P3180-97"))
        assertFalse(DiagnosticKnowledge.validCode("P9999"))
    }

    @Test fun `모든 공개 해설은 출처 원장과 권리 상태를 통과한다`() {
        val status = KnowledgeCatalog.audit(DiagnosticKnowledge.entries)
        assertTrue(status.issues.joinToString(), status.issues.isEmpty())
        assertEquals(105, status.entryCount)
        assertEquals(34, status.sourceCount)
        assertEquals(102, status.commonObdEntries)
        assertEquals(3, status.manufacturerSpecificEntries)
        assertEquals(104, status.oemDocumentEntries)
        assertEquals(1, status.pendingDefinitionEntries)
    }

    @Test fun `미검증 실차 코드는 제조사 원문이 있는 것처럼 표시하지 않는다`() {
        val entry = DiagnosticKnowledge.search("P317E-97").single()
        assertEquals(EvidenceGrade.UNVERIFIED, entry.verificationGrade)
        assertNull(entry.sourceId)
        assertTrue(entry.sourceUrl.isBlank())
    }

    @Test fun `배포 APK와 같은 공개 지식 팩을 해석하고 검증한다`() {
        val bytes = checkNotNull(javaClass.classLoader?.getResourceAsStream(PublicKnowledgePackCodec.ASSET_NAME)).use { it.readBytes() }
        val pack = PublicKnowledgePackCodec.decode(bytes)
        assertEquals("2026.09.13-public.7", pack.version)
        assertEquals(105, pack.entries.size)
        assertEquals(34, pack.sources.size)
        assertTrue(pack.entries.all { it.drivingAdvice.isNotBlank() })
        assertTrue(pack.entries.all { it.ownerChecks.isNotEmpty() })
        assertTrue(pack.entries.all { it.technicianHandoff.isNotEmpty() })
        assertTrue(pack.entries.all { it.beforeClear.isNotEmpty() })
        assertEquals(2, pack.entries.count { it.urgency == com.eunho.leafobd.data.DiagnosticUrgency.STOP_AND_TOW })
    }

    @Test fun `출처를 바꾼 데이터 팩은 거부한다`() {
        val bytes = checkNotNull(javaClass.classLoader?.getResourceAsStream(PublicKnowledgePackCodec.ASSET_NAME)).use { it.readBytes() }
        val changed = bytes.toString(Charsets.UTF_8).replaceFirst(
            "https://static.nhtsa.gov/odi/tsbs/2013/SB-10060561-2273.pdf",
            "https://example.invalid/changed.pdf"
        )
        assertThrows(IllegalArgumentException::class.java) { PublicKnowledgePackCodec.decode(changed.toByteArray()) }
    }

    @Test fun `교차 검증한 네 공통 코드는 완전한 안전 안내를 가진다`() {
        val codes = setOf("P00B7", "P2118", "P0236", "P0299")
        val entries = DiagnosticKnowledge.entries.filter { it.code in codes }
        assertEquals(codes, entries.map { it.code }.toSet())
        assertTrue(entries.all { it.codeScope == com.eunho.leafobd.data.CodeScope.COMMON_OBD })
        assertTrue(entries.all { it.urgency == com.eunho.leafobd.data.DiagnosticUrgency.PROMPT_SERVICE })
        assertTrue(entries.all { it.sourceId != null && it.nextChecks.size >= 4 })
        assertTrue(entries.all { it.ownerChecks.size >= 4 && it.technicianHandoff.size >= 5 })
        assertTrue(entries.all { it.drivingAdvice.isNotBlank() && it.beforeClear.contains("프리즈 프레임") })
    }

    @Test fun `P0A0D는 과전압으로 오해하지 않도록 고전압 안전 안내를 제공한다`() {
        val entry = DiagnosticKnowledge.search("P0A0D").single()
        assertEquals(com.eunho.leafobd.data.CodeScope.COMMON_OBD, entry.codeScope)
        assertEquals(com.eunho.leafobd.data.DiagnosticUrgency.STOP_AND_TOW, entry.urgency)
        assertTrue(entry.explanation.contains("과전압") && entry.explanation.contains("인터록"))
        assertTrue(entry.drivingAdvice.contains("주황색") && entry.ownerChecks.any { it.contains("접촉하지") })
        assertTrue(entry.nextChecks.size >= 5 && entry.technicianHandoff.size >= 6)
        assertTrue(entry.limitations.contains("Digital Annex") && entry.redistribution.contains("미확보"))
    }
}
