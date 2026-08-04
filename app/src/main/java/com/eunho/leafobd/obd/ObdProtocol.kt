package com.eunho.leafobd.obd

/**
 * ELM327 이 지원하는 OBD-II 통신 프로토콜.
 *
 * 번호와 이름은 ELM327 데이터시트에 공개된 표준값이다.
 * `ATSP<번호>` 로 지정하고 `ATDPN` 으로 확인한다.
 * 제조사 전용 확장이 아니라 어떤 ELM327 계열 어댑터에서도 동일하다.
 *
 * 2019년식 Nissan Leaf 를 포함한 최근 차량은 대부분 [CAN_11B_500K] 를 쓴다.
 *
 * @param code `ATSP` 뒤에 붙는 한 자리 값
 * @param slow 초기화에 시간이 오래 걸리는 프로토콜(5보 초기화 등)
 */
enum class ObdProtocol(
    val code: String,
    val label: String,
    val description: String,
    val slow: Boolean = false
) {
    AUTO("0", "자동 선택", "어댑터가 스스로 프로토콜을 찾습니다. 대부분의 차량에서 이것으로 충분합니다."),

    J1850_PWM("1", "SAE J1850 PWM", "포드 계열 구형 차량 (41.6 kbaud)"),
    J1850_VPW("2", "SAE J1850 VPW", "GM 계열 구형 차량 (10.4 kbaud)"),
    ISO9141_2("3", "ISO 9141-2", "구형 아시아·유럽 차량 (5보 초기화)", slow = true),
    KWP_SLOW("4", "ISO 14230-4 KWP (5보)", "구형 KWP2000, 5보 초기화", slow = true),
    KWP_FAST("5", "ISO 14230-4 KWP (고속)", "KWP2000 고속 초기화"),

    CAN_11B_500K("6", "ISO 15765-4 CAN 11bit/500k", "2008년 이후 대부분의 차량. **Nissan Leaf 는 보통 이것입니다.**"),
    CAN_29B_500K("7", "ISO 15765-4 CAN 29bit/500k", "확장 ID를 쓰는 CAN"),
    CAN_11B_250K("8", "ISO 15765-4 CAN 11bit/250k", "저속 CAN"),
    CAN_29B_250K("9", "ISO 15765-4 CAN 29bit/250k", "저속 확장 ID CAN"),
    J1939("A", "SAE J1939 CAN", "상용차·트럭 계열");

    /** 이 프로토콜을 지정하는 AT 명령. */
    val setCommand: String get() = "ATSP$code"

    companion object {
        fun ofCode(code: String?): ObdProtocol? =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }

        /**
         * 자동 선택이 실패했을 때 순서대로 시도할 후보.
         *
         * 승용차에서 실제로 쓰이는 것부터 시도해 시간을 아낀다.
         * 5보 초기화 계열(3, 4)은 한 번에 5초 이상 걸리므로 기본 후보에서 제외했다.
         * 필요하면 설정에서 수동으로 직접 고를 수 있다.
         */
        val SWEEP_CANDIDATES: List<ObdProtocol> = listOf(
            CAN_11B_500K,
            CAN_29B_500K,
            CAN_11B_250K,
            CAN_29B_250K,
            KWP_FAST
        )

        /** 설정 화면에서 고를 수 있는 목록(자동 포함). */
        val SELECTABLE: List<ObdProtocol> = entries.toList()
    }
}
