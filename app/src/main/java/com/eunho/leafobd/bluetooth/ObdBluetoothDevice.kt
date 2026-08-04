package com.eunho.leafobd.bluetooth

import com.eunho.leafobd.util.MacMasking

/**
 * 화면에 보여줄 페어링된 Bluetooth 장치.
 *
 * [address] 는 연결에만 사용하고 로그 파일에는 [maskedAddress] 만 기록한다.
 */
data class ObdBluetoothDevice(
    val name: String,
    val address: String
) {
    val maskedAddress: String
        get() = MacMasking.mask(address) ?: "알 수 없음"

    /** 이름으로 봤을 때 OBD 어댑터일 가능성이 높은지. 목록 정렬에만 쓴다. */
    val looksLikeObdAdapter: Boolean
        get() {
            val upper = name.uppercase()
            return SppConstants.LIKELY_OBD_NAME_HINTS.any { upper.contains(it) }
        }
}
