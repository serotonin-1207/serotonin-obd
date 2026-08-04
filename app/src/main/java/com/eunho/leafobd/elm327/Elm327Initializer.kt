package com.eunho.leafobd.elm327

import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.obd.ObdProtocol
import com.eunho.leafobd.obd.VoltageParser
import com.eunho.leafobd.obd.VoltageReading
import com.eunho.leafobd.util.ResponseText
import kotlinx.coroutines.delay

/**
 * 초기화 결과.
 *
 * @param failedCommand 실패한 명령. 성공했으면 null.
 *   "초기화 중 어떤 명령에서 실패했는지 표시한다"는 요구사항을 위해 보관한다.
 */
data class Elm327InitResult(
    val success: Boolean,
    val logs: List<CommandLog>,
    val adapterInfo: String?,
    val voltage: VoltageReading?,
    val protocol: String?,
    val failedCommand: String? = null,
    val errorMessage: String? = null,
    /** 프로토콜 탐색 결과. 어느 프로토콜로 통신이 되었는지 담고 있다. */
    val probe: ProtocolProbeResult? = null
)

/**
 * ELM327 기본 초기화 시퀀스를 실행한다.
 *
 * 순서: ATZ → ATE0 → ATL0 → ATS0 → ATH0 → ATSP<n> → ATI → ATRV → 프로토콜 확인
 *
 * 각 명령은 이전 명령의 프롬프트가 확인된 뒤에 전송된다([Elm327Client]가 직렬화).
 *
 * @param protocol 사용할 프로토콜. [ObdProtocol.AUTO] 면 어댑터 자동 선택.
 * @param sweepOnFailure 자동/지정 프로토콜로 통신이 안 될 때 후보를 순서대로 시도할지.
 *   ELM327 클론에서 자동 탐색이 실패해 `UNABLE TO CONNECT` 만 반복되는 사례가 흔하다.
 */
class Elm327Initializer(
    private val client: Elm327Client,
    /** true 면 ATH1 로 CAN 헤더를 켜서 어느 ECU가 응답했는지 확인할 수 있게 한다. */
    private val headersOn: Boolean = false,
    private val protocol: ObdProtocol = ObdProtocol.AUTO,
    private val sweepOnFailure: Boolean = true
) {

    suspend fun initialize(): Elm327InitResult {
        val logs = ArrayList<CommandLog>()
        var adapterInfo: String? = null
        var voltage: VoltageReading? = null
        var protocolName: String? = null

        for (command in Elm327Command.initSequence(headersOn, protocol.code)) {
            val log = client.send(command)
            logs.add(log)

            if (!log.success && !command.optional) {
                return Elm327InitResult(
                    success = false,
                    logs = logs,
                    adapterInfo = adapterInfo,
                    voltage = voltage,
                    protocol = protocolName,
                    failedCommand = command.command,
                    errorMessage = "[${command.command} / ${command.label}] 단계에서 실패했습니다. " +
                        (log.errorMessage ?: "어댑터가 정상 응답하지 않았습니다.")
                )
            }

            when (command.command) {
                Elm327Command.IDENTIFY.command -> {
                    // 주의: 여기 표시되는 문자열은 어댑터가 스스로 보고하는 값이다.
                    // "ELM327 v2.3" 이라고 나와도 정품 여부를 단정할 수 없다.
                    adapterInfo = ResponseText.dataLines(log.rawResponse, command.command)
                        .joinToString(" ")
                        .ifBlank { null }
                }

                Elm327Command.READ_VOLTAGE.command -> {
                    voltage = VoltageParser.parse(log.rawResponse)
                }
            }

            // ATZ 직후에는 어댑터가 재시작 중이라 곧바로 다음 명령을 받지 못할 수 있다.
            if (command.command == Elm327Command.RESET.command) {
                delay(RESET_SETTLE_MS)
            }
        }

        // 실제로 차량과 통신되는 프로토콜을 확인한다.
        //
        // 어댑터 초기화가 성공해도 차량 버스에 붙지 못하는 경우가 있어
        // (ELM327 클론의 자동 탐색 실패), 여기서 표준 요청 `0100` 으로 실제 통신을 확인하고
        // 실패하면 후보 프로토콜을 순서대로 시도한다.
        val probe = ProtocolProbe(client).probe(protocol, sweepOnFailure)
        logs.addAll(probe.logs)
        protocolName = probe.protocolName

        // 프로토콜을 찾지 못해도 초기화 자체는 성공으로 본다.
        // 어댑터는 정상이며, 원시 응답이 남아 있어야 원인을 판단할 수 있기 때문이다.
        return Elm327InitResult(
            success = true,
            logs = logs,
            adapterInfo = adapterInfo,
            voltage = voltage,
            protocol = protocolName,
            probe = probe
        )
    }

    private companion object {
        /** ATZ(리셋) 후 어댑터가 안정될 때까지 기다리는 시간. */
        const val RESET_SETTLE_MS = 700L
    }
}
