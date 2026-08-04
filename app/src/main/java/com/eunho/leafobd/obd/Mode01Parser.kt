package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/**
 * `0101` 응답 — 경고등 상태와 저장된 DTC 개수.
 *
 * 응답 형식: `41 01 A B C D`
 *  - A 의 최상위 비트: MIL(체크 엔진 / 경고등) 점등 여부
 *  - A 의 하위 7비트: ECU 가 보고하는 저장 DTC 개수
 *  - B, C, D: 준비 모니터 상태 비트 (내연기관용이 대부분이라 해석하지 않고 원시값만 보존)
 */
data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val rawHex: String,
    val ecu: String? = null
) {
    val milLabel: String get() = if (milOn) "점등" else "소등"
}

/** `0100` 계열 응답에서 얻은 "이 차량이 지원하는 PID" 목록. */
data class SupportedPids(
    val ids: Set<Int>,
    val rawHex: String
) {
    fun supports(pid: Pid): Boolean = pid.id in ids
    fun supports(id: Int): Boolean = id in ids
}

/** Mode 01 / Mode 09 응답 해석. */
object Mode01Parser {

    /**
     * `0100`, `0120`, `0140` … 응답의 32비트 비트맵을 해석한다.
     *
     * 응답 `41 00 BE 3E B8 11` 에서 4바이트(BE 3E B8 11)가 비트맵이며,
     * 최상위 비트가 `base + 1`, 최하위 비트가 `base + 0x20` 에 대응한다.
     * 최하위 비트가 1이면 "다음 구간(0x20 단위)도 조회할 수 있다"는 뜻이다.
     *
     * @param base 조회한 구간의 시작값 (0x00, 0x20, 0x40 …)
     * @param responsePrefix Mode 01 은 0x41, Mode 02(프리즈 프레임)는 0x42
     * @param extraSkip 머리값·PID 다음에 더 건너뛸 바이트 수.
     *   Mode 02 응답은 PID 뒤에 프레임 번호가 1바이트 더 붙으므로 1을 넘긴다.
     */
    fun parseSupportedPids(
        rawResponse: String,
        base: Int,
        command: String,
        responsePrefix: Int = 0x41,
        extraSkip: Int = 0,
        headersOn: Boolean = false
    ): SupportedPids {
        val lines = ResponseText.dataLines(rawResponse, command)
        val responses = ObdFrameParser.parse(lines, headersOn)

        val ids = LinkedHashSet<Int>()
        val raw = StringBuilder()

        for (response in responses) {
            val bytes = response.bytes
            val prefixIndex = bytes.indexOf(responsePrefix)
            if (prefixIndex < 0) continue
            // 머리값 다음 바이트는 조회한 PID 번호(예: 00, 20)이고 그다음 4바이트가 비트맵이다.
            val bitmap = bytes.drop(prefixIndex + 2 + extraSkip).take(4)
            if (bitmap.size < 4) continue

            if (raw.isNotEmpty()) raw.append(" ")
            raw.append(ResponseText.formatHex(bitmap))

            bitmap.forEachIndexed { byteIndex, value ->
                for (bit in 0 until 8) {
                    // 최상위 비트부터 base+1, base+2 … 순서
                    val isSet = (value shr (7 - bit)) and 1 == 1
                    if (isSet) ids.add(base + byteIndex * 8 + bit + 1)
                }
            }
        }

        return SupportedPids(ids, raw.toString())
    }

    /** `0101` 응답에서 MIL 상태와 DTC 개수를 읽는다. */
    fun parseMonitorStatus(rawResponse: String, headersOn: Boolean = false): MonitorStatus? {
        val lines = ResponseText.dataLines(rawResponse, "0101")
        val responses = ObdFrameParser.parse(lines, headersOn)

        for (response in responses) {
            val bytes = response.bytes
            val prefixIndex = bytes.indexOf(0x41)
            if (prefixIndex < 0) continue
            // 41 01 A ...
            if (bytes.getOrNull(prefixIndex + 1) != 0x01) continue
            val a = bytes.getOrNull(prefixIndex + 2) ?: continue

            return MonitorStatus(
                milOn = (a and 0x80) != 0,
                dtcCount = a and 0x7F,
                rawHex = ResponseText.formatHex(bytes.drop(prefixIndex).take(6)),
                ecu = response.ecuId
            )
        }
        return null
    }

    /**
     * Mode 01 단일 PID 응답(`41 <PID> <데이터…>`)을 해석한다.
     *
     * @param mode 요청 모드. 01(실시간) 또는 02(프리즈 프레임)
     */
    fun parsePidValue(
        rawResponse: String,
        pidId: Int,
        command: String,
        requestMode: Int = 0x01,
        headersOn: Boolean = false
    ): PidValue? {
        val lines = ResponseText.dataLines(rawResponse, command)
        val responses = ObdFrameParser.parse(lines, headersOn)
        val prefix = requestMode + 0x40

        for (response in responses) {
            val bytes = response.bytes
            var index = bytes.indexOf(prefix)
            while (index >= 0) {
                if (bytes.getOrNull(index + 1) == pidId) {
                    // 프리즈 프레임(Mode 02)은 PID 다음에 프레임 번호 1바이트가 더 붙는다.
                    val dataStart = index + 2 + (if (requestMode == 0x02) 1 else 0)
                    val expected = Pid.of(pidId)?.bytes ?: (bytes.size - dataStart)
                    val data = bytes.drop(dataStart).take(expected.coerceAtLeast(1))
                    if (data.isNotEmpty()) return PidDecoder.decode(pidId, data)
                }
                index = bytes.indexOf(prefix, index + 1)
            }
        }
        return null
    }

    private fun List<Int>.indexOf(value: Int, from: Int): Int {
        for (i in from until size) if (this[i] == value) return i
        return -1
    }
}
