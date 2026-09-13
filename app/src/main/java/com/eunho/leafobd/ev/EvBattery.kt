package com.eunho.leafobd.ev

import com.eunho.leafobd.data.BatteryChannel
import com.eunho.leafobd.data.BatterySnapshot
import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.util.ResponseText
import java.time.OffsetDateTime

/** OVMS MIT sources and byte offsets: EV_PROTOCOLS.md. Target cars have not been validated yet. */
enum class EvProfile(val label: String, val tx: String, val rx: String, val count: Int, val requests: List<String>) {
    LEAF_ZE1("리프 ZE1 · 2019 · 40kWh", "79B", "7BB", 96, listOf("2101", "2102", "2104", "2161")),
    NIRO_DE("니로 EV DE · 2019 · 64kWh", "7E4", "7EC", 98, listOf("220101", "220102", "220103", "220104", "220105"))
}

data class EvBatteryResult(val snapshot: BatterySnapshot, val messages: List<String>, val logs: List<CommandLog>)

/** Strict ISO-TP for ATH1/ATD0: rejects missing/duplicate/reordered frames and wrong ECU. */
object BatteryFrames {
    fun payload(raw: String, request: String, rx: String): List<Int>? {
        val lines = ResponseText.dataLines(raw, request)
        var length: Int? = null
        val bytes = mutableListOf<Int>()
        var sequence = 1
        for (line in lines) {
            val hex = line.replace(" ", "").uppercase()
            if (!hex.startsWith(rx)) continue
            if (hex.length !in 7..19 || (hex.length - 3) % 2 != 0 || !hex.all { it in "0123456789ABCDEF" }) return null
            val frame = hex.drop(3).chunked(2).map { it.toInt(16) }
            when (frame[0] shr 4) {
                0 -> {
                    if (length != null) return null
                    length = frame[0] and 15
                    if (length !in 1..7 || frame.size < length + 1) return null
                    bytes.addAll(frame.drop(1).take(length))
                }
                1 -> {
                    if (length != null || frame.size != 8) return null
                    length = ((frame[0] and 15) shl 8) or frame[1]
                    if (length !in 8..512) return null
                    bytes.addAll(frame.drop(2))
                }
                2 -> {
                    val expected = length ?: return null
                    if (bytes.size >= expected || (frame[0] and 15) != sequence) return null
                    val needed = minOf(7, expected - bytes.size)
                    if (frame.size < needed + 1) return null
                    bytes.addAll(frame.drop(1).take(needed))
                    sequence = (sequence + 1) and 15
                }
                else -> return null
            }
        }
        if (length == null || bytes.size != length) return null
        val req = request.chunked(2).map { it.toInt(16) }
        val prefix = listOf(req[0] + 0x40) + req.drop(1)
        return bytes.takeIf { it.take(prefix.size) == prefix }?.drop(prefix.size)
    }
}

object EvBatteryDecoder {
    fun decode(profile: EvProfile, pages: Map<String, List<Int>>, capturedAt: String): BatterySnapshot {
        fun valid(v: Double, low: Double, high: Double) = v.takeIf { it.isFinite() && it in low..high }
        val volts = MutableList<Double?>(profile.count) { null }
        val temperatures = mutableListOf<Pair<String, Double>>()
        var soc: Double? = null
        var soh: Double? = null
        if (profile == EvProfile.LEAF_ZE1) {
            pages["2101"]?.takeIf { it.size == 51 }?.let { d ->
                soc = valid(((d[31] shl 16) or (d[32] shl 8) or d[33]) / 10000.0, 0.0, 100.0)
            }
            pages["2102"]?.takeIf { it.size == 196 }?.let { d ->
                for (i in volts.indices) volts[i] = valid(((d[i * 2] shl 8) or d[i * 2 + 1]) / 1000.0, 0.001, 4.999)
            }
            pages["2104"]?.takeIf { it.size == 29 }?.let { d ->
                for (i in 0..3) {
                    val thermistor = (d[i * 3] shl 8) or d[i * 3 + 1]
                    if (thermistor != 65535) valid(-0.102 * (thermistor - 710), -80.0, 150.0)?.let {
                        temperatures.add("센서 ${i + 1} · 환산값" to it)
                    }
                }
            }
            pages["2161"]?.takeIf { it.size == 329 }?.let { d -> soh = valid(((d[2] shl 8) or d[3]) / 100.0, 0.0, 100.0) }
        } else {
            pages["220101"]?.takeIf { it.size >= 20 }?.let { d ->
                soc = valid(d[4] / 2.0, 0.0, 100.0)
                for (i in 16..19) valid(d[i].toDouble(), 0.0, 150.0)?.let { temperatures.add("센서 ${i - 15}" to it) }
            }
            for (page in 2..4) pages["22010$page"]?.takeIf { it.size >= 36 }?.let { d ->
                for (i in 0..31) volts[(page - 2) * 32 + i] = valid(d[i + 4] * 0.02, 0.001, 5.0)
            }
            pages["220105"]?.takeIf { it.size >= 36 }?.let { d ->
                soh = valid(((d[25] shl 8) or d[26]) / 10.0, 0.0, 100.0)
                volts[96] = valid(d[34] * 0.02, 0.001, 5.0)
                volts[97] = valid(d[35] * 0.02, 0.001, 5.0)
            }
        }
        return BatterySnapshot(profile.label, capturedAt, "차량 직접 조회 · OVMS 근거 · 실차 대조 전", profile.count,
            volts.mapIndexed { i, v -> BatteryChannel(i + 1, v, null) }, soc, soh,
            temperatureSensors = temperatures, acquisitionNote = "여러 요청을 순차 조회한 결과 · 표시 시각은 조회 완료 시각 · 동시 측정 아님")
    }
}

class EvBatteryService(private val client: Elm327Client) {
    /** No polling, retry, session change, security access, control or clearing. Caller owns operation mutex. */
    suspend fun read(profile: EvProfile,
        shouldStop: () -> Boolean = { false },
        onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }): EvBatteryResult {
        val logs = mutableListOf<CommandLog>()
        val pages = linkedMapOf<String, List<Int>>()
        val messages = mutableListOf<String>()
        var completed = 0
        suspend fun send(command: String): CommandLog = client.send(command, 8000).also { logs.add(it) }
        // Explicit complete configuration; refuse reads when any adapter prerequisite is rejected.
        val setup = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATD0", "ATSP6", "ATCAF1", "ATCFC1", "ATAL",
            "ATSH${profile.tx}", "ATCRA${profile.rx}", "ATFCSH${profile.tx}", "ATFCSD30000A", "ATFCSM1")
        try {
            for (command in setup) {
                if (shouldStop()) break
                onProgress("어댑터 준비 · $command", 0, profile.requests.size)
                val log = send(command)
                check(log.success && (command == "ATZ" || ResponseText.dataLines(log.rawResponse, command).any { it.trim() == "OK" })) {
                    "어댑터 설정 실패: $command. 배터리 요청을 중단했습니다."
                }
            }
            for ((index, request) in profile.requests.withIndex()) {
                if (shouldStop()) break
                val label = when (request) {
                    "2101", "220101" -> "잔량·배터리 기본값"
                    "2102", "220102", "220103", "220104" -> "셀 전압"
                    "2104" -> "온도 센서"
                    else -> "건강도·추가 배터리 값"
                }
                onProgress("${index + 1}/${profile.requests.size} · $label 조회 중", index, profile.requests.size)
                val log = send(request)
                val data = if (log.success) BatteryFrames.payload(log.rawResponse, request, profile.rx) else null
                if (data == null) messages.add("$label ($request) · 응답 미확인 또는 불완전. 차량 전원·연결을 확인하고 다시 연결한 뒤 조회하세요.") else pages[request] = data
                onProgress("${index + 1}/${profile.requests.size} · $label 처리 완료", index + 1, profile.requests.size)
                completed = index + 1
            }
        } catch (e: Exception) {
            messages.add(e.message ?: "배터리 조회 실패")
        } finally {
            onProgress("어댑터 설정 복원 중 · 완료 후 연결 해제", completed, profile.requests.size)
            // Clear address/filter configuration; caller disconnects and invalidates initialization as well.
            for (command in listOf("ATFCSM0", "ATCRA", "ATSH7DF")) {
                if (!send(command).success) messages.add("어댑터 설정 복원 실패: $command")
            }
        }
        if (shouldStop()) messages.add("사용자가 중단했습니다. 중단 전 수신한 값만 표시합니다.")
        val snapshot = EvBatteryDecoder.decode(profile, pages, OffsetDateTime.now().toString())
        messages.add("전압 ${snapshot.channels.count { it.volts != null }}/${profile.count} · 미확인 항목은 정상 판정하지 않습니다.")
        return EvBatteryResult(snapshot, messages, logs)
    }
}
