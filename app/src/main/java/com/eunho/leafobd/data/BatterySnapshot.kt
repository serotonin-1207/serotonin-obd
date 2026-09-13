package com.eunho.leafobd.data

/** A single timestamp only; channel order is not a physical pack layout. No guessed diagnostic commands. */
data class BatteryChannel(val id: Int, val volts: Double?, val celsius: Double?)
data class BatterySnapshot(
    val vehicle: String,
    val capturedAt: String,
    val source: String,
    val expectedChannels: Int,
    val channels: List<BatteryChannel>,
    val soc: Double?,
    val reportedSoh: Double?,
    val demo: Boolean = false,
    val temperatureSensors: List<Pair<String, Double>> = emptyList(),
    val acquisitionNote: String = "외부 파일의 단일 시각 스냅샷"
) {
    val completeVoltages: Boolean get() = channels.size == expectedChannels && channels.all { it.volts != null }
    val spreadMv: Double? get() = if (completeVoltages) {
        val values = channels.mapNotNull { it.volts }
        (values.max() - values.min()) * 1000
    } else null
}

object BatteryCsv {
    const val MAX_BYTES = 262144
    const val HEADER = "channel,voltage_v,temperature_c"
    /** Strict, small interchange format. Blank means unknown, never zero. */
    fun parse(text: String): BatterySnapshot {
        require(text.length <= MAX_BYTES) { "파일이 너무 큽니다. 256 KB 이하만 가져올 수 있습니다." }
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val meta = linkedMapOf<String, String>()
        val data = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("#")) {
                val pair = line.drop(1).split('=', limit = 2)
                require(pair.size == 2 && pair[0] !in meta) { "메타데이터 형식 또는 중복 항목을 확인하세요." }
                meta[pair[0]] = pair[1]
            } else data.add(line)
        }
        require(data.firstOrNull() == HEADER) { "열 이름은 $HEADER 이어야 합니다." }
        fun required(key: String) = meta[key]?.takeIf { it.isNotBlank() && it.length <= 200 }
            ?: throw IllegalArgumentException("필수 정보가 없습니다: $key")
        val vehicle = required("vehicle")
        val source = required("source")
        val timestamp = required("captured_at")
        require(runCatching { java.time.OffsetDateTime.parse(timestamp) }.isSuccess) { "측정 시각에 시간대를 포함하세요. 예: 2026-09-11T10:00:00+09:00" }
        val expected = required("expected_channels").toIntOrNull()
        require(expected != null && expected in 1..512) { "채널 수는 1~512여야 합니다." }
        fun number(raw: String?, min: Double, max: Double): Double? {
            if (raw.isNullOrBlank()) return null
            val n = raw.toDoubleOrNull()
            require(n != null && n.isFinite() && n in min..max) { "숫자 또는 단위 범위를 확인하세요: $raw" }
            return n
        }
        val channels = data.drop(1).map { line ->
            val fields = line.split(',')
            require(fields.size == 3) { "각 행은 채널,전압,온도 3개 항목이어야 합니다." }
            val id = fields[0].trim().toIntOrNull()
            require(id != null && id in 1..expected) { "채널 번호가 범위를 벗어났습니다." }
            BatteryChannel(id, number(fields[1].trim(), 0.0, 6.0), number(fields[2].trim(), -80.0, 150.0))
        }
        require(channels.isNotEmpty() && channels.map { it.id }.distinct().size == channels.size) { "채널이 비었거나 중복되었습니다." }
        return BatterySnapshot(vehicle, timestamp, source, expected, channels.sortedBy { it.id },
            number(meta["soc"], 0.0, 100.0), number(meta["soh"], 0.0, 100.0))
    }

    fun example() = BatterySnapshot("가상 차량 · 실제 측정 아님", "2026-09-11T10:00:00+09:00", "화면 확인용 가상 데이터", 12,
        List(12) { BatteryChannel(it + 1, 3.69 + (it % 7) * 0.003, 25.0 + it % 3) }, 68.0, 94.0, true,
        acquisitionNote = "화면 확인용 가상 수치 · 실제 차량 또는 외부 측정 파일 아님")
}
