package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/** DTC 조회 응답의 처리 결과 종류. */
enum class ObdResponseStatus(val label: String) {
    /** 정상 응답. 코드가 0건일 수도 있다. */
    OK("정상"),

    /** `NO DATA` — 해당 모드에 코드가 없거나 차량/어댑터가 지원하지 않는다. */
    NO_DATA("데이터 없음"),

    /** 어댑터가 명령을 인식하지 못했다(`?`). */
    UNSUPPORTED("지원되지 않음"),

    /** 통신 오류 (CAN ERROR, UNABLE TO CONNECT 등) */
    ERROR("통신 오류"),

    /** 응답이 비었거나 해석할 수 있는 데이터가 없다. */
    EMPTY("빈 응답")
}

/**
 * DTC 조회 결과.
 *
 * @param normalizedHex 잡음을 제거하고 정규화한 16진 응답 (로그 저장용)
 * @param truncated 홀수 길이 등으로 응답 일부가 잘린 것으로 보이면 true
 */
data class DtcParseResult(
    val status: ObdResponseStatus,
    val codes: List<DtcCode>,
    val normalizedHex: String,
    val message: String? = null,
    val truncated: Boolean = false
)

/**
 * 표준 OBD-II DTC 파서.
 *
 * Android API를 쓰지 않는 순수 Kotlin이므로 JVM 단위 테스트로 검증한다.
 */
object DtcParser {

    /** 사용하지 않는 자리를 채우는 패딩 값. 코드가 아니다. */
    private const val PADDING = 0x0000

    /**
     * 한 번에 응답할 수 있는 DTC 개수의 현실적인 상한.
     * 개수 바이트 판별 휴리스틱에서 말도 안 되는 값을 거르는 데 쓴다.
     */
    private const val MAX_PLAUSIBLE_COUNT = 0x40

    /**
     * @param headersOn `ATH1` 로 CAN 헤더를 켠 상태의 응답이면 true.
     *   이때는 ECU별로 나누어 해석하고 각 코드에 보고 ECU를 기록한다.
     */
    fun parse(
        rawResponse: String,
        mode: ObdMode,
        headersOn: Boolean = false
    ): DtcParseResult {
        val dataLines = ResponseText.dataLines(rawResponse, mode.command)

        if (dataLines.isEmpty()) {
            return DtcParseResult(
                status = ObdResponseStatus.EMPTY,
                codes = emptyList(),
                normalizedHex = "",
                message = "어댑터가 응답하지 않았습니다."
            )
        }

        ResponseText.errorKeyword(rawResponse, mode.command)?.let { keyword ->
            val status =
                if (keyword == ResponseText.UNKNOWN_COMMAND) ObdResponseStatus.UNSUPPORTED
                else ObdResponseStatus.ERROR
            return DtcParseResult(
                status = status,
                codes = emptyList(),
                normalizedHex = "",
                message = ResponseText.errorMessageKorean(keyword)
            )
        }

        if (ResponseText.isNoData(rawResponse)) {
            return DtcParseResult(
                status = ObdResponseStatus.NO_DATA,
                codes = emptyList(),
                normalizedHex = "",
                message = "${mode.label}: 데이터 없음 (코드가 없거나 이 모드를 지원하지 않습니다)"
            )
        }

        val responses = ObdFrameParser.parse(dataLines, headersOn)
        if (responses.isEmpty()) {
            return DtcParseResult(
                status = ObdResponseStatus.EMPTY,
                codes = emptyList(),
                normalizedHex = "",
                message = "16진 데이터가 없어 해석할 수 없습니다."
            )
        }

        val codes = ArrayList<DtcCode>()
        val normalized = StringBuilder()
        var truncated = false
        var prefixFound = false

        for (response in responses) {
            if (normalized.isNotEmpty()) normalized.append("\n")
            response.ecuId?.let { normalized.append("[$it] ") }
            normalized.append(ResponseText.formatHex(response.bytes))

            val prefixIndex = response.bytes.indexOf(mode.responsePrefix)
            if (prefixIndex < 0) continue
            prefixFound = true

            var payload = response.bytes.subList(prefixIndex + 1, response.bytes.size)

            // ISO 15765(CAN)에서는 머리값 다음에 DTC 개수 바이트가 붙는다.
            // ISO 9141/KWP에서는 개수 바이트 없이 바로 DTC가 이어진다.
            // 두 형식을 구분하기 위해 "개수 * 2 == 남은 바이트 수"인지 확인한다.
            if (payload.isNotEmpty()) {
                val candidateCount = payload[0]
                if (candidateCount <= MAX_PLAUSIBLE_COUNT &&
                    payload.size - 1 == candidateCount * 2
                ) {
                    payload = payload.subList(1, payload.size)
                }
            }

            if (payload.size % 2 != 0) truncated = true

            var i = 0
            while (i + 1 < payload.size) {
                val high = payload[i]
                val low = payload[i + 1]
                i += 2
                val value = (high shl 8) or low
                if (value == PADDING) continue  // 0000 은 빈 자리 패딩이다.
                codes.add(
                    DtcCode(
                        code = decode(high, low),
                        status = mode.status,
                        source = mode.source,
                        rawBytes = "%02X %02X".format(high, low),
                        ecu = response.ecuId
                    )
                )
            }
        }

        if (!prefixFound) {
            return DtcParseResult(
                status = ObdResponseStatus.EMPTY,
                codes = emptyList(),
                normalizedHex = normalized.toString(),
                message = "${mode.label} 응답 머리값(0x%02X)을 찾지 못했습니다."
                    .format(mode.responsePrefix)
            )
        }

        val message = when {
            truncated -> "응답이 중간에 잘린 것으로 보입니다. 다시 읽어 주십시오."
            codes.isEmpty() -> "${mode.label}: 검출된 코드가 없습니다."
            else -> null
        }

        return DtcParseResult(
            status = ObdResponseStatus.OK,
            codes = codes,
            normalizedHex = normalized.toString(),
            message = message,
            truncated = truncated
        )
    }

    /**
     * 2바이트를 표준 DTC 문자열로 변환한다. (SAE J2012)
     *
     * 첫 바이트 상위 2비트 -> 계통 문자: 00=P, 01=C, 10=B, 11=U
     * 그다음 2비트 -> 첫 번째 숫자 (0~3)
     * 나머지 12비트 -> 3자리 16진수
     *
     * 예: 0x01 0x33 -> P0133
     */
    fun decode(high: Int, low: Int): String {
        val letter = when ((high shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val firstDigit = (high shr 4) and 0x03
        val rest = "%01X%02X".format(high and 0x0F, low and 0xFF)
        return "$letter$firstDigit$rest"
    }
}
