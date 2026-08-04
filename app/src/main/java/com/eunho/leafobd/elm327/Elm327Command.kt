package com.eunho.leafobd.elm327

/**
 * ELM327 AT 명령.
 *
 * ELM327 데이터시트에 공개된 표준 AT 명령만 사용한다.
 * 제조사 전용 확장 명령(ST/VT 계열 등)은 어댑터마다 동작이 달라 사용하지 않는다.
 *
 * 모든 명령은 캐리지리턴(`\r`)으로 끝내야 한다. 전송은 [Elm327Client]가 담당한다.
 */
data class Elm327Command(
    val command: String,
    val label: String,
    /** 이 명령의 응답을 기다릴 최대 시간(ms). */
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /**
     * 실패해도 초기화를 계속할지 여부.
     * 어댑터마다 지원하지 않는 AT 명령이 있으므로 일부는 선택 사항으로 둔다.
     */
    val optional: Boolean = false
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 5_000L

        /**
         * ATZ는 어댑터를 완전히 재시작하므로 응답이 느리다.
         * 넉넉하게 기다린다.
         */
        val RESET = Elm327Command("ATZ", "어댑터 리셋", timeoutMs = 10_000L)
        val ECHO_OFF = Elm327Command("ATE0", "명령 에코 끄기")
        val LINEFEED_OFF = Elm327Command("ATL0", "줄바꿈 최소화")
        val SPACES_OFF = Elm327Command("ATS0", "응답 공백 제거")
        val HEADERS_OFF = Elm327Command("ATH0", "CAN 헤더 숨김")

        /**
         * CAN 헤더 표시. 어느 ECU가 응답했는지 알 수 있지만
         * 다중 프레임 응답을 앱이 직접 재조립해야 한다([com.eunho.leafobd.obd.ObdFrameParser]).
         */
        val HEADERS_ON = Elm327Command("ATH1", "CAN 헤더 표시")
        val PROTOCOL_AUTO = Elm327Command("ATSP0", "프로토콜 자동 선택")
        val IDENTIFY = Elm327Command("ATI", "어댑터 식별정보")
        val READ_VOLTAGE = Elm327Command("ATRV", "차량 측 전압")

        /**
         * 현재 선택된 프로토콜 이름. 어댑터에 따라 지원하지 않을 수 있어 선택 사항이다.
         * 첫 OBD 명령 전에는 아직 프로토콜이 정해지지 않아 `AUTO`로만 나올 수 있다.
         */
        val DESCRIBE_PROTOCOL = Elm327Command("ATDP", "연결 프로토콜", optional = true)

        /** 프로토콜 번호(숫자). ATDP 를 보완한다. */
        val DESCRIBE_PROTOCOL_NUMBER = Elm327Command("ATDPN", "프로토콜 번호", optional = true)

        /**
         * 웜 스타트. ATZ 와 달리 통신 설정을 유지한 채 재시작하므로 재연결 시 빠르다.
         * 지원하지 않는 어댑터가 있어 선택 사항으로 둔다.
         */
        val WARM_START = Elm327Command("ATWS", "웜 스타트", timeoutMs = 5_000L, optional = true)

        /**
         * 첫 연결 시 실행하는 기본 초기화 순서.
         * 지시서 8장의 순서를 그대로 따른다.
         *
         * @param headersOn true 면 ATH0 대신 ATH1 을 보내 CAN 헤더를 표시한다.
         * @param protocolCode `ATSP` 뒤에 붙일 값. 기본 `0`(자동 선택).
         */
        fun initSequence(
            headersOn: Boolean = false,
            protocolCode: String = "0"
        ): List<Elm327Command> = listOf(
            RESET,
            ECHO_OFF,
            LINEFEED_OFF,
            SPACES_OFF,
            if (headersOn) HEADERS_ON else HEADERS_OFF,
            if (protocolCode == "0") {
                PROTOCOL_AUTO
            } else {
                Elm327Command("ATSP$protocolCode", "프로토콜 $protocolCode 지정")
            },
            IDENTIFY,
            READ_VOLTAGE
        )

        /** 기본(헤더 숨김) 초기화 순서. */
        val INIT_SEQUENCE: List<Elm327Command> = initSequence(headersOn = false)

        /** OBD 데이터 요청은 차량 응답을 기다려야 하므로 조금 더 넉넉하게 잡는다. */
        const val OBD_TIMEOUT_MS: Long = 12_000L
    }
}
