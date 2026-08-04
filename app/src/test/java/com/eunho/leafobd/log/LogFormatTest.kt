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
