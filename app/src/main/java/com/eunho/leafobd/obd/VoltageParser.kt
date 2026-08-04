package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/**
 * `ATRV` 응답.
 *
 * @param raw 원시 응답 문자열
 * @param volts 해석에 성공한 전압. 실패하면 null.
 */
data class VoltageReading(
    val raw: String,
    val volts: Double?
) {
    /** 화면에 표시할 문자열. */
    val display: String
        get() = volts?.let { "%.1f V".format(it) } ?: raw.trim().ifEmpty { "확인 불가" }

    /**
     * 12V 보조 배터리 상태에 대한 참고 안내.
     *
     * 주의: 이 값은 어댑터가 OBD 단자에서 읽은 참고값이다.
     * 정밀 멀티미터 측정을 대체하지 않으며, 이 값만으로 고장 여부를 판단하지 않는다.
     */
    val hint: String?
        get() = when {
            volts == null -> null
            volts < 11.0 -> "12V 보조 배터리 전압이 매우 낮습니다. 정밀 측정과 점검이 필요합니다."
            volts < 12.0 -> "12V 보조 배터리 전압이 낮은 편입니다. 참고값이므로 멀티미터로 확인하십시오."
            volts > 15.5 -> "전압이 비정상적으로 높게 측정되었습니다. 어댑터 값을 그대로 신뢰하지 마십시오."
            else -> null
        }
}

/** `ATRV` 응답에서 전압을 뽑아내는 파서. */
object VoltageParser {

    // "12.4V", "12.4 V", "ELM327 v2.3" 같은 문자열이 섞여 들어올 수 있으므로
    // 숫자 + 선택적 소수점 뒤에 V가 오는 형태만 인정한다.
    private val PATTERN = Regex("""(\d{1,2}(?:\.\d+)?)\s*V""", RegexOption.IGNORE_CASE)

    fun parse(rawResponse: String): VoltageReading {
        val lines = ResponseText.dataLines(rawResponse, "ATRV")
        val joined = lines.joinToString(" ")
        val volts = PATTERN.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()
        return VoltageReading(raw = joined, volts = volts)
    }
}
