package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/** CAN ID 하나에 대한 수신 집계. */
data class CanFrameSummary(
    val id: String,
    val count: Int,
    val sample: String
)

/**
 * 버스 듣기 결과.
 *
 * @param frames CAN ID별 집계 (많이 나온 순)
 * @param totalFrames 수신한 전체 프레임 수
 * @param raw 원시 수신 내용 (앞부분만 보관)
 */
data class CanMonitorResult(
    val frames: List<CanFrameSummary> = emptyList(),
    val totalFrames: Int = 0,
    val durationMs: Long = 0,
    val raw: String = "",
    val errorMessage: String? = null
) {
    val hasTraffic: Boolean get() = totalFrames > 0

    /**
     * 결과 해석.
     *
     * 2018년 이후 Leaf(ZE1)는 OBD-II 포트가 CAN 게이트웨이로 분리되어 있어
     * 브로드캐스트 트래픽이 포트까지 오지 않는 것으로 알려져 있다.
     * 따라서 "프레임 0건"이 곧 "차량이 꺼져 있다"를 뜻하지는 않는다.
     */
    val interpretation: String
        get() = when {
            errorMessage != null -> errorMessage
            totalFrames == 0 ->
                "버스에서 아무 프레임도 들리지 않았습니다.\n" +
                    "2018년 이후 Leaf(ZE1)는 OBD-II 포트가 CAN 게이트웨이로 분리되어 있어 " +
                    "브로드캐스트 트래픽이 포트까지 오지 않는 것으로 알려져 있습니다. " +
                    "따라서 이 결과만으로 차량이 꺼져 있다고 단정할 수는 없습니다."

            else ->
                "프레임 ${totalFrames}건, CAN ID ${frames.size}종이 확인되었습니다.\n" +
                    "버스에 통신이 흐르고 있습니다. 차량은 깨어 있습니다."
        }
}

/**
 * `ATMA` 수신 결과를 CAN ID별로 집계한다.
 *
 * 수신 줄은 헤더 표시(ATH1) 상태에서 다음과 같은 모양이다.
 * ```
 * 358 00 08 80
 * 1DA 01 02 03 04 05 06 07
 * ```
 */
object CanMonitorParser {

    /** 원시 수신 내용에서 프레임을 뽑아 집계한다. */
    fun parse(raw: String, durationMs: Long): CanMonitorResult {
        val counts = LinkedHashMap<String, Int>()
        val samples = LinkedHashMap<String, String>()
        var total = 0

        for (line in ResponseText.lines(raw)) {
            val upper = line.uppercase().trim()

            // 상태 문자열은 프레임이 아니다.
            if (upper.isEmpty()) continue
            if (upper == "OK" || upper == "?" || upper.startsWith("AT")) continue
            if (ResponseText.ERROR_KEYWORDS.any { upper.contains(it) }) continue
            if (upper.contains("NO DATA") || upper.contains("SEARCHING")) continue

            // 첫 토큰이 CAN ID(16진 3자리 또는 8자리)여야 한다.
            val compact = upper.replace(" ", "")
            if (!compact.all { it in '0'..'9' || it in 'A'..'F' }) continue

            val id = when {
                compact.length > 3 && compact.length % 2 == 1 -> compact.take(3)
                compact.length > 8 -> compact.take(8)
                else -> continue
            }

            counts[id] = (counts[id] ?: 0) + 1
            samples.putIfAbsent(id, upper)
            total++
        }

        val frames = counts.entries
            .sortedByDescending { it.value }
            .map { (id, count) -> CanFrameSummary(id, count, samples[id] ?: "") }

        return CanMonitorResult(
            frames = frames,
            totalFrames = total,
            durationMs = durationMs,
            raw = raw.take(MAX_RAW_CHARS)
        )
    }

    /** 로그에 남길 원시 내용의 최대 길이. 수신량이 많으면 파일이 커진다. */
    private const val MAX_RAW_CHARS = 4000
}
