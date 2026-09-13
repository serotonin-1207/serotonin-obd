package com.eunho.leafobd.obd

import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.Elm327Command
import com.eunho.leafobd.log.CommandLog

/** ECU 여러 곳의 오류코드를 모아 읽은 결과. */
data class UdsDiagnosticsResult(
    val results: List<UdsDtcReadResult> = emptyList(),
    val vin: String? = null,
    val durationMs: Long = 0,
    val errorMessage: String? = null
) {
    /** 코드가 하나라도 있는 ECU. */
    val withCodes: List<UdsDtcReadResult> get() = results.filter { it.hasCodes }

    val allCodes: List<UdsDtcCode> get() = results.flatMap { it.codes }

    /** 지금도 고장 상태이거나 확정된 코드 — 우선 살펴야 할 것들. */
    val activeCodes: List<UdsDtcCode>
        get() = allCodes.filter { it.currentlyFailing || it.confirmed }

    val summary: String
        get() = when {
            errorMessage != null -> errorMessage
            results.isEmpty() -> "조회한 ECU 가 없습니다."
            results.any { !it.complete } -> "일부 ECU의 응답을 확인하지 못했습니다. 확인된 코드 ${allCodes.size}건 · 전체 정상 여부 확인 불가"
            allCodes.isEmpty() -> "ECU ${results.size}곳을 확인했고 저장된 오류코드가 없습니다."
            else ->
                "ECU ${results.size}곳 중 ${withCodes.size}곳에서 오류코드 ${allCodes.size}건을 찾았습니다."
        }
}

/**
 * 응답하는 ECU 주소에 **UDS 표준 읽기 요청**을 보내 오류코드와 차대번호를 읽는다.
 *
 * [EcuScanner] 로 응답하는 주소를 찾은 뒤 이 클래스로 실제 값을 읽는다.
 *
 * ## 안전
 *
 * 보내는 것은 `19 02`(오류코드 읽기)와 `22 F1 90`(차대번호 읽기) 뿐이다.
 * 둘 다 ISO 14229 표준 읽기 서비스이며 차량 상태를 바꾸지 않는다.
 * 삭제(`14`), 쓰기(`2E`), 세션 제어(`10`), 보안 접근(`27`)은 보내지 않는다.
 */
class UdsDiagnostics(private val client: Elm327Client) {

    /**
     * @param addresses 조회할 ECU 주소 목록 ([EcuScanner] 결과)
     * @param readVin 차대번호도 읽을지
     */
    suspend fun readAll(
        addresses: List<String>,
        readVin: Boolean = false,
        onProgress: suspend (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Pair<List<CommandLog>, UdsDiagnosticsResult> {
        val logs = ArrayList<CommandLog>()
        val results = ArrayList<UdsDtcReadResult>()
        var vin: String? = null
        val startedAt = System.currentTimeMillis()

        // 헤더 표시와 긴 메시지 허용은 다중 프레임 해석에 필요하다.
        logs.add(client.send(Elm327Command.HEADERS_ON))
        logs.add(client.send(Elm327Command("ATAL", "긴 메시지 허용", optional = true)))

        try {
            addresses.forEachIndexed { index, address ->
                onProgress(index + 1, addresses.size, address)

                val setHeader = client.send(Elm327Command("ATSH$address", "요청 주소 $address"))
                logs.add(setHeader)
                if (!setHeader.success) return@forEachIndexed

                // 다중 프레임을 끝까지 받으려면 흐름 제어를 직접 보내야 한다.
                EcuScanner.flowControlCommands(address).forEach { logs.add(client.send(it)) }

                val dtcLog = client.send(READ_DTC, READ_TIMEOUT_MS)
                logs.add(dtcLog)
                var parsed = UdsDtcParser.parse(dtcLog.rawResponse, address, READ_DTC)

                // 프레임이 유실되었으면 간격을 넓혀 한 번 더 시도한다.
                // 잘못 정렬된 데이터로 엉뚱한 코드를 보여 주는 것보다 다시 읽는 편이 낫다.
                if (parsed.needsRetry) {
                    EcuScanner.flowControlCommands(address, EcuScanner.SLOW_ST_MIN_MS)
                        .forEach { logs.add(client.send(it)) }

                    val retryLog = client.send(READ_DTC, READ_TIMEOUT_MS)
                    logs.add(retryLog)
                    val retried = UdsDtcParser.parse(retryLog.rawResponse, address, READ_DTC)
                    // 재시도가 더 온전하면 그것을 쓴다.
                    if (!retried.needsRetry || retried.codes.size > parsed.codes.size) {
                        parsed = retried.copy(
                            message = retried.message?.let { "$it (재시도함)" } ?: "재시도로 완성됨"
                        )
                    }
                }

                // 응답 자체가 없던 주소는 결과에 넣지 않는다.
                results.add(parsed)

                if (readVin && vin == null) {
                    val vinLog = client.send(READ_VIN, READ_TIMEOUT_MS)
                    logs.add(vinLog)
                    vin = UdsVinParser.parse(vinLog.rawResponse, READ_VIN)
                }
            }
        } finally {
            logs.add(client.send(Elm327Command("ATSH7DF", "요청 주소 방송으로 복귀", optional = true)))
            logs.add(client.send(Elm327Command("ATFCSM0", "흐름 제어 자동으로 복귀", optional = true)))
        }

        return logs to UdsDiagnosticsResult(
            results = results,
            vin = vin,
            durationMs = System.currentTimeMillis() - startedAt
        )
    }

    private companion object {
        /** ISO 14229 ReadDTCInformation, 상태 마스크 FF = 모든 코드. */
        const val READ_DTC = "1902FF"

        /** ISO 14229 ReadDataByIdentifier, DID F190 = 차대번호. */
        const val READ_VIN = "22F190"

        /** 다중 프레임 응답은 시간이 더 걸린다. */
        const val READ_TIMEOUT_MS = 6_000L
    }
}

/** `22 F1 90` 응답에서 차대번호를 뽑는다. */
object UdsVinParser {

    private val VALID = ('A'..'Z').toSet() + ('0'..'9').toSet() - setOf('I', 'O', 'Q')

    fun parse(rawResponse: String, command: String = "22F190"): String? {
        val lines = com.eunho.leafobd.util.ResponseText.dataLines(rawResponse, command)
        val responses = ObdFrameParser.parse(lines, headersOn = true)

        for (response in responses) {
            val bytes = response.bytes
            val sidIndex = bytes.indexOf(0x62)
            if (sidIndex < 0) continue
            // 62 F1 90 <ASCII...>
            if (bytes.getOrNull(sidIndex + 1) != 0xF1) continue
            if (bytes.getOrNull(sidIndex + 2) != 0x90) continue

            val text = bytes.drop(sidIndex + 3)
                .map { it.toChar() }
                .filter { it in VALID }
                .joinToString("")

            if (text.isNotBlank()) return text.take(17)
        }
        return null
    }
}
