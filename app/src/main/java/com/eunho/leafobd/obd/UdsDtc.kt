package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/**
 * UDS(ISO 14229) 형식의 오류코드.
 *
 * 표준 OBD 의 2바이트 DTC 와 달리 **3바이트 + 상태 1바이트**로 구성된다.
 *
 * ```
 * 33 ED 00   0C
 * └──┬───┘   └┬┘
 *   DTC     상태 비트
 * ```
 *
 * 앞 2바이트는 SAE J2012 문자 코드(P/C/B/U + 4자리)로 변환하고,
 * 세 번째 바이트는 고장 유형(FTB, Failure Type Byte)으로 그대로 표시한다.
 * 예: `33 ED 00` → `P33ED-00`
 *
 * @param ecu 이 코드를 보고한 ECU 주소
 * @param statusByte ISO 14229 DTCStatusOfDTC 비트
 */
data class UdsDtcCode(
    val code: String,
    val failureType: Int,
    val statusByte: Int,
    val rawBytes: String,
    val ecu: String? = null
) {
    /** `P33ED-00` 형태의 전체 표기. */
    val fullCode: String get() = "%s-%02X".format(code, failureType)

    val systemLabel: String
        get() = when (code.firstOrNull()) {
            'P' -> "파워트레인"
            'C' -> "섀시"
            'B' -> "바디"
            'U' -> "네트워크/통신"
            else -> "알 수 없음"
        }

    /** 상태 비트를 한국어 설명으로 푼다. */
    val statusLabels: List<String>
        get() = buildList {
            if (statusByte and 0x01 != 0) add("현재 고장 상태")
            if (statusByte and 0x02 != 0) add("이번 주행 주기에서 고장")
            if (statusByte and 0x04 != 0) add("보류 중")
            if (statusByte and 0x08 != 0) add("확정됨")
            if (statusByte and 0x10 != 0) add("삭제 후 검사 미완료")
            if (statusByte and 0x20 != 0) add("삭제 후 고장 발생")
            if (statusByte and 0x40 != 0) add("이번 주기 검사 미완료")
            if (statusByte and 0x80 != 0) add("경고등 점등 요청")
        }

    /** 확정된 고장인지. 보류와 구분해 표시한다. */
    val confirmed: Boolean get() = statusByte and 0x08 != 0

    /** 지금도 고장 상태인지. */
    val currentlyFailing: Boolean get() = statusByte and 0x01 != 0
}

/** 한 ECU 의 오류코드 조회 결과. */
data class UdsDtcReadResult(
    val ecu: String,
    val codes: List<UdsDtcCode> = emptyList(),
    val statusAvailabilityMask: Int? = null,
    val raw: String = "",
    val truncated: Boolean = false,
    /** 응답이 선언한 전체 길이. */
    val declaredLength: Int? = null,
    /** 실제로 받은 길이. */
    val receivedLength: Int? = null,
    /** 중간에 프레임이 빠졌는지. 이 경우 뒤쪽 코드는 해석하지 않았다. */
    val frameGap: Boolean = false,
    val message: String? = null
) {
    val hasCodes: Boolean get() = codes.isNotEmpty()

    /** 다시 읽어야 하는 상태인지. */
    val needsRetry: Boolean get() = truncated || frameGap
}

/**
 * `19 02`(ReadDTCInformation) 응답 파서.
 *
 * 응답 형식:
 * ```
 * 59 02 <상태 마스크> [<DTC 3바이트> <상태 1바이트>] ...
 * ```
 */
object UdsDtcParser {

    private const val POSITIVE_SID = 0x59
    private const val SUBFUNCTION = 0x02

    /** DTC 한 건이 차지하는 바이트 수 (3바이트 코드 + 1바이트 상태). */
    private const val RECORD_SIZE = 4

    fun parse(rawResponse: String, requestAddress: String, command: String = "1902FF"): UdsDtcReadResult {
        if (ResponseText.isNoData(rawResponse)) {
            return UdsDtcReadResult(ecu = requestAddress, message = "응답 없음")
        }

        val lines = ResponseText.dataLines(rawResponse, command)
        val responses = ObdFrameParser.parse(lines, headersOn = true)
        if (responses.isEmpty()) {
            return UdsDtcReadResult(ecu = requestAddress, raw = rawResponse.trim(), message = "해석할 데이터 없음")
        }

        for (response in responses) {
            // 프레임이 유실됐으면 그 지점까지만 해석한다.
            // 유실 지점 뒤는 바이트 정렬이 어긋나 엉뚱한 코드가 만들어진다.
            val bytes = response.usableBytes
            val sidIndex = bytes.indexOf(POSITIVE_SID)
            if (sidIndex < 0) continue
            if (bytes.getOrNull(sidIndex + 1) != SUBFUNCTION) continue

            val mask = bytes.getOrNull(sidIndex + 2)
            val payload = bytes.drop(sidIndex + 3)

            val codes = ArrayList<UdsDtcCode>()
            var i = 0
            while (i + RECORD_SIZE <= payload.size) {
                val b1 = payload[i]
                val b2 = payload[i + 1]
                val ftb = payload[i + 2]
                val status = payload[i + 3]
                i += RECORD_SIZE

                // 000000 은 빈 자리다.
                if (b1 == 0 && b2 == 0 && ftb == 0) continue

                codes.add(
                    UdsDtcCode(
                        code = decode(b1, b2),
                        failureType = ftb,
                        statusByte = status,
                        rawBytes = "%02X %02X %02X %02X".format(b1, b2, ftb, status),
                        ecu = response.ecuId ?: requestAddress
                    )
                )
            }

            val leftover = payload.size - i
            val incomplete = !response.complete || leftover > 0

            return UdsDtcReadResult(
                ecu = response.ecuId ?: requestAddress,
                codes = codes,
                statusAvailabilityMask = mask,
                raw = rawResponse.trim(),
                truncated = incomplete,
                declaredLength = response.declaredLength,
                receivedLength = response.bytes.size,
                frameGap = response.gapAfter != null,
                message = when {
                    response.gapAfter != null ->
                        "응답 도중 프레임이 유실되었습니다. 여기 표시된 ${codes.size}건까지만 신뢰할 수 있습니다. " +
                            "다시 읽어 주십시오."

                    response.declaredLength != null && response.bytes.size < response.declaredLength ->
                        "응답이 끝까지 오지 않았습니다. " +
                            "(${response.bytes.size}/${response.declaredLength}바이트) 다시 읽어 주십시오."

                    leftover > 0 ->
                        "응답 끝에 해석하지 못한 ${leftover}바이트가 남았습니다."

                    codes.isEmpty() -> "저장된 오류코드가 없습니다."
                    else -> null
                }
            )
        }

        // 부정 응답 등
        return UdsDtcReadResult(
            ecu = requestAddress,
            raw = rawResponse.trim(),
            message = "오류코드 응답이 아닙니다."
        )
    }

    /**
     * UDS 3바이트 DTC 의 앞 2바이트를 문자 코드로 바꾼다. (SAE J2012)
     *
     * 표준 OBD 2바이트 DTC 와 변환 규칙이 같다.
     * 예: `0x33 0xED` → `P33ED`, `0x0A 0xA6` → `P0AA6`
     */
    fun decode(high: Int, low: Int): String = DtcParser.decode(high, low)
}
