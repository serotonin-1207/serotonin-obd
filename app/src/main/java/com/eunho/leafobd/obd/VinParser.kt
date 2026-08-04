package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/**
 * `0902` 응답에서 차대번호(VIN)를 읽는다.
 *
 * 응답 형식: `49 02 01 <17바이트 ASCII>`
 *  - `49` = Mode 09 응답
 *  - `02` = VIN 항목
 *  - `01` = 메시지 개수
 *
 * VIN 은 17자리이며 대문자 영숫자만 쓴다(혼동을 피하려고 I, O, Q 는 쓰지 않는다).
 *
 * 개인정보 주의: VIN 은 차량을 특정할 수 있는 식별정보다.
 * 화면에는 전체를 보여주더라도 파일에는 마스킹해서 저장한다.
 * ([com.eunho.leafobd.util.VinMasking] 참조)
 */
object VinParser {

    private const val VIN_LENGTH = 17

    private val VALID_CHARS = ('A'..'Z').toSet() + ('0'..'9').toSet() - setOf('I', 'O', 'Q')

    fun parse(rawResponse: String, headersOn: Boolean = false): String? {
        val lines = ResponseText.dataLines(rawResponse, "0902")
        val responses = ObdFrameParser.parse(lines, headersOn)

        for (response in responses) {
            val bytes = response.bytes
            val prefixIndex = bytes.indexOf(0x49)
            if (prefixIndex < 0) continue
            if (bytes.getOrNull(prefixIndex + 1) != 0x02) continue

            // 49 02 <메시지 개수> <ASCII...>
            val ascii = bytes.drop(prefixIndex + 3)
                .map { it.toChar() }
                .filter { it in VALID_CHARS }
                .joinToString("")

            if (ascii.length >= VIN_LENGTH) return ascii.take(VIN_LENGTH)
            // 일부 차량은 앞을 0x00 으로 채워 보내 길이가 모자랄 수 있다. 있는 만큼만 돌려준다.
            if (ascii.isNotEmpty()) return ascii
        }
        return null
    }
}
