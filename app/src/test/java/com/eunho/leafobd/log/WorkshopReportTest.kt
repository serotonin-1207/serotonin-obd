package com.eunho.leafobd.log

import com.eunho.leafobd.data.BatteryCsv
import com.eunho.leafobd.obd.*
import org.junit.Assert.*
import org.junit.Test

class WorkshopReportTest {
    @Test fun excludesFreeTextAndRawIdentifiers() {
        val secret = "PRIVATE_IDENTIFIER_123"
        val dtc = DtcCode("P0300", DtcStatus.STORED, DtcSource.MODE_03, secret, secret, secret)
        val battery = BatteryCsv.example().copy(vehicle = secret, source = secret, capturedAt = secret,
            acquisitionNote = secret, temperatureSensors = listOf(secret to 25.0))
        val report = WorkshopReport.create(listOf(dtc), listOf(UdsDtcReadResult(secret, raw = secret, message = secret)), battery, false)
        assertFalse(report.contains(secret))
        assertTrue(report.contains("P0300"))
        assertTrue(report.contains("응답 불완전"))
        assertTrue(report.contains("가상 배터리 예시"))
    }
    @Test fun incompleteBatteryAndNoCodesAreNotNormalVerdicts() {
        val battery = BatteryCsv.example().copy(channels = BatteryCsv.example().channels.take(1), soc = null)
        val report = WorkshopReport.create(emptyList(), emptyList(), battery, true)
        assertTrue(report.contains("모의 진단"))
        assertTrue(report.contains("전체 편차 미확인"))
        assertTrue(report.contains("미조회/미지원/정상 여부"))
        assertTrue(report.contains("삭제 전후 비교가 포함되지 않습니다"))
    }
}
