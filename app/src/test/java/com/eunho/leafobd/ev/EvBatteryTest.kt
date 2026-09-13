package com.eunho.leafobd.ev

import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.Elm327Transport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EvBatteryTest {
    private val time = "2026-09-11T10:00:00+09:00"
    private fun framed(rx: String, data: List<Int>): String {
        val lines = mutableListOf<List<Int>>()
        lines.add(listOf(0x10 or (data.size shr 8), data.size and 255) + data.take(6))
        data.drop(6).chunked(7).forEachIndexed { i, chunk -> lines.add(listOf(0x20 or ((i + 1) and 15)) + chunk) }
        return lines.joinToString("\r") { rx + " " + it.joinToString(" ") { b -> "%02X".format(b) } } + "\r>"
    }
    @Test fun completeLongMessageWithSequenceWrap() {
        val bytes = List(329) { it and 255 }
        assertEquals(bytes, BatteryFrames.payload(framed("7BB", listOf(0x61, 0x61) + bytes), "2161", "7BB"))
    }
    @Test fun wrongEcuAndNegativeResponseRejected() {
        assertNull(BatteryFrames.payload("7EC 03 7F 21 12\r>", "2101", "7BB"))
        assertNull(BatteryFrames.payload("7BB 03 7F 21 12\r>", "2101", "7BB"))
    }
    @Test fun missingAndDuplicateFramesRejected() {
        val lines = framed("7BB", listOf(0x61, 2) + List(196) { 1 }).split('\r')
        assertNull(BatteryFrames.payload(lines.filterIndexed { i, _ -> i != 3 }.joinToString("\r"), "2102", "7BB"))
        assertNull(BatteryFrames.payload((lines.take(3) + lines.drop(2)).joinToString("\r"), "2102", "7BB"))
        assertNull(BatteryFrames.payload(lines.dropLast(3).joinToString("\r"), "2102", "7BB"))
    }
    @Test fun badSingleFrameLengthAndWrongPidRejected() {
        assertNull(BatteryFrames.payload("7BB 07 61 01 00\r>", "2101", "7BB"))
        assertNull(BatteryFrames.payload(framed("7BB", listOf(0x61, 4) + List(51) { 0 }), "2101", "7BB"))
    }
    @Test fun leafPublishedSocResponse() {
        // OVMS source contains this ZE1 capture; this is not a capture from the user's car.
        val raw = "7BB10356101FFFFFC18\r7BB2102AFFFFB62FFFF\r"
        assertNull(BatteryFrames.payload(raw, "2101", "7BB"))
        val d = MutableList(51) { 0 }; d[31] = 0x0C; d[32] = 0x44; d[33] = 0xB5
        assertEquals(80.4021, EvBatteryDecoder.decode(EvProfile.LEAF_ZE1, mapOf("2101" to d), time).soc!!, 0.0001)
    }
    @Test fun leafVoltageAndTemperatureAreSeparate() {
        val v = List(96) { listOf(0x0E, 0x74) }.flatten() + listOf(0, 0, 0, 0)
        val t = MutableList(29) { 255 }; t[0] = 2; t[1] = 13
        val s = EvBatteryDecoder.decode(EvProfile.LEAF_ZE1, mapOf("2102" to v, "2104" to t), time)
        assertEquals(3.7, s.channels.first().volts!!, 0.0001)
        assertEquals(0.0, s.spreadMv!!, 0.0001)
        assertEquals(18.87, s.temperatureSensors.single().second, 0.001)
        assertTrue(s.channels.all { it.celsius == null })
    }
    @Test fun niroAll98ChannelsAndSoh() {
        val cell = List(4) { 0 } + List(32) { 185 }
        val five = MutableList(36) { 0 }; five[25] = 3; five[26] = 172; five[34] = 186; five[35] = 185
        val s = EvBatteryDecoder.decode(EvProfile.NIRO_DE, mapOf("220102" to cell, "220103" to cell, "220104" to cell, "220105" to five), time)
        assertEquals(98, s.channels.count { it.volts != null })
        assertEquals(94.0, s.reportedSoh!!, 0.001)
        assertEquals(20.0, s.spreadMv!!, 0.001)
    }
    @Test fun missingPageNeverCreatesFullSpread() {
        val s = EvBatteryDecoder.decode(EvProfile.NIRO_DE, mapOf("220102" to (List(4) { 0 } + List(32) { 185 })), time)
        assertNull(s.spreadMv)
        assertNull(s.soc)
        assertNull(s.reportedSoh)
        assertEquals(32, s.channels.count { it.volts != null })
    }
    @Test fun unsupportedLeafLayoutAndOutOfRangeHealthUnknown() {
        val s = EvBatteryDecoder.decode(EvProfile.LEAF_ZE1, mapOf("2101" to List(42) { 0 }, "2161" to List(329) { 255 }), time)
        assertNull(s.soc); assertNull(s.reportedSoh)
    }
    private class Scripted(private val reject: String? = null) : Elm327Transport {
        val sent = mutableListOf<String>(); private var pending = byteArrayOf()
        override val description = "검증용 가상 통로"
        override val simulated = true
        override val isOpen = true
        override fun open() {}
        override fun close() {}
        override fun write(data: ByteArray) {
            val c = data.toString(Charsets.US_ASCII).trim(); sent.add(c)
            pending = (if (c == reject) "?\r>" else if (c.startsWith("AT")) "OK\r>" else "NO DATA\r>").toByteArray()
        }
        override fun read(buffer: ByteArray): Int {
            val n = minOf(buffer.size, pending.size); pending.copyInto(buffer, 0, 0, n); pending = pending.drop(n).toByteArray(); return n
        }
    }
    @Test fun setupFailureStopsAllVehicleRequestsAndRestores() = runBlocking {
        val t = Scripted("ATFCSM1")
        val r = EvBatteryService(Elm327Client(t)).read(EvProfile.LEAF_ZE1)
        assertTrue(t.sent.all { it.startsWith("AT") })
        assertEquals(listOf("ATFCSM0", "ATCRA", "ATSH7DF"), t.sent.takeLast(3))
        assertTrue(r.messages.any { it.contains("설정 실패") })
    }
    @Test fun onlyAllowlistedReadsNoRetriesAndRawPreserved() = runBlocking {
        val t = Scripted()
        val r = EvBatteryService(Elm327Client(t)).read(EvProfile.NIRO_DE)
        assertEquals(EvProfile.NIRO_DE.requests, t.sent.filterNot { it.startsWith("AT") })
        assertTrue(r.logs.filter { it.command.startsWith("22") }.all { it.rawResponse == "NO DATA\r>" })
        assertNull(r.snapshot.spreadMv)
    }
    @Test fun stopAfterFirstRequestRetainsLogsAndRestores() = runBlocking {
        val t = Scripted()
        var stop = false
        val stages = mutableListOf<String>()
        val r = EvBatteryService(Elm327Client(t)).read(EvProfile.NIRO_DE, { stop }) { stage, done, _ ->
            stages.add(stage)
            if (done == 1) stop = true
        }
        assertEquals(listOf("220101"), t.sent.filterNot { it.startsWith("AT") })
        assertEquals(listOf("ATFCSM0", "ATCRA", "ATSH7DF"), t.sent.takeLast(3))
        assertTrue(r.messages.any { it.contains("중단") })
        assertTrue(stages.any { it.contains("복원") })
        assertTrue(r.logs.any { it.command == "220101" })
    }
    @Test fun stopDuringSetupSendsNoVehicleRequests() = runBlocking {
        val t = Scripted(); var stop = false
        EvBatteryService(Elm327Client(t)).read(EvProfile.LEAF_ZE1, { stop }) { _, _, _ -> stop = true }
        assertTrue(t.sent.all { it.startsWith("AT") })
    }
}
