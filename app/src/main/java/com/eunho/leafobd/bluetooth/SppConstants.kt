package com.eunho.leafobd.bluetooth

import java.util.UUID

object SppConstants {

    /**
     * 표준 Serial Port Profile UUID.
     *
     * ELM327 계열 Bluetooth Classic 어댑터는 모두 이 UUID로 RFCOMM 서비스를 제공한다.
     * Vgate iCar Pro BT3.0도 여기에 해당한다.
     */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** RFCOMM 연결 타임아웃(ms). */
    const val CONNECT_TIMEOUT_MS: Long = 15_000L

    /**
     * 어댑터 이름에 흔히 들어가는 문자열.
     * 목록에서 OBD 어댑터로 추정되는 장치를 위쪽에 보여 주기 위한 힌트일 뿐,
     * 이 목록에 없다고 연결을 막지는 않는다.
     */
    val LIKELY_OBD_NAME_HINTS: List<String> = listOf(
        "OBD", "ELM", "VGATE", "ICAR", "V-LINK", "VLINK", "OBDII", "VEEPEAK", "KONNWEI"
    )
}
