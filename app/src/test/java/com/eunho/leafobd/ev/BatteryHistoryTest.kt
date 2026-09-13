package com.eunho.leafobd.ev

import com.eunho.leafobd.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class BatteryHistoryTest {
    @Test fun profileIdSurvivesRoundTrip() {
        val id = java.util.UUID.randomUUID().toString()
        val record = BatteryRecord(profile = null, snapshot = snapshot(), message = "", raw = "", vehicleProfileId = id)
        assertEquals(id, BatteryRecordCodec.decode(BatteryRecordCodec.encode(record)).vehicleProfileId)
    }

    @Test fun readsVersionOneRecordWithoutInventingVehicleProfile() {
        val original = BatteryRecord(profile = null, snapshot = snapshot(), message = "이전", raw = "원본")
        val buffer = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(buffer).use { output ->
            fun text(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); output.writeInt(bytes.size); output.write(bytes) }
            fun number(value: Double?) { output.writeBoolean(value != null); if (value != null) output.writeDouble(value) }
            output.writeInt(1); text(original.id); text("")
            val data = original.snapshot
            text(data.vehicle); text(data.capturedAt); text(data.source); text(data.acquisitionNote)
            output.writeInt(data.expectedChannels); number(data.soc); number(data.reportedSoh); output.writeBoolean(data.demo)
            output.writeInt(data.channels.size); data.channels.forEach { output.writeInt(it.id); number(it.volts); number(it.celsius) }
            output.writeInt(data.temperatureSensors.size); data.temperatureSensors.forEach { text(it.first); output.writeDouble(it.second) }
            text(original.message); text(original.raw)
        }
        val restored = BatteryRecordCodec.decode(buffer.toByteArray())
        assertNull(restored.vehicleProfileId)
        assertEquals(original.snapshot, restored.snapshot)
    }
    private fun snapshot() = BatterySnapshot("외부 차량", "2026-09-11T10:00:00+09:00", "검증용",
        3, listOf(BatteryChannel(1, 3.7, null), BatteryChannel(3, null, 25.0)), null, 94.0,
        temperatureSensors = listOf("센서 A" to -3.5), acquisitionNote = "검증용 시각")

    @Test fun restartRoundTripPreservesNullsSourcesAndRaw() {
        val dir = Files.createTempDirectory("battery-history-test").toFile()
        try {
            val record = BatteryRecord(profile = null, snapshot = snapshot(), message = "일부 누락", raw = "7BB 10 35\r\n>")
            BatteryHistory(dir).save(record)
            val (loaded, failed) = BatteryHistory(dir).load()
            assertEquals(0, failed); assertEquals(listOf(record), loaded)
            assertNull(loaded.single().snapshot.spreadMv)
            assertNull(loaded.single().snapshot.soc)
        } finally { dir.deleteRecursively() }
    }
    @Test fun corruptFileDoesNotHideValidRecords() {
        val dir = Files.createTempDirectory("battery-corrupt-test").toFile()
        try {
            BatteryHistory(dir).save(BatteryRecord(profile = null, snapshot = snapshot(), message = "", raw = ""))
            File(dir, "broken.battery").writeBytes(byteArrayOf(1, 2, 3))
            val (loaded, failed) = BatteryHistory(dir).load()
            assertEquals(1, failed); assertEquals(1, loaded.size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun mismatchedVehicleCannotBecomeNiroRecord() {
        val record = BatteryRecord(profile = EvProfile.NIRO_DE, snapshot = snapshot(), message = "", raw = "")
        assertThrows(IllegalArgumentException::class.java) { BatteryRecordCodec.encode(record) }
    }
    @Test fun truncatedAndExtraDataRejected() {
        val bytes = BatteryRecordCodec.encode(BatteryRecord(profile = null, snapshot = snapshot(), message = "", raw = ""))
        assertThrows(Exception::class.java) { BatteryRecordCodec.decode(bytes.dropLast(1).toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { BatteryRecordCodec.decode(bytes + byteArrayOf(0)) }
    }
    @Test fun invalidNumbersAndDuplicateChannelsRejected() {
        val bad = snapshot().copy(channels = listOf(BatteryChannel(1, Double.NaN, null)))
        assertThrows(IllegalArgumentException::class.java) { BatteryRecordCodec.encode(BatteryRecord(profile = null, snapshot = bad, message = "", raw = "")) }
        val duplicate = snapshot().copy(channels = List(2) { BatteryChannel(1, 3.7, null) })
        assertThrows(IllegalArgumentException::class.java) { BatteryRecordCodec.encode(BatteryRecord(profile = null, snapshot = duplicate, message = "", raw = "")) }
    }
    @Test fun futureOldAndDemoLabelsDiffer() {
        assertTrue(BatteryPresentation.age(snapshot(), Instant.parse("2026-09-11T01:06:00Z")).contains("이전 측정값"))
        assertTrue(BatteryPresentation.age(snapshot(), Instant.parse("2026-09-11T00:00:00Z")).contains("미래"))
        assertTrue(BatteryPresentation.age(BatteryCsv.example(), Instant.now()).contains("가상 예시"))
    }
    @Test fun missingChannelExcludedFromExtrema() {
        val text = BatteryPresentation.extrema(snapshot())
        assertTrue(text.contains("3.700 V")); assertTrue(text.contains("채널 1")); assertFalse(text.contains("채널 3"))
    }
    @Test fun interpretationExplainsCompleteMeasurementAndLimits() {
        val complete = BatterySnapshot("검증 차량", "2026-09-11T10:00:00+09:00", "검증용", 2,
            listOf(BatteryChannel(1, 3.700, null), BatteryChannel(2, 3.710, null)), 45.8222, 82.99,
            temperatureSensors = listOf("센서 1" to 38.0, "센서 2" to 40.3))
        val text = BatteryPresentation.interpretation(complete).joinToString("\n")
        assertTrue(text.contains("전압 2개를 모두"))
        assertTrue(text.contains("SOC 45.8%"))
        assertTrue(text.contains("SOH 82.99%"))
        assertTrue(text.contains("실제 용량 시험 결과가 아니"))
        assertTrue(text.contains("정상이나 고장을 판정하지 않"))
        assertTrue(text.contains("38.0~40.3 °C"))
    }
    @Test fun leafUnavailableTemperaturePositionIsExplained() {
        val leaf = BatterySnapshot("리프 ZE1 · 2019 · 40kWh", "2026-09-11T10:00:00+09:00", "검증용", 2,
            listOf(BatteryChannel(1, 3.700, null), BatteryChannel(2, 3.710, null)), 45.8, 82.99,
            temperatureSensors = listOf("센서 1 · 환산값" to 40.3, "센서 2 · 환산값" to 40.0, "센서 4 · 환산값" to 38.0))
        val text = BatteryPresentation.interpretation(leaf).joinToString("\n")
        assertTrue(text.contains("1·2·4번"))
        assertTrue(text.contains("미제공(FFFF)"))
    }
    @Test fun interpretationDoesNotTreatMissingValuesAsNormal() {
        val text = BatteryPresentation.interpretation(snapshot()).joinToString("\n")
        assertTrue(text.contains("1/3개만"))
        assertTrue(text.contains("SOC · 받지 못했습니다"))
        assertTrue(text.contains("편차 · 모든 셀을 받지 못해"))
    }
    @Test fun profilesStaySeparatedAndNewestFirst() {
        val dir = Files.createTempDirectory("battery-profiles-test").toFile()
        try {
            EvProfile.entries.forEach { p ->
                BatteryHistory(dir).save(BatteryRecord(profile = p,
                    snapshot = EvBatteryDecoder.decode(p, emptyMap(), "2026-09-11T10:00:00+09:00"), message = "응답 없음", raw = "NO DATA\r>"))
            }
            val records = BatteryHistory(dir).load().first
            assertEquals(1, records.count { it.profile == EvProfile.LEAF_ZE1 })
            assertEquals(1, records.count { it.profile == EvProfile.NIRO_DE })
        } finally { dir.deleteRecursively() }
    }
    @Test fun demoIsNotPersisted() {
        val dir = Files.createTempDirectory("battery-demo-test").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) { BatteryHistory(dir).save(BatteryRecord(profile = null, snapshot = BatteryCsv.example(), message = "", raw = "")) }
            assertTrue(BatteryHistory(dir).load().first.isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun deleteIsRestrictedToOneKnownRecordPath() {
        val dir = Files.createTempDirectory("battery-delete-test").toFile()
        try {
            val a = BatteryRecord(profile = null, snapshot = snapshot(), message = "", raw = "")
            val b = a.copy(id = java.util.UUID.randomUUID().toString())
            val history = BatteryHistory(dir); history.save(a); history.save(b)
            assertThrows(IllegalArgumentException::class.java) { history.delete("../outside") }
            history.delete(a.id)
            assertEquals(listOf(b), history.load().first)
        } finally { dir.deleteRecursively() }
    }
}
