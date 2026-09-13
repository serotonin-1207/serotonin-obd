package com.eunho.leafobd.log

import com.eunho.leafobd.data.BatteryCsv
import org.junit.Assert.*
import org.junit.Test

class BatteryReportTest {
    @Test fun excludesFreeTextAndPreservesMissingChannels() {
        val data = BatteryCsv.example().copy(vehicle = "SECRET", source = "SECRET", capturedAt = "SECRET", acquisitionNote = "SECRET",
            channels = BatteryCsv.example().channels.drop(1), temperatureSensors = listOf("SECRET" to 25.0))
        val report = BatteryReport.from(data)
        assertFalse(report.toString().contains("SECRET"))
        assertNull(report.charts.first().points.first().second)
        assertTrue(report.summary.contains("전체 전압 편차: 미확인"))
        assertTrue(report.demo)
    }
    @Test fun invalidNumbersDoNotEnterGraphsOrSummary() {
        val data = BatteryCsv.example()
        val report = BatteryReport.from(data.copy(soc = Double.NaN, reportedSoh = Double.POSITIVE_INFINITY,
            channels = data.channels.map { it.copy(volts = -1.0) }))
        assertTrue(report.charts.first().points.all { it.second == null })
        assertFalse(report.summary.contains("NaN"))
        assertFalse(report.summary.contains("Infinity"))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateChannelIdsRejected() {
        val data = BatteryCsv.example()
        BatteryReport.from(data.copy(channels = listOf(data.channels.first(), data.channels.first())))
    }
}
