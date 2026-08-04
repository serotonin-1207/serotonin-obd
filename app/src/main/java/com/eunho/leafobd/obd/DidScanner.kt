package com.eunho.leafobd.obd

import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.Elm327Command
import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.util.ResponseText

/** 읽기에 성공한 데이터 항목 하나. */
data class DidValue(
    val ecu: String,
    val did: Int,
    val bytes: List<Int>,
    val complete: Boolean = true
) {
    val didHex: String get() = "%04X".format(did)

    val rawHex: String get() = bytes.joinToString(" ") { "%02X".format(it) }

    /** ASCII 로 읽히면 그 문자열. 아니면 null. */
    val asText: String?
        get() {
            if (bytes.size < 3) return null
            val printable = bytes.count { it in 0x20..0x7E }
            if (printable < bytes.size * 3 / 4) return null
            return bytes.filter { it in 0x20..0x7E }.map { it.toChar() }.joinToString("").trim()
                .ifBlank { null }
        }

    /** 표준으로 이름이 정해진 항목이면 그 이름. */
    val label: String get() = StandardDid.nameOf(did) ?: "DID $didHex"
}

/** DID 스캔 결과. */
data class DidScanResult(
    val values: List<DidValue> = emptyList(),
    val scannedCount: Int = 0,
    val durationMs: Long = 0,
    val errorMessage: String? = null
) {
    val found: Boolean get() = values.isNotEmpty()

    val byEcu: Map<String, List<DidValue>> get() = values.groupBy { it.ecu }

    val summary: String
        get() = when {
            errorMessage != null -> errorMessage
            values.isEmpty() -> "읽을 수 있는 데이터 항목을 찾지 못했습니다."
            else -> "데이터 항목 ${values.size}개를 읽었습니다. (${byEcu.size}개 ECU)"
        }
}

/** ISO 14229 가 이름을 정해 둔 표준 데이터 식별자. */
object StandardDid {

    private val NAMES = mapOf(
        0xF186 to "현재 진단 세션",
        0xF187 to "제조사 부품번호",
        0xF188 to "제조사 소프트웨어 번호",
        0xF189 to "제조사 소프트웨어 버전",
        0xF18A to "시스템 공급사 식별자",
        0xF18B to "ECU 제조일",
        0xF18C to "ECU 일련번호",
        0xF190 to "차대번호(VIN)",
        0xF191 to "제조사 하드웨어 번호",
        0xF192 to "공급사 하드웨어 번호",
        0xF193 to "공급사 하드웨어 버전",
        0xF194 to "공급사 소프트웨어 번호",
        0xF195 to "공급사 소프트웨어 버전",
        0xF197 to "시스템 이름",
        0xF198 to "수리소 코드",
        0xF199 to "프로그래밍 날짜",
        0xF19D to "설치 날짜"
    )

    fun nameOf(did: Int): String? = NAMES[did]

    /** 표준 식별 정보 구간. 어느 차량에서도 의미가 같다. */
    val IDENTIFICATION: List<Int> = (0xF180..0xF1FF).toList()

    /**
     * 제조사가 흔히 쓰는 구간.
     *
     * 특정 값을 추측해 넣은 것이 아니라 **구간을 훑는 것**이며,
     * 보내는 요청은 표준 읽기(`22`)뿐이다.
     */
    val MANUFACTURER: List<Int> = (0x0000..0x01FF).toList()
}

/**
 * `22`(ReadDataByIdentifier)로 읽을 수 있는 데이터 항목을 찾는다.
 *
 * ## 왜 필요한가
 *
 * 오류코드는 "무엇이 잘못됐다"까지만 알려 준다.
 * 배터리 셀 전압이나 모듈 상태 같은 실제 값을 읽을 수 있으면
 * 수리 업체가 어느 부분을 손봐야 하는지 훨씬 좁힐 수 있다.
 *
 * ## 안전
 *
 * `22` 는 ISO 14229 **읽기 전용** 서비스다. 어떤 값도 바꾸지 않는다.
 * 지원하지 않는 DID 는 ECU 가 부정 응답하거나 무시할 뿐이다.
 */
class DidScanner(private val client: Elm327Client) {

    suspend fun scan(
        addresses: List<String>,
        dids: List<Int> = StandardDid.IDENTIFICATION,
        onProgress: suspend (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Pair<List<CommandLog>, DidScanResult> {
        val logs = ArrayList<CommandLog>()
        val values = ArrayList<DidValue>()
        val startedAt = System.currentTimeMillis()
        val total = addresses.size * dids.size
        var done = 0

        logs.add(client.send(Elm327Command.HEADERS_ON))
        logs.add(client.send(Elm327Command("ATAL", "긴 메시지 허용", optional = true)))
        logs.add(client.send(Elm327Command(FAST_TIMEOUT, "응답 대기 단축", optional = true)))

        try {
            for (address in addresses) {
                val setHeader = client.send(Elm327Command("ATSH$address", "요청 주소 $address"))
                logs.add(setHeader)
                if (!setHeader.success) continue

                EcuScanner.flowControlCommands(address).forEach { logs.add(client.send(it)) }

                for (did in dids) {
                    done++
                    onProgress(done, total, "$address / %04X".format(did))

                    val command = "22%04X".format(did)
                    val log = client.send(command, DID_TIMEOUT_MS)
                    // 응답이 없는 DID 가 대부분이라 로그가 폭증한다. 성공한 것만 남긴다.
                    val value = parse(log.rawResponse, address, did, command)
                    if (value != null) {
                        logs.add(log)
                        values.add(value)
                    }
                }
            }
        } finally {
            logs.add(client.send(Elm327Command("ATSH7DF", "요청 주소 복귀", optional = true)))
            logs.add(client.send(Elm327Command(RESET_TIMEOUT, "응답 대기 복귀", optional = true)))
        }

        return logs to DidScanResult(
            values = values,
            scannedCount = total,
            durationMs = System.currentTimeMillis() - startedAt
        )
    }

    private fun parse(rawResponse: String, address: String, did: Int, command: String): DidValue? {
        if (ResponseText.isNoData(rawResponse)) return null
        if (rawResponse.isBlank()) return null

        val lines = ResponseText.dataLines(rawResponse, command)
        val responses = ObdFrameParser.parse(lines, headersOn = true)

        for (response in responses) {
            val bytes = response.usableBytes
            val sidIndex = bytes.indexOf(POSITIVE_SID)
            if (sidIndex < 0) continue
            // 62 <DID 상위> <DID 하위> <데이터…>
            if (bytes.getOrNull(sidIndex + 1) != ((did shr 8) and 0xFF)) continue
            if (bytes.getOrNull(sidIndex + 2) != (did and 0xFF)) continue

            val data = bytes.drop(sidIndex + 3)
            if (data.isEmpty()) continue

            return DidValue(
                ecu = response.ecuId ?: address,
                did = did,
                bytes = data,
                complete = response.complete
            )
        }
        return null
    }

    private companion object {
        /** 정상 응답 머리값 (0x22 + 0x40). */
        const val POSITIVE_SID = 0x62

        /** 대부분의 DID 는 응답이 없으므로 대기를 짧게 잡는다. */
        const val FAST_TIMEOUT = "ATST14"
        const val RESET_TIMEOUT = "ATST64"
        const val DID_TIMEOUT_MS = 900L
    }
}
