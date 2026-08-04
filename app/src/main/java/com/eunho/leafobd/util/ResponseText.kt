package com.eunho.leafobd.util

/**
 * ELM327 원시 응답 문자열을 다루는 순수 함수 모음.
 *
 * Android API에 의존하지 않으므로 JVM 단위 테스트에서 그대로 검증할 수 있다.
 *
 * ELM327 응답에는 다음이 섞여 들어올 수 있다.
 *  - 명령 echo (ATE0 적용 전 또는 어댑터가 무시한 경우)
 *  - `SEARCHING...` (프로토콜 자동 탐색 중)
 *  - 프롬프트 `>`
 *  - CR/LF 혼합
 *  - 공백 (ATS0 적용 전)
 *  - 상태 문자열 (`NO DATA`, `CAN ERROR` 등)
 */
object ResponseText {

    const val PROMPT = '>'

    /** 데이터가 없다는 정상 응답. 오류가 아니라 "해당 모드에 코드 없음/미지원"을 뜻한다. */
    const val NO_DATA = "NO DATA"

    /**
     * 통신 실패를 뜻하는 키워드.
     * 사용자에게는 한국어 메시지로 변환해 보여준다.
     */
    val ERROR_KEYWORDS: List<String> = listOf(
        "UNABLE TO CONNECT",
        "BUS INIT: ERROR",
        "BUS INIT",
        "BUS ERROR",
        "CAN ERROR",
        "BUFFER FULL",
        "DATA ERROR",
        "FB ERROR",
        "LV RESET",
        "STOPPED",
        "ERROR"
    )

    /** 어댑터가 알아듣지 못한 명령에 대한 응답. */
    const val UNKNOWN_COMMAND = "?"

    /** 진행 표시용 문자열. 데이터가 아니므로 버린다. */
    private val NOISE_LINES: List<String> = listOf("SEARCHING...", "SEARCHING", "BUS INIT: OK")

    /**
     * 원시 응답을 줄 단위로 나눈다.
     * CR, LF, CRLF를 모두 줄바꿈으로 취급하고 프롬프트와 공백을 제거한다.
     */
    fun lines(raw: String): List<String> =
        raw.replace(PROMPT.toString(), "\n")
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * 데이터 줄만 남긴다.
     *
     * @param command 보낸 명령. 같은 문자열로 시작하는 echo 줄을 제거하는 데 쓴다.
     */
    fun dataLines(raw: String, command: String? = null): List<String> {
        val echo = command?.replace(" ", "")?.uppercase()
        return lines(raw).filter { line ->
            val upper = line.uppercase()
            val compact = upper.replace(" ", "")
            when {
                upper in NOISE_LINES -> false
                echo != null && compact == echo -> false
                else -> true
            }
        }
    }

    /** 응답 어디에든 `NO DATA`가 있으면 true. */
    fun isNoData(raw: String): Boolean =
        raw.uppercase().contains(NO_DATA)

    /** 응답이 `OK`만 담고 있으면 true. AT 명령의 정상 응답이다. */
    fun isOk(raw: String, command: String? = null): Boolean =
        dataLines(raw, command).any { it.equals("OK", ignoreCase = true) }

    /**
     * 통신 오류 키워드를 찾는다. 없으면 null.
     *
     * `NO DATA`는 오류가 아니므로 여기서 걸러지지 않는다.
     */
    fun errorKeyword(raw: String, command: String? = null): String? {
        val dataLines = dataLines(raw, command)
        if (dataLines.any { it == UNKNOWN_COMMAND }) return UNKNOWN_COMMAND
        val upper = dataLines.joinToString("\n") { it.uppercase() }
        if (upper.contains(NO_DATA)) return null
        return ERROR_KEYWORDS.firstOrNull { upper.contains(it) }
    }

    /** ELM327 오류 키워드를 사용자용 한국어 설명으로 바꾼다. */
    fun errorMessageKorean(keyword: String): String = when (keyword) {
        UNKNOWN_COMMAND -> "어댑터가 명령을 인식하지 못했습니다. 어댑터가 이 명령을 지원하지 않을 수 있습니다."
        "UNABLE TO CONNECT" -> "차량 통신 버스에 연결하지 못했습니다. 시동(READY) 상태와 OBD 단자 결합을 확인하십시오."
        "BUS INIT: ERROR", "BUS INIT" -> "통신 버스 초기화에 실패했습니다. 어댑터를 다시 꽂고 시도하십시오."
        "BUS ERROR" -> "통신 버스 오류가 발생했습니다. 배선 또는 어댑터 상태를 확인하십시오."
        "CAN ERROR" -> "CAN 통신 오류가 발생했습니다. 차량 전원 상태와 어댑터 연결을 확인하십시오."
        "BUFFER FULL" -> "어댑터 버퍼가 가득 찼습니다. 연결을 끊고 다시 시도하십시오."
        "DATA ERROR" -> "수신한 데이터가 손상되었습니다. 다시 시도하십시오."
        "FB ERROR" -> "어댑터 피드백 오류입니다. 어댑터를 다시 꽂으십시오."
        "LV RESET" -> "어댑터 전압이 낮아 재시작되었습니다. 차량 12V 배터리 상태를 확인하십시오."
        "STOPPED" -> "명령이 중단되었습니다. 다시 시도하십시오."
        "ERROR" -> "어댑터가 오류를 보고했습니다."
        else -> "알 수 없는 어댑터 오류입니다: $keyword"
    }

    /**
     * 데이터 줄을 16진 바이트 배열로 바꾼다.
     *
     * 다중 프레임(ISO-TP) 응답은 ELM327이 아래처럼 줄머리를 붙여 준다.
     * ```
     * 014
     * 0: 43 04 01 33 02 45
     * 1: 03 21 00 00 00 00
     * ```
     * 이때 길이 표시 줄(`014`)과 프레임 번호(`0:`)를 제거하고 이어 붙인다.
     *
     * @return 0~255 정수 목록. 16진수가 아닌 문자가 있으면 그 줄은 건너뛴다.
     */
    fun hexBytes(dataLines: List<String>): List<Int> {
        val payload = StringBuilder()
        var multiFrame = false

        // "0:", "1:" 같은 프레임 번호가 하나라도 있으면 다중 프레임 응답이다.
        val frameLines = dataLines.filter { it.length > 1 && it[1] == ':' }
        if (frameLines.isNotEmpty()) multiFrame = true

        for (line in dataLines) {
            var cleaned = line.replace(" ", "").uppercase()

            // 프레임 번호 제거 (예: "0:430401330245" -> "430401330245")
            if (cleaned.length > 1 && cleaned[1] == ':') {
                cleaned = cleaned.substring(2)
            } else if (multiFrame && cleaned.length <= 3) {
                // 다중 프레임의 총 길이 표시 줄(예: "014")은 데이터가 아니다.
                continue
            }

            if (cleaned.isEmpty()) continue
            if (!cleaned.all { it in '0'..'9' || it in 'A'..'F' }) continue
            payload.append(cleaned)
        }

        val hex = payload.toString()
        val bytes = ArrayList<Int>(hex.length / 2)
        var i = 0
        // 홀수 길이면 마지막 반쪽 바이트는 잘린 응답이므로 버린다.
        while (i + 1 < hex.length) {
            bytes.add(hex.substring(i, i + 2).toInt(16))
            i += 2
        }
        return bytes
    }

    /** 사람이 보기 좋은 형태로 정규화한 16진 문자열(2바이트마다 공백). */
    fun formatHex(bytes: List<Int>): String =
        bytes.joinToString(" ") { it.toString(16).uppercase().padStart(2, '0') }
}
