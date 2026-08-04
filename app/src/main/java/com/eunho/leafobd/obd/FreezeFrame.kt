package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/**
 * 프리즈 프레임 — 고장이 확정되던 **순간**의 데이터 스냅샷.
 *
 * 왜 중요한가:
 * Mode 04(표준 삭제)를 실행하면 프리즈 프레임도 **함께 지워진다.**
 * 즉, 삭제 전에 저장해 두지 않으면 "고장이 언제 어떤 상태에서 났는지"를
 * 영영 알 수 없게 된다. 이 앱은 삭제 전에 반드시 이 값을 읽어 파일에 남긴다.
 *
 * @param triggerDtc 프리즈 프레임을 저장하게 만든 DTC (PID 0x02)
 * @param values 해석에 성공한 값들
 * @param rawResponses PID별 원시 응답. 해석하지 못한 PID도 그대로 보존한다.
 */
data class FreezeFrame(
    val triggerDtc: String? = null,
    val values: List<PidValue> = emptyList(),
    val rawResponses: Map<String, String> = emptyMap(),
    val supportedPids: Set<Int> = emptySet()
) {
    /** 원시 응답을 포함해 아무것도 기록되지 않았으면 true. */
    val isEmpty: Boolean
        get() = triggerDtc == null && values.isEmpty() && rawResponses.isEmpty()

    /**
     * 실제로 쓸 수 있는 값을 얻었는지.
     *
     * 원시 응답만 있고 해석된 값이 하나도 없는 경우(예: 통신 타임아웃)는
     * "읽었다"고 볼 수 없으므로 false 다.
     */
    val hasData: Boolean
        get() = triggerDtc != null || values.isNotEmpty()
}

object FreezeFrameParser {

    /** 프리즈 프레임을 저장시킨 DTC를 담고 있는 PID. */
    const val PID_TRIGGER_DTC = 0x02

    /**
     * `0202 00` 응답에서 원인 DTC를 읽는다.
     *
     * 응답 형식: `42 02 00 <상위> <하위>`
     */
    fun parseTriggerDtc(rawResponse: String, command: String, headersOn: Boolean = false): String? {
        val lines = ResponseText.dataLines(rawResponse, command)
        val responses = ObdFrameParser.parse(lines, headersOn)

        for (response in responses) {
            val bytes = response.bytes
            val prefixIndex = bytes.indexOf(0x42)
            if (prefixIndex < 0) continue
            if (bytes.getOrNull(prefixIndex + 1) != PID_TRIGGER_DTC) continue

            // 42 02 <프레임번호> <DTC 상위> <DTC 하위>
            val high = bytes.getOrNull(prefixIndex + 3) ?: continue
            val low = bytes.getOrNull(prefixIndex + 4) ?: continue
            if (high == 0 && low == 0) return null  // 저장된 프리즈 프레임 없음
            return DtcParser.decode(high, low)
        }
        return null
    }
}
