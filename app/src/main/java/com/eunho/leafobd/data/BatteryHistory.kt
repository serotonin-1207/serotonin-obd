package com.eunho.leafobd.data

import com.eunho.leafobd.ev.EvProfile
import java.io.*
import java.time.OffsetDateTime
import java.util.UUID

data class BatteryRecord(
    val id: String = UUID.randomUUID().toString(),
    val profile: EvProfile?,
    val snapshot: BatterySnapshot,
    val message: String,
    val raw: String,
    val vehicleProfileId: String? = null
)

/** App-private, versioned records. No serialization of executable classes or external paths. */
class BatteryHistory(private val directory: File) {
    fun delete(id: String) {
        require(id == UUID.fromString(id).toString())
        val target = File(directory, "$id.battery")
        check(!target.exists() || target.delete()) { "기록을 삭제하지 못했습니다." }
    }
    fun save(record: BatteryRecord) {
        require(!record.snapshot.demo) { "가상 예시는 측정 기록에 저장하지 않습니다." }
        require(record.id == UUID.fromString(record.id).toString())
        directory.mkdirs()
        val target = File(directory, "${record.id}.battery")
        val temp = File(directory, "${record.id}.pending")
        try {
            val bytes = BatteryRecordCodec.encode(record)
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            check(temp.renameTo(target)) { "측정 기록 저장에 실패했습니다." }
        } finally { temp.delete() }
    }

    /** An unreadable file never hides the other records. Report its count instead. */
    fun load(): Pair<List<BatteryRecord>, Int> {
        var failed = 0
        val records = directory.listFiles()?.filter { it.extension == "battery" }?.mapNotNull { file ->
            runCatching {
                require(file.length() in 1..BatteryRecordCodec.MAX_BYTES.toLong())
                BatteryRecordCodec.decode(file.readBytes())
            }.getOrElse { failed++; null }
        }.orEmpty().sortedByDescending { OffsetDateTime.parse(it.snapshot.capturedAt).toInstant() }
        return records to failed
    }
}

object BatteryRecordCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun encode(record: BatteryRecord): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { o ->
            fun text(s: String) { val b = s.toByteArray(Charsets.UTF_8); require(b.size <= MAX_BYTES); o.writeInt(b.size); o.write(b) }
            fun number(n: Double?) { o.writeBoolean(n != null); if (n != null) o.writeDouble(n) }
            o.writeInt(2); text(record.id); text(record.profile?.name.orEmpty()); text(record.vehicleProfileId.orEmpty())
            val s = record.snapshot
            text(s.vehicle); text(s.capturedAt); text(s.source); text(s.acquisitionNote)
            o.writeInt(s.expectedChannels); number(s.soc); number(s.reportedSoh); o.writeBoolean(s.demo)
            o.writeInt(s.channels.size)
            s.channels.forEach { o.writeInt(it.id); number(it.volts); number(it.celsius) }
            o.writeInt(s.temperatureSensors.size)
            s.temperatureSensors.forEach { text(it.first); o.writeDouble(it.second) }
            text(record.message); text(record.raw)
        }
        val bytes = buffer.toByteArray()
        require(bytes.size <= MAX_BYTES) { "측정 기록이 너무 큽니다." }
        decode(bytes) // Validate before replacing a durable record.
        return bytes
    }

    fun decode(bytes: ByteArray): BatteryRecord {
        require(bytes.size <= MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            fun text(): String { val n = input.readInt(); require(n in 0..input.available()); return ByteArray(n).also { input.readFully(it) }.toString(Charsets.UTF_8) }
            fun number(low: Double, high: Double): Double? = if (!input.readBoolean()) null else input.readDouble().also { require(it.isFinite() && it in low..high) }
            val version = input.readInt(); require(version in 1..2)
            val id = text(); require(id == UUID.fromString(id).toString())
            val profile = text().let { if (it.isEmpty()) null else EvProfile.valueOf(it) }
            val vehicleProfileId = if (version >= 2) text().takeIf { it.isNotEmpty() }?.also { require(it == UUID.fromString(it).toString()) } else null
            val vehicle = text(); val timestamp = text(); OffsetDateTime.parse(timestamp)
            val source = text(); val note = text()
            require(vehicle.isNotBlank() && source.isNotBlank())
            val expected = input.readInt(); require(expected in 1..512)
            val soc = number(0.0, 100.0); val soh = number(0.0, 100.0); val demo = input.readBoolean()
            val count = input.readInt(); require(count in 0..expected)
            val channels = List(count) {
                val channel = input.readInt(); require(channel in 1..expected)
                BatteryChannel(channel, number(0.0, 6.0), number(-80.0, 150.0))
            }
            require(channels.map { it.id }.distinct().size == count)
            val sensorCount = input.readInt(); require(sensorCount in 0..512)
            val sensors = List(sensorCount) { text() to input.readDouble().also { require(it.isFinite() && it in -80.0..150.0) } }
            val message = text(); val raw = text(); require(input.available() == 0)
            val snapshot = BatterySnapshot(vehicle, timestamp, source, expected, channels, soc, soh, demo, sensors, note)
            require(profile == null || (vehicle == profile.label && expected == profile.count && !demo))
            return BatteryRecord(id, profile, snapshot, message, raw, vehicleProfileId)
        }
    }
}

object BatteryPresentation {
    /** Age is a presentation rule, never a battery health threshold. */
    fun age(snapshot: BatterySnapshot, now: java.time.Instant): String {
        if (snapshot.demo) return "가상 예시 · 실시간 측정 아님"
        val measured = runCatching { OffsetDateTime.parse(snapshot.capturedAt).toInstant() }.getOrNull()
            ?: return "측정 시각 확인 불가"
        val seconds = java.time.Duration.between(measured, now).seconds
        return when {
            seconds < -60 -> "측정 시각이 현재보다 미래입니다 · 기기/파일 시각 확인"
            seconds >= 300 -> "이전 측정값 · ${seconds / 60}분 전 · 현재 상태가 아닙니다"
            else -> "단일 조회 결과 · 실시간 갱신 아님"
        }
    }
    fun extrema(s: BatterySnapshot): String {
        val received = s.channels.filter { it.volts != null }
        if (received.isEmpty()) return "전압 수신 없음"
        val low = received.minOf { it.volts!! }; val high = received.maxOf { it.volts!! }
        fun ids(v: Double): String {
            val matches = received.filter { it.volts == v }
            return matches.take(8).joinToString(", ") { it.id.toString() } + if (matches.size > 8) " 외 ${matches.size - 8}개" else ""
        }
        return "수신값 중 최저 ${"%.3f".format(java.util.Locale.ROOT, low)} V · 채널 ${ids(low)}\n수신값 중 최고 ${"%.3f".format(java.util.Locale.ROOT, high)} V · 채널 ${ids(high)}"
    }

    fun percent(value: Double?, decimals: Int): String = value?.let {
        ".${decimals}f".let { pattern -> "%$pattern".format(java.util.Locale.ROOT, it) } + "%"
    } ?: "미확인"

    /** 측정값을 설명하되 차종별 검증 기준이 없는 정상/고장 판정은 만들지 않는다. */
    fun interpretation(s: BatterySnapshot): List<String> {
        val received = s.channels.count { it.volts != null }
        val result = mutableListOf<String>()
        result += if (received == s.expectedChannels) {
            "통신 · 셀 전압 ${s.expectedChannels}개를 모두 받았습니다."
        } else {
            "통신 · 셀 전압 $received/${s.expectedChannels}개만 받았습니다. 누락된 값으로 전체 상태를 판단하지 마세요."
        }
        result += s.soc?.let {
            "SOC ${percent(it, 1)} · 지금 사용할 수 있다고 차량 BMS가 계산한 충전 잔량입니다. 계기판 표시와 다를 수 있습니다."
        } ?: "SOC · 받지 못했습니다. 현재 충전 잔량을 판단할 수 없습니다."
        result += s.reportedSoh?.let {
            "SOH ${percent(it, 2)} · 차량 BMS의 배터리 열화 추정값입니다. 실제 용량 시험 결과가 아니므로 같은 조건의 변화 추세를 확인하세요."
        } ?: "SOH · 받지 못했습니다. 배터리 건강도를 추정하지 않습니다."
        result += s.spreadMv?.let {
            "셀 전압 편차 ${"%.0f".format(java.util.Locale.ROOT, it)} mV · 같은 시각의 최고 셀과 최저 셀 차이입니다. 작을수록 전압이 고른 편이지만, 이 값 하나로 정상이나 고장을 판정하지 않습니다."
        } ?: "셀 전압 편차 · 모든 셀을 받지 못해 계산하지 않았습니다."
        val temperatures = s.temperatureSensors.map { it.second } + s.channels.mapNotNull { it.celsius }
        result += if (temperatures.isEmpty()) {
            "배터리 온도 · 받지 못했습니다. 과열 여부를 판단하지 않습니다."
        } else {
            val low = temperatures.min(); val high = temperatures.max()
            "배터리 온도 ${"%.1f".format(java.util.Locale.ROOT, low)}~${"%.1f".format(java.util.Locale.ROOT, high)} °C · 센서 간 차이는 ${"%.1f".format(java.util.Locale.ROOT, high - low)} °C입니다. 외기온, 충전 직후인지, 주행 부하를 함께 확인하세요."
        }
        val sensorIds = s.temperatureSensors.mapNotNull { (label, _) ->
            Regex("^센서 (\\d+)").find(label)?.groupValues?.get(1)?.toIntOrNull()
        }
        if (s.vehicle.startsWith("리프 ZE1") && sensorIds == listOf(1, 2, 4)) {
            result += "온도 센서 번호 · 리프 응답의 4개 후보 중 1·2·4번을 표시했습니다. 3번 원시값은 미제공(FFFF)이라 결과에서 제외했습니다."
        }
        return result
    }
}
