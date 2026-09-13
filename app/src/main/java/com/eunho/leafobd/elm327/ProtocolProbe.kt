package com.eunho.leafobd.elm327

import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.obd.ObdProtocol
import com.eunho.leafobd.util.ResponseText

/** 프로토콜 한 개를 시험한 결과. */
enum class ProbeOutcome(val label: String) {
    /** 차량이 정상 응답했다. 이 프로토콜을 쓰면 된다. */
    RESPONDED("응답함"),

    /**
     * 제한 시간 내 유효한 응답을 확인하지 못했다(`NO DATA`).
     * 프로토콜은 맞을 가능성이 있으나 해당 모드를 지원하지 않는 상태일 수 있다.
     */
    CONNECTED_NO_DATA("응답 미확인(데이터 없음)"),

    /** 연결 자체가 되지 않았다(`UNABLE TO CONNECT`, 타임아웃 등). */
    FAILED("연결 실패")
}

/**
 * 프로토콜 탐색 결과.
 *
 * @param protocol 실제로 쓰기로 정한 프로토콜. 끝내 실패하면 null.
 * @param outcome 위 프로토콜의 시험 결과
 * @param attempted 시도한 프로토콜과 각각의 결과 (화면·로그에 그대로 보여 준다)
 * @param logs 이 과정에서 오간 모든 명령
 */
data class ProtocolProbeResult(
    val protocol: ObdProtocol?,
    val outcome: ProbeOutcome,
    val attempted: List<Pair<ObdProtocol, ProbeOutcome>> = emptyList(),
    val logs: List<CommandLog> = emptyList(),
    val protocolName: String? = null
) {
    val success: Boolean get() = outcome != ProbeOutcome.FAILED

    /** 자동 선택이 실패해 순차 시도로 찾아낸 경우. 설정 고정을 안내하기 위해 쓴다. */
    val foundBySweep: Boolean
        get() = success && attempted.size > 1
}

/**
 * 차량과 실제로 통신이 되는 프로토콜을 찾는다.
 *
 * 왜 필요한가:
 * `ATSP0`(자동 선택)은 어댑터가 스스로 프로토콜을 찾게 하는 방식인데,
 * ELM327 클론에서는 이 탐색이 실패해 `UNABLE TO CONNECT` 만 반복하는 경우가 흔하다.
 * 이때 프로토콜을 손으로 지정하면 정상 통신되는 사례가 많다.
 *
 * 여기서 쓰는 `ATSP<n>` 과 `ATDPN` 은 모두 ELM327 데이터시트에 공개된 표준 명령이며
 * 제조사 전용 명령이 아니다. 차량에 보내는 것은 표준 OBD 요청(`0100`) 하나뿐이다.
 */
class ProtocolProbe(private val client: Elm327Client) {

    /**
     * @param preferred 설정에서 고른 프로토콜. [ObdProtocol.AUTO] 면 자동 선택을 먼저 시도한다.
     * @param sweepOnFailure 자동/지정 프로토콜이 실패했을 때 후보를 순서대로 시도할지
     */
    suspend fun probe(
        preferred: ObdProtocol = ObdProtocol.AUTO,
        sweepOnFailure: Boolean = true
    ): ProtocolProbeResult {
        val logs = ArrayList<CommandLog>()
        val attempted = ArrayList<Pair<ObdProtocol, ProbeOutcome>>()

        // 1) 설정된(또는 자동) 프로토콜을 먼저 시도한다.
        val first = tryProtocol(preferred, logs)
        attempted.add(preferred to first)
        if (first == ProbeOutcome.RESPONDED) {
            return finish(preferred, first, attempted, logs)
        }

        // 2) 실패하면 후보를 순서대로 시도한다.
        if (sweepOnFailure) {
            for (candidate in ObdProtocol.SWEEP_CANDIDATES) {
                if (candidate == preferred) continue
                val outcome = tryProtocol(candidate, logs)
                attempted.add(candidate to outcome)
                if (outcome == ProbeOutcome.RESPONDED) {
                    return finish(candidate, outcome, attempted, logs)
                }
            }
        }

        // 3) 정상 응답은 없지만 "버스 연결은 된" 프로토콜이 있으면 그것을 쓴다.
        attempted.firstOrNull { it.second == ProbeOutcome.CONNECTED_NO_DATA }?.let { (protocol, outcome) ->
            // 마지막에 시도한 프로토콜이 남아 있으므로 다시 지정해 준다.
            if (protocol != ObdProtocol.AUTO) {
                logs.add(client.send(protocol.setCommand))
            }
            return finish(protocol, outcome, attempted, logs)
        }

        return ProtocolProbeResult(
            protocol = null,
            outcome = ProbeOutcome.FAILED,
            attempted = attempted,
            logs = logs
        )
    }

    private suspend fun finish(
        protocol: ObdProtocol,
        outcome: ProbeOutcome,
        attempted: List<Pair<ObdProtocol, ProbeOutcome>>,
        logs: MutableList<CommandLog>
    ): ProtocolProbeResult {
        // 어댑터가 실제로 어떤 프로토콜을 쓰고 있는지 이름으로 확인해 로그에 남긴다.
        val describe = client.send(Elm327Command.DESCRIBE_PROTOCOL)
        logs.add(describe)
        val name = ResponseText.dataLines(describe.rawResponse, Elm327Command.DESCRIBE_PROTOCOL.command)
            .joinToString(" ")
            .ifBlank { null }

        return ProtocolProbeResult(
            protocol = protocol,
            outcome = outcome,
            attempted = attempted,
            logs = logs,
            protocolName = name
        )
    }

    /** 프로토콜을 지정하고 표준 요청 `0100` 으로 실제 통신이 되는지 확인한다. */
    private suspend fun tryProtocol(
        protocol: ObdProtocol,
        logs: MutableList<CommandLog>
    ): ProbeOutcome {
        val setLog = client.send(protocol.setCommand)
        logs.add(setLog)
        if (!setLog.success) return ProbeOutcome.FAILED

        val probeLog = client.send(PROBE_COMMAND, Elm327Command.OBD_TIMEOUT_MS)
        logs.add(probeLog)

        return when {
            // 41 = Mode 01 정상 응답 머리값
            probeLog.success && probeLog.rawResponse.replace(" ", "").contains("4100") ->
                ProbeOutcome.RESPONDED

            ResponseText.isNoData(probeLog.rawResponse) -> ProbeOutcome.CONNECTED_NO_DATA

            else -> ProbeOutcome.FAILED
        }
    }

    private companion object {
        /**
         * 프로토콜 확인용 요청.
         * `0100`(지원 PID 조회)은 OBD-II 를 따르는 차량이면 반드시 응답해야 하는 항목이라
         * 통신 가능 여부를 판단하기에 가장 적합하다.
         */
        const val PROBE_COMMAND = "0100"
    }
}
