package com.eunho.leafobd.obd

/**
 * 이 앱이 사용하는 표준 OBD-II 명령.
 *
 * SAE J1979에 정의된 공개 표준 모드만 사용한다.
 * 제조사 전용(Mode 22 등) 명령은 검증 자료 없이 추가하지 않는다.
 */
enum class ObdMode(
    val command: String,
    /** 정상 응답의 첫 바이트. 요청 모드 + 0x40 이다. */
    val responsePrefix: Int,
    val status: DtcStatus,
    val source: DtcSource,
    val label: String
) {
    /** 확정 저장된 배출가스 관련 DTC */
    STORED_DTC("03", 0x43, DtcStatus.STORED, DtcSource.MODE_03, "저장된 오류코드"),

    /** 보류 중(1주기만 감지된) DTC */
    PENDING_DTC("07", 0x47, DtcStatus.PENDING, DtcSource.MODE_07, "보류 중 오류코드"),

    /** 영구 DTC. Mode 04로 지워지지 않으며 ECU가 자체 판단으로만 지운다. */
    PERMANENT_DTC("0A", 0x4A, DtcStatus.PERMANENT, DtcSource.MODE_0A, "영구 오류코드");
}

/**
 * 표준 DTC 삭제 명령.
 *
 * 중요: Mode 04는 배출가스 관련 진단정보를 지우는 표준 명령이다.
 * Nissan Leaf의 EV 전용 ECU(VCM, LBC, OBC/PDM)와 ABS·에어백 오류가
 * 이 명령으로 지워진다고 가정하지 않는다. UI에도 같은 취지를 표시한다.
 */
object ObdClear {
    const val COMMAND: String = "04"
    const val RESPONSE_PREFIX: Int = 0x44
    const val LABEL: String = "표준 오류코드 삭제 (Mode 04)"
}
