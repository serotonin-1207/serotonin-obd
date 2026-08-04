package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/**
 * ECU 탐색에 쓰는 요청.
 *
 * **모두 ISO 14229(UDS) 국제 표준에 정의된 읽기 전용 서비스다.**
 * 제조사 전용 명령이 아니며, 차량 상태를 바꾸지 않는다.
 *
 * 의도적으로 넣지 않은 것:
 *  - `0x10` 진단 세션 제어 — ECU 상태를 바꾼다
 *  - `0x27` 보안 접근 — 잠금 해제 시도
 *  - `0x2E` 데이터 쓰기 / `0x31` 루틴 실행 / `0x11` ECU 리셋 — 모두 쓰기·작동
 *
 * @param positiveSid 정상 응답의 첫 바이트 (요청 SID + 0x40)
 */
enum class EcuProbeRequest(
    val command: String,
    val label: String,
    val positiveSid: Int
) {
    /** 0x3E TesterPresent — 가장 가볍고 부작용이 없다. 존재 확인용. */
    TESTER_PRESENT("3E00", "존재 확인 (TesterPresent)", 0x7E),

    /** 0x22 ReadDataByIdentifier, DID F190 = 차대번호(VIN). */
    READ_VIN("22F190", "차대번호 읽기", 0x62),

    /** 0x19 ReadDTCInformation, subfunction 0x02 = 상태 마스크로 DTC 조회. */
    READ_DTC("1902FF", "오류코드 읽기", 0x59)
}

/** 응답의 성격. */
enum class EcuResponseKind(val label: String) {
    /** 요청을 정상 처리했다. */
    POSITIVE("정상 응답"),

    /**
     * 부정 응답(`7F`). 서비스를 지원하지 않거나 조건이 안 맞는다는 뜻인데,
     * **ECU 가 거기 존재한다는 확실한 증거다.** 무응답과 전혀 다르다.
     */
    NEGATIVE("거부 응답 (ECU 존재)"),

    /** 해석하지 못한 데이터. 그래도 무언가 돌아왔다. */
    UNKNOWN("알 수 없는 응답")
}

/** 응답한 주소 한 건. */
data class EcuScanHit(
    val requestAddress: String,
    val respondingId: String?,
    val request: EcuProbeRequest,
    val kind: EcuResponseKind,
    val raw: String,
    /** 부정 응답 코드(NRC). 있으면 이유를 알 수 있다. */
    val negativeCode: Int? = null
) {
    val negativeReason: String?
        get() = negativeCode?.let { UdsNegativeResponse.describe(it) }
}

/** 스캔 전체 결과. */
data class EcuScanResult(
    val hits: List<EcuScanHit> = emptyList(),
    val scannedAddresses: Int = 0,
    val durationMs: Long = 0,
    val deep: Boolean = false,
    val errorMessage: String? = null
) {
    val found: Boolean get() = hits.isNotEmpty()

    /** 응답한 고유 주소 목록. */
    val respondingAddresses: List<String>
        get() = hits.map { it.requestAddress }.distinct()

    val summary: String
        get() = when {
            errorMessage != null -> errorMessage
            hits.isEmpty() ->
                "주소 ${scannedAddresses}개를 확인했지만 응답한 ECU가 없습니다.\n" +
                    "게이트웨이가 표준 진단 요청도 막고 있을 가능성이 큽니다. " +
                    "이 어댑터와 표준 명령으로 할 수 있는 시도는 여기까지입니다."

            else ->
                "응답한 ECU 주소 ${respondingAddresses.size}개를 찾았습니다: " +
                    respondingAddresses.joinToString(", ") + "\n" +
                    "이 주소로는 통신이 됩니다. 여기서부터 진단을 확장할 수 있습니다."
        }
}

/** UDS 부정 응답 코드(NRC) 설명. ISO 14229 표준값이다. */
object UdsNegativeResponse {
    fun describe(code: Int): String = when (code) {
        0x10 -> "일반 거부"
        0x11 -> "지원하지 않는 서비스"
        0x12 -> "지원하지 않는 하위 기능"
        0x13 -> "요청 길이가 맞지 않음"
        0x22 -> "지금 조건에서는 응답할 수 없음"
        0x31 -> "요청 범위를 벗어남 (해당 데이터 없음)"
        0x33 -> "보안 접근이 필요함"
        0x78 -> "처리 중 — 응답이 지연됨"
        0x7E -> "현재 세션에서 지원하지 않는 서비스"
        0x7F -> "현재 세션에서 지원하지 않는 하위 기능"
        else -> "부정 응답 0x%02X".format(code)
    }
}

/** 스캔 대상 주소 목록. */
object EcuAddresses {

    /**
     * 표준 OBD-II 진단 주소.
     * ISO 15765-4 가 정한 물리 주소로, 응답은 보통 +8 (`7E0` → `7E8`) 로 온다.
     */
    val STANDARD: List<String> = (0..7).map { "7E%X".format(it) }

    /**
     * 11비트 진단 주소 전체 범위.
     *
     * 특정 제조사의 주소를 추측해 넣은 것이 아니라, 진단용으로 배정된
     * 주소 구간을 처음부터 끝까지 훑는 것이다. 보내는 요청은 표준 읽기뿐이다.
     */
    val FULL: List<String> = (0x700..0x7EF).map { "%03X".format(it) }
}

/** 원시 응답에서 UDS 응답을 해석한다. */
object EcuScanParser {

    /** 부정 응답 머리값. */
    private const val NEGATIVE_SID = 0x7F

    /**
     * @return 응답이 있으면 [EcuScanHit], 없으면 null
     */
    fun parse(
        rawResponse: String,
        address: String,
        request: EcuProbeRequest,
        headersOn: Boolean
    ): EcuScanHit? {
        if (ResponseText.isNoData(rawResponse)) return null
        if (rawResponse.isBlank()) return null

        val lines = ResponseText.dataLines(rawResponse, request.command)
        if (lines.isEmpty()) return null

        // 통신 오류는 응답이 아니다.
        ResponseText.errorKeyword(rawResponse, request.command)?.let { return null }

        val responses = ObdFrameParser.parse(lines, headersOn)
        if (responses.isEmpty()) return null

        for (response in responses) {
            val bytes = response.bytes
            if (bytes.isEmpty()) continue

            // 7F <요청SID> <NRC> — 부정 응답. ECU 가 존재한다는 증거다.
            val negIndex = bytes.indexOf(NEGATIVE_SID)
            if (negIndex >= 0 && negIndex + 2 < bytes.size) {
                return EcuScanHit(
                    requestAddress = address,
                    respondingId = response.ecuId,
                    request = request,
                    kind = EcuResponseKind.NEGATIVE,
                    raw = rawResponse.trim(),
                    negativeCode = bytes[negIndex + 2]
                )
            }

            if (bytes.contains(request.positiveSid)) {
                return EcuScanHit(
                    requestAddress = address,
                    respondingId = response.ecuId,
                    request = request,
                    kind = EcuResponseKind.POSITIVE,
                    raw = rawResponse.trim()
                )
            }
        }

        // 무언가 돌아왔지만 형식을 모르겠다 — 그래도 기록한다.
        return EcuScanHit(
            requestAddress = address,
            respondingId = responses.firstOrNull()?.ecuId,
            request = request,
            kind = EcuResponseKind.UNKNOWN,
            raw = rawResponse.trim()
        )
    }
}
