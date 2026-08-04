package com.eunho.leafobd.obd

/** DTC 종류. 표준 OBD-II 모드에 대응한다. */
enum class DtcStatus(val label: String) {
    /** Mode 03 — 확정되어 저장된 오류코드 */
    STORED("저장"),

    /** Mode 07 — 아직 확정되지 않은 보류 중 오류코드 */
    PENDING("보류"),

    /** Mode 0A — 삭제 명령으로 지워지지 않는 영구 오류코드 */
    PERMANENT("영구")
}

/** 코드를 읽어 온 명령. 로그에 출처를 남기기 위해 별도로 보관한다. */
enum class DtcSource(val command: String, val label: String) {
    MODE_03("03", "Mode 03 (저장)"),
    MODE_07("07", "Mode 07 (보류)"),
    MODE_0A("0A", "Mode 0A (영구)")
}

/**
 * 표준 OBD-II 오류코드 한 건.
 *
 * @param code 표준 형식 코드 (예: `P0133`)
 * @param rawBytes 이 코드를 만든 원본 2바이트 (예: `01 33`)
 * @param description 설명. 1차 버전에서는 계통(첫 글자) 수준 설명만 채우고,
 *   검증되지 않은 Nissan 전용 코드 해석을 임의로 넣지 않는다.
 */
data class DtcCode(
    val code: String,
    val status: DtcStatus,
    val source: DtcSource,
    val rawBytes: String,
    val description: String? = null,
    /** 이 코드를 보고한 ECU의 CAN 주소. 헤더 표시(ATH1)가 꺼져 있으면 null. */
    val ecu: String? = null
) {
    /** 코드 첫 글자로 알 수 있는 계통 이름. */
    val systemLabel: String
        get() = when (code.firstOrNull()) {
            'P' -> "파워트레인"
            'C' -> "섀시"
            'B' -> "바디"
            'U' -> "네트워크/통신"
            else -> "알 수 없음"
        }

    /**
     * 안전상 특별히 주의해야 하는 계통인지 판단한다.
     *
     * 주의: 이 값이 false라고 해서 "안전한 코드"라는 뜻이 아니다.
     * 앱은 어떤 코드도 무시해도 된다고 안내하지 않는다.
     * 이 플래그는 UI에서 강조 표시를 하기 위한 보조 수단일 뿐이다.
     */
    val needsAttention: Boolean
        get() = code.firstOrNull() == 'B' || code.firstOrNull() == 'C' || code.firstOrNull() == 'U'
}
