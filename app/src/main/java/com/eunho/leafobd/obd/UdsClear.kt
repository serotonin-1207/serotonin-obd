package com.eunho.leafobd.obd

import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.Elm327Command
import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.util.ResponseText

/** ECU 한 곳의 삭제 결과. */
data class UdsClearOutcome(
    val ecu: String,
    val accepted: Boolean,
    val raw: String,
    val negativeCode: Int? = null,
    val message: String
) {
    val negativeReason: String? get() = negativeCode?.let { UdsNegativeResponse.describe(it) }
}

/** 삭제 전체 결과와 전후 비교. */
data class UdsClearResult(
    val outcomes: List<UdsClearOutcome> = emptyList(),
    val before: List<UdsDtcCode> = emptyList(),
    val after: List<UdsDtcCode> = emptyList(),
    val durationMs: Long = 0,
    val errorMessage: String? = null,
    val verificationComplete: Boolean = false
) {
    /** 삭제 명령을 받아들인 ECU 수. */
    val acceptedCount: Int get() = outcomes.count { it.accepted }

    /** 사라진 코드. */
    val cleared: List<UdsDtcCode>
        get() {
            if (!verificationComplete) return emptyList()
            val afterCodes = after.map { it.ecu to it.fullCode }.toSet()
            return before.filterNot { (it.ecu to it.fullCode) in afterCodes }
        }

    /** 삭제 후에도 남은 코드. 활성 고장일 가능성이 크다. */
    val remaining: List<UdsDtcCode>
        get() {
            val beforeCodes = before.map { it.ecu to it.fullCode }.toSet()
            return after.filter { (it.ecu to it.fullCode) in beforeCodes }
        }

    /** 삭제 후 새로 나타난 코드. */
    val appeared: List<UdsDtcCode>
        get() {
            val beforeCodes = before.map { it.ecu to it.fullCode }.toSet()
            return after.filterNot { (it.ecu to it.fullCode) in beforeCodes }
        }

    val hasRecurrence: Boolean get() = remaining.isNotEmpty() || appeared.isNotEmpty()

    /**
     * 남아 있지만 **상태 비트가 달라진** 코드.
     *
     * 코드가 사라지지 않았어도 "현재 고장" 비트가 꺼졌다면 의미 있는 변화다.
     * 반대로 그대로면 ECU 가 지금도 같은 고장을 보고 있다는 뜻이다.
     */
    val statusChanged: List<Triple<UdsDtcCode, Int, Int>>
        get() {
            val beforeByCode = before.associateBy { it.ecu to it.fullCode }
            return after.mapNotNull { now ->
                val was = beforeByCode[now.ecu to now.fullCode] ?: return@mapNotNull null
                if (was.statusByte == now.statusByte) null
                else Triple(now, was.statusByte, now.statusByte)
            }
        }

    /** 삭제 후에도 **여전히 현재 고장 상태**인 코드. 이것이 실제 원인이다. */
    val stillFailing: List<UdsDtcCode> get() = after.filter { it.currentlyFailing }

    val summary: String
        get() = when {
            errorMessage != null -> errorMessage
            !verificationComplete -> "삭제 후 재조회가 완전하지 않습니다. 코드 소거 여부는 확인 불가입니다. 확인된 코드 ${after.size}건."
            after.isEmpty() && before.isNotEmpty() ->
                "코드 ${before.size}건이 모두 사라졌습니다. " +
                    "다만 고장 원인이 남아 있으면 주행·충전 중 다시 나타납니다."

            stillFailing.isNotEmpty() ->
                "삭제 후에도 ${stillFailing.size}건이 **현재 고장 상태**로 남아 있습니다: " +
                    stillFailing.joinToString(", ") { it.fullCode } + "\n" +
                    "ECU 가 지금도 그 고장을 감지하고 있습니다. 반복해서 지워도 결과는 같습니다."

            hasRecurrence ->
                "코드 ${cleared.size}건이 사라지고 ${remaining.size + appeared.size}건이 남았습니다. " +
                    "남은 코드는 현재 고장 상태는 아닙니다."

            else -> "삭제 명령을 ${acceptedCount}개 ECU 가 받아들였습니다."
        }
}

/**
 * ECU 오류코드 삭제 (ISO 14229 service 0x14, ClearDiagnosticInformation).
 *
 * ## 이 명령이 하는 일과 하지 않는 일
 *
 * **하는 일**: ECU 에 저장된 고장 기록을 지우고, ECU 가 처음부터 다시 판단하게 한다.
 *
 * **하지 않는 일**: 고장 감지 기능을 끄지 않는다. 보호 로직은 그대로 살아 있다.
 * 고장 조건이 남아 있으면 ECU 는 곧바로 다시 검출하고 같은 코드를 다시 세운다.
 * 접촉기를 강제로 닫거나 충전을 강제로 시작하는 명령이 **아니다.**
 * 그런 명령은 이 앱에 없으며 앞으로도 넣지 않는다.
 *
 * ## 호출 전 조건
 *
 * 조건 검증은 상위 계층([com.eunho.leafobd.viewmodel.UdsClearEligibility])이 담당한다.
 * 이 클래스는 검증을 통과했다고 가정하고 명령만 보낸다.
 */
class UdsClear(private val client: Elm327Client) {

    /**
     * @param addresses 삭제할 ECU 주소 목록
     */
    /**
     * @param extendedSession 거부하는 ECU 에 확장 진단 세션(`10 03`)을 먼저 요청할지.
     *   일부 ECU 는 기본 세션에서 삭제를 받지 않는다. ISO 14229 표준 요청이며,
     *   세션은 일정 시간 뒤 자동으로 기본 세션으로 돌아간다.
     *   값을 쓰거나 잠금을 푸는 명령이 아니다.
     */
    suspend fun clear(
        addresses: List<String>,
        extendedSession: Boolean = false,
        onProgress: suspend (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Pair<List<CommandLog>, List<UdsClearOutcome>> {
        val logs = ArrayList<CommandLog>()
        val outcomes = ArrayList<UdsClearOutcome>()

        logs.add(client.send(Elm327Command.HEADERS_ON))

        try {
            addresses.forEachIndexed { index, address ->
                onProgress(index + 1, addresses.size, address)

                val setHeader = client.send(Elm327Command("ATSH$address", "요청 주소 $address"))
                logs.add(setHeader)
                if (!setHeader.success) return@forEachIndexed

                EcuScanner.flowControlCommands(address).forEach { logs.add(client.send(it)) }

                // 14 FF FF FF — 모든 그룹의 고장 기록 삭제 (ISO 14229 표준)
                var log = client.send(CLEAR_COMMAND, CLEAR_TIMEOUT_MS)
                logs.add(log)
                var outcome = parse(log.rawResponse, address)

                // 거부당했고 확장 세션이 허용되면 세션을 바꿔 한 번 더 시도한다.
                val retryable = outcome.negativeCode == NRC_SERVICE_NOT_SUPPORTED ||
                    outcome.negativeCode == NRC_CONDITIONS_NOT_CORRECT ||
                    outcome.negativeCode == NRC_SUBFUNCTION_NOT_SUPPORTED
                if (extendedSession && !outcome.accepted && retryable) {
                    logs.add(client.send(EXTENDED_SESSION, SESSION_TIMEOUT_MS))
                    log = client.send(CLEAR_COMMAND, CLEAR_TIMEOUT_MS)
                    logs.add(log)
                    val retried = parse(log.rawResponse, address)
                    if (retried.accepted) {
                        outcome = retried.copy(message = retried.message + " (확장 세션 후)")
                    }
                }

                outcomes.add(outcome)
            }
        } finally {
            logs.add(client.send(Elm327Command("ATSH7DF", "요청 주소 복귀", optional = true)))
        }

        return logs to outcomes
    }

    private fun parse(rawResponse: String, address: String): UdsClearOutcome {
        val lines = ResponseText.dataLines(rawResponse, CLEAR_COMMAND)
        val responses = ObdFrameParser.parse(lines, headersOn = true)

        for (response in responses) {
            val bytes = response.usableBytes

            // 7F 14 <NRC> — 거부
            val negIndex = bytes.indexOf(0x7F)
            if (negIndex >= 0 && bytes.getOrNull(negIndex + 1) == 0x14) {
                val nrc = bytes.getOrNull(negIndex + 2)
                return UdsClearOutcome(
                    ecu = address,
                    accepted = false,
                    raw = rawResponse.trim(),
                    negativeCode = nrc,
                    message = "ECU 가 삭제를 거부했습니다" +
                        (nrc?.let { " — ${UdsNegativeResponse.describe(it)}" } ?: "")
                )
            }

            // 54 — 정상 응답
            if (bytes.contains(POSITIVE_SID)) {
                return UdsClearOutcome(
                    ecu = address,
                    accepted = true,
                    raw = rawResponse.trim(),
                    message = "삭제 명령을 받아들였습니다."
                )
            }
        }

        return UdsClearOutcome(
            ecu = address,
            accepted = false,
            raw = rawResponse.trim(),
            message = if (ResponseText.isNoData(rawResponse)) "응답 없음"
            else "예상과 다른 응답입니다."
        )
    }

    private companion object {
        /**
         * ISO 14229 ClearDiagnosticInformation.
         * `FF FF FF` = 모든 고장 그룹.
         */
        const val CLEAR_COMMAND = "14FFFFFF"

        /** 정상 응답 머리값 (0x14 + 0x40). */
        const val POSITIVE_SID = 0x54

        /** 삭제는 ECU 내부 처리가 있어 응답이 느릴 수 있다. */
        const val CLEAR_TIMEOUT_MS = 8_000L

        /**
         * ISO 14229 DiagnosticSessionControl — 확장 진단 세션(0x03).
         * 값을 쓰거나 잠금을 푸는 명령이 아니며, 일정 시간 뒤 기본 세션으로 자동 복귀한다.
         */
        const val EXTENDED_SESSION = "1003"
        const val SESSION_TIMEOUT_MS = 3_000L

        const val NRC_SERVICE_NOT_SUPPORTED = 0x11
        const val NRC_SUBFUNCTION_NOT_SUPPORTED = 0x12
        const val NRC_CONDITIONS_NOT_CORRECT = 0x22
    }
}
