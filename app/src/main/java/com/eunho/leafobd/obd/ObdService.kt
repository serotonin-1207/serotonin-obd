package com.eunho.leafobd.obd

import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.Elm327Command
import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.util.ResponseText

/** DTC 조회 한 건의 결과(원시 로그 + 해석 결과). */
data class DtcReadOutcome(
    val mode: ObdMode,
    val log: CommandLog,
    val result: DtcParseResult
)

/** Mode 04 실행 결과. */
data class ClearOutcome(
    val log: CommandLog,
    /** 어댑터가 삭제 명령을 받아들였는지. 고장이 수리되었다는 뜻은 결코 아니다. */
    val accepted: Boolean,
    val message: String
)

/** Mode 01 기본 조회 결과. */
data class VehicleInfoOutcome(
    val logs: List<CommandLog> = emptyList(),
    val monitorStatus: MonitorStatus? = null,
    val supportedPids: SupportedPids? = null,
    val liveValues: List<PidValue> = emptyList(),
    val vin: String? = null
)

/**
 * 표준 OBD-II 명령만 사용하는 진단 서비스.
 *
 * Nissan 전용 명령은 이 클래스에 넣지 않는다.
 * 검증된 자료가 확보되면 `nissan` 패키지에 별도 모듈로 추가한다.
 *
 * @param headersOn `ATH1` 로 초기화했으면 true. 파서가 ECU별로 나누어 해석한다.
 */
class ObdService(
    private val client: Elm327Client,
    private val headersOn: Boolean = false
) {

    // ------------------------------------------------------------------
    // Mode 03 / 07 / 0A — DTC 읽기
    // ------------------------------------------------------------------

    suspend fun readDtcs(mode: ObdMode): DtcReadOutcome {
        val log = client.send(mode.command, Elm327Command.OBD_TIMEOUT_MS)
        val result = DtcParser.parse(log.rawResponse, mode, headersOn)
        return DtcReadOutcome(mode, log, result)
    }

    /** 저장 → 보류 → 영구 순서로 모두 읽는다. */
    suspend fun readAllDtcs(): List<DtcReadOutcome> =
        ObdMode.entries.map { readDtcs(it) }

    // ------------------------------------------------------------------
    // Mode 01 — 모니터 상태 · 지원 PID · 실시간 값
    // ------------------------------------------------------------------

    /** `0101` — 경고등(MIL) 점등 여부와 ECU가 보고하는 DTC 개수. */
    suspend fun readMonitorStatus(): Pair<CommandLog, MonitorStatus?> {
        val log = client.send("0101", Elm327Command.OBD_TIMEOUT_MS)
        return log to Mode01Parser.parseMonitorStatus(log.rawResponse, headersOn)
    }

    /**
     * `0100`, `0120`, `0140` … 을 이어서 조회해 지원 PID 전체 목록을 만든다.
     *
     * 다음 구간을 조회할지는 비트맵 최하위 비트(구간 경계값)로 판단한다.
     * 지원하지 않는 PID를 묻지 않게 되어 불필요한 타임아웃을 줄인다.
     */
    suspend fun readSupportedPids(): Pair<List<CommandLog>, SupportedPids> {
        val logs = ArrayList<CommandLog>()
        val ids = LinkedHashSet<Int>()
        val raw = StringBuilder()

        var base = 0x00
        while (base <= MAX_PID_BASE) {
            val command = "01%02X".format(base)
            val log = client.send(command, Elm327Command.OBD_TIMEOUT_MS)
            logs.add(log)

            if (!log.success || ResponseText.isNoData(log.rawResponse)) break

            val block = Mode01Parser.parseSupportedPids(
                rawResponse = log.rawResponse,
                base = base,
                command = command,
                headersOn = headersOn
            )
            if (block.ids.isEmpty()) break

            ids.addAll(block.ids)
            if (raw.isNotEmpty()) raw.append(" / ")
            raw.append("%02X:".format(base)).append(block.rawHex)

            // 구간 경계값(0x20, 0x40 …)이 지원 목록에 있으면 다음 구간도 조회할 수 있다.
            val nextBase = base + 0x20
            if (nextBase !in ids) break
            base = nextBase
        }

        return logs to SupportedPids(ids, raw.toString())
    }

    /** 지원 목록에 있는 PID 중 해석 가능한 것만 실시간으로 읽는다. */
    suspend fun readLiveValues(supported: SupportedPids): Pair<List<CommandLog>, List<PidValue>> {
        val logs = ArrayList<CommandLog>()
        val values = ArrayList<PidValue>()

        for (pid in Pid.entries) {
            if (!supported.supports(pid)) continue
            val command = "01${pid.hex}"
            val log = client.send(command, Elm327Command.OBD_TIMEOUT_MS)
            logs.add(log)
            if (!log.success) continue

            Mode01Parser.parsePidValue(
                rawResponse = log.rawResponse,
                pidId = pid.id,
                command = command,
                requestMode = 0x01,
                headersOn = headersOn
            )?.let { values.add(it) }
        }

        return logs to values
    }

    // ------------------------------------------------------------------
    // Mode 02 — 프리즈 프레임
    // ------------------------------------------------------------------

    /**
     * 프리즈 프레임을 읽는다.
     *
     * **반드시 Mode 04(삭제) 전에 호출해야 한다.** 삭제하면 사라지는 값이다.
     *
     * 해석하지 못한 PID도 원시 응답을 그대로 보존한다.
     * 나중에 검증된 자료가 생기면 저장된 원시값으로 다시 해석할 수 있어야 하기 때문이다.
     */
    suspend fun readFreezeFrame(): Pair<List<CommandLog>, FreezeFrame> {
        val logs = ArrayList<CommandLog>()
        val rawResponses = LinkedHashMap<String, String>()

        // 1) 지원하는 프리즈 프레임 PID 조회
        val supportedCommand = "020000"
        val supportedLog = client.send(supportedCommand, Elm327Command.OBD_TIMEOUT_MS)
        logs.add(supportedLog)

        if (!supportedLog.success || ResponseText.isNoData(supportedLog.rawResponse)) {
            return logs to FreezeFrame(rawResponses = mapOf(supportedCommand to supportedLog.rawResponse))
        }
        rawResponses[supportedCommand] = supportedLog.rawResponse

        val supported = Mode01Parser.parseSupportedPids(
            rawResponse = supportedLog.rawResponse,
            base = 0x00,
            command = supportedCommand,
            responsePrefix = 0x42,
            // Mode 02 응답은 PID 뒤에 프레임 번호가 1바이트 더 붙는다.
            extraSkip = 1,
            headersOn = headersOn
        )

        // 2) 프리즈 프레임을 저장시킨 원인 DTC
        var triggerDtc: String? = null
        if (supported.supports(FreezeFrameParser.PID_TRIGGER_DTC) || supported.ids.isEmpty()) {
            val command = "020200"
            val log = client.send(command, Elm327Command.OBD_TIMEOUT_MS)
            logs.add(log)
            rawResponses[command] = log.rawResponse
            if (log.success) {
                triggerDtc = FreezeFrameParser.parseTriggerDtc(log.rawResponse, command, headersOn)
            }
        }

        // 3) 해석 가능한 PID 값
        val values = ArrayList<PidValue>()
        for (pid in Pid.entries) {
            if (!supported.supports(pid)) continue
            val command = "02${pid.hex}00"
            val log = client.send(command, Elm327Command.OBD_TIMEOUT_MS)
            logs.add(log)
            rawResponses[command] = log.rawResponse
            if (!log.success) continue

            Mode01Parser.parsePidValue(
                rawResponse = log.rawResponse,
                pidId = pid.id,
                command = command,
                requestMode = 0x02,
                headersOn = headersOn
            )?.let { values.add(it) }
        }

        return logs to FreezeFrame(
            triggerDtc = triggerDtc,
            values = values,
            rawResponses = rawResponses,
            supportedPids = supported.ids
        )
    }

    // ------------------------------------------------------------------
    // Mode 09 — 차량 정보
    // ------------------------------------------------------------------

    /**
     * `0902` — 차대번호(VIN).
     *
     * 통신이 정상인지 확인하는 가장 확실한 방법이기도 하다.
     * VIN 은 식별정보이므로 파일에 저장할 때는 마스킹한다.
     */
    suspend fun readVin(): Pair<CommandLog, String?> {
        val log = client.send("0902", Elm327Command.OBD_TIMEOUT_MS)
        return log to VinParser.parse(log.rawResponse, headersOn)
    }

    // ------------------------------------------------------------------
    // Mode 04 — 삭제
    // ------------------------------------------------------------------

    /**
     * 표준 DTC 삭제(Mode 04)를 **1회만** 실행한다.
     *
     * 이 함수는 재시도하지 않는다. 반복 삭제는 지시서에서 금지한 동작이다.
     * 호출 전 조건 검증은 상위 계층(ViewModel)이 담당한다.
     */
    suspend fun clearDtcs(): ClearOutcome {
        val log = client.send(ObdClear.COMMAND, Elm327Command.OBD_TIMEOUT_MS)

        val dataLines = ResponseText.dataLines(log.rawResponse, ObdClear.COMMAND)
        val responses = ObdFrameParser.parse(dataLines, headersOn)
        val accepted = log.success && responses.any { it.bytes.firstOrNull() == ObdClear.RESPONSE_PREFIX }

        val message = when {
            !log.success -> log.errorMessage ?: "삭제 명령이 실패했습니다."
            accepted -> "어댑터가 삭제 명령(44)을 정상 수신했습니다. 실제로 어떤 코드가 지워졌는지는 재조회로 확인해야 합니다."
            ResponseText.isNoData(log.rawResponse) ->
                "차량이 삭제 명령에 응답하지 않았습니다(NO DATA). 표준 삭제를 지원하지 않는 상태일 수 있습니다."
            else -> "예상과 다른 응답을 받았습니다. 원시 응답을 확인하십시오."
        }

        return ClearOutcome(log = log, accepted = accepted, message = message)
    }

    private companion object {
        /** 지원 PID 조회를 이어 갈 최대 구간. 0xC0 까지면 PID 0xE0 범위를 덮는다. */
        const val MAX_PID_BASE = 0xC0
    }
}
