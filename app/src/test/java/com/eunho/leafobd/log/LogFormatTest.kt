package com.eunho.leafobd.log

import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.obd.DtcSource
import com.eunho.leafobd.obd.DtcStatus
import com.eunho.leafobd.util.Json
import com.eunho.leafobd.util.MacMasking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class LogFormatTest {
    @Test fun batteryReadDoesNotReportNoDtcs() {
        val s = session().copy(batteryOnly = true)
        assertTrue(SessionFormatter.toText(s).contains("오류코드 미조회"))
        assertFalse(SessionFormatter.toText(s).contains("저장 DTC (Mode 03): 없음"))
        assertTrue(SessionFormatter.toClipboardSummary(s).contains("오류코드 미조회"))
        assertTrue(SessionFormatter.toJson(s).contains("\"batteryOnly\": true"))
    }

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")

    private val startedAt: Instant =
        ZonedDateTime.of(2026, 7, 29, 22, 35, 0, 0, zone).toInstant()

    private fun code(value: String, status: DtcStatus, source: DtcSource) = DtcCode(
        code = value,
        status = status,
        source = source,
        rawBytes = "01 33"
    )

    private fun session(
        clearAttempted: Boolean = false,
        after: List<DtcCode> = emptyList()
    ) = DiagnosticSession(
        id = "test-session",
        startedAt = startedAt,
        deviceName = "V-LINK",
        deviceAddressMasked = MacMasking.mask("00:1D:A5:68:98:8B"),
        adapterInfo = "ELM327 v2.3",
        adapterVoltage = "12.4 V",
        protocol = "ISO 15765-4 (CAN 11/500)",
        commands = listOf(
            CommandLog(
                timestamp = startedAt,
                command = "03",
                rawResponse = "43 01 33 00 00\r\r>",
                normalizedResponse = "43 01 33 00 00",
                elapsedMs = 120,
                success = true
            )
        ),
        dtcBeforeClear = listOf(
            code("P0133", DtcStatus.STORED, DtcSource.MODE_03),
            code("P0AA6", DtcStatus.PERMANENT, DtcSource.MODE_0A)
        ),
        clearAttempted = clearAttempted,
        clearVerificationComplete = clearAttempted,
        clearResponse = if (clearAttempted) "44" else null,
        dtcAfterClear = after,
        appVersion = "1.0",
        androidVersion = "Android 14 (API 34)"
    )

    // ---------- MAC 마스킹 ----------

    @Test
    fun `MAC 주소는 마지막 2바이트만 남긴다`() {
        assertEquals("**:**:**:**:98:8B", MacMasking.mask("00:1D:A5:68:98:8B"))
    }

    @Test
    fun `빈 주소는 null 이다`() {
        assertEquals(null, MacMasking.mask(null))
        assertEquals(null, MacMasking.mask(""))
    }

    // ---------- 파일명 ----------

    @Test
    fun `파일명은 LeafOBD_날짜_시각 형식이다`() {
        assertEquals("SerotoninOBD_2026-07-29_223500", SessionFormatter.fileBaseName(session(), zone))
    }

    // ---------- 텍스트 ----------

    @Test
    fun `텍스트에 전체 MAC 주소가 들어가지 않는다`() {
        val text = SessionFormatter.toText(session(), zone)

        assertFalse(text.contains("00:1D:A5:68:98:8B"))
        assertTrue(text.contains("**:**:**:**:98:8B"))
    }

    @Test
    fun `텍스트에 원시 응답과 안전 문구가 들어간다`() {
        val text = SessionFormatter.toText(session(), zone)

        assertTrue(text.contains("[Serotonin OBD 진단 결과]"))
        assertTrue(text.contains("43 01 33 00 00"))
        assertTrue(text.contains("P0133"))
        assertTrue(text.contains("정비소의 전문 진단을 대체하지 않습니다"))
    }

    @Test
    fun `삭제 후 재발 코드가 있으면 경고 문구를 남긴다`() {
        val recurring = session(
            clearAttempted = true,
            after = listOf(code("P0133", DtcStatus.STORED, DtcSource.MODE_03))
        )

        val text = SessionFormatter.toText(recurring, zone)

        assertTrue(text.contains("활성 고장일 수 있습니다"))
        assertEquals(listOf("P0133"), recurring.remainingCodes.map { it.code })
        assertEquals(listOf("P0AA6"), recurring.clearedCodes.map { it.code })
        assertTrue(recurring.newCodes.isEmpty())
    }

    @Test
    fun `모의 세션은 텍스트 맨 위에 경고를 표시한다`() {
        val text = SessionFormatter.toText(session().copy(simulated = true), zone)

        assertTrue(text.contains("모의 데이터"))
    }

    // ---------- JSON ----------

    @Test
    fun `JSON 에 필수 항목이 들어간다`() {
        val json = SessionFormatter.toJson(session(), zone)

        assertTrue(json.contains("\"app\": \"Serotonin OBD\""))
        assertTrue(json.contains("\"deviceAddressMasked\": \"**:**:**:**:98:8B\""))
        assertTrue(json.contains("\"P0133\""))
        assertTrue(json.contains("\"clearAttempted\": false"))
        assertFalse(json.contains("00:1D:A5:68:98:8B"))
    }

    @Test
    fun `새 JSON은 진단 당시 차량 계열 스냅샷을 저장하고 다시 읽는다`() {
        val snapshot = DiagnosticVehicleSnapshot(
            profileId = "00000000-0000-0000-0000-000000000001",
            coverageId = "kr-hyundai-avante-family",
            coverageLabel = "대한민국 현대 아반떼 계열",
            catalogRevision = 3,
            manufacturer = "현대", model = "아반떼 CN7", modelYear = 2022,
            powertrain = "가솔린", market = "대한민국", engine = "1.6 MPI", transmission = "6단 자동"
        )
        val communication = DiagnosticCommunicationSnapshot(protocolIdentified = true, standardDataObserved = false, udsRespondingEcuCount = 4)
        val json = SessionFormatter.toJson(session().copy(vehicleSnapshot = snapshot, communicationSnapshot = communication), zone)
        val restored = SavedCodeHistory.parse(json)

        assertTrue(json.contains("\"reportSchema\": 2"))
        assertTrue(json.contains("\"coverageId\": \"kr-hyundai-avante-family\""))
        assertEquals("kr-hyundai-avante-family", restored.coverageId)
        assertEquals("대한민국 현대 아반떼 계열", restored.coverageLabel)
        assertEquals(true, restored.protocolIdentified)
        assertEquals(false, restored.standardDataObserved)
        assertEquals(4, restored.udsRespondingEcuCount)
        assertTrue(SessionFormatter.toText(session().copy(vehicleSnapshot = snapshot), zone).contains("차량 계열: 대한민국 현대 아반떼 계열"))
    }

    @Test
    fun `스키마 1 기록에 새 차량 객체가 있어도 계열을 추정하지 않는다`() {
        val snapshot = DiagnosticVehicleSnapshot("id", "kr-kia-k5-family", "대한민국 기아 K5 계열", 3,
            "기아", "K5 DL3", 2021, "가솔린", "대한민국", "1.6T", "8단 자동")
        val oldJson = SessionFormatter.toJson(session().copy(vehicleSnapshot = snapshot), zone)
            .replace("\"reportSchema\": 2", "\"reportSchema\": 1")
        val restored = SavedCodeHistory.parse(oldJson)
        assertEquals(null, restored.coverageId)
        assertEquals(null, restored.coverageLabel)
    }

    @Test
    fun `JSON 문자열의 제어문자를 이스케이프한다`() {
        val escaped = Json.str("43 01\r\n>")

        assertEquals("\"43 01\\r\\n>\"", escaped)
    }

    @Test
    fun `JSON 문자열의 따옴표와 역슬래시를 이스케이프한다`() {
        assertEquals("\"a\\\"b\\\\c\"", Json.str("a\"b\\c"))
    }

    @Test
    fun `null 은 JSON null 로 쓴다`() {
        assertEquals("null", Json.str(null))
    }

    // ---------- 복사용 요약 ----------

    @Test
    fun `복사용 요약은 기록 양식을 따른다`() {
        val summary = SessionFormatter.toClipboardSummary(session(), zone)

        listOf(
            "[Serotonin OBD 진단 결과]",
            "차량:",
            "진단 시각:",
            "어댑터 정보 ATI:",
            "어댑터 전압 ATRV:",
            "저장 DTC:",
            "보류 DTC:",
            "영구 DTC:",
            "삭제 시도 여부:",
            "특이사항:"
        ).forEach { assertTrue("누락된 항목: $it", summary.contains(it)) }
    }

    @Test
    fun `요약은 모드별로 코드를 나눈다`() {
        val summary = SessionFormatter.toClipboardSummary(session(), zone)

        assertTrue(summary.contains("저장 DTC: P0133"))
        assertTrue(summary.contains("영구 DTC: P0AA6"))
        assertTrue(summary.contains("보류 DTC: 없음"))
    }
}
