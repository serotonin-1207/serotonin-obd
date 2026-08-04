package com.eunho.leafobd.elm327

/**
 * 테스트 모드에서 쓸 모의 시나리오.
 *
 * 실제 어댑터나 차량 없이도 화면 흐름, 파서, 로그 저장, 삭제 조건 검증을
 * 끝까지 확인하기 위한 것이다.
 */
enum class FakeScenario(val label: String, val description: String) {
    NORMAL(
        "표준 응답",
        "저장 코드 1건(P0133). 삭제하면 모두 사라진다."
    ),
    RECURRING(
        "삭제 후 재발",
        "저장 코드 4건, 영구 코드 1건. 삭제해도 코드가 다시 나타난다(활성 고장 상황)."
    ),
    NO_DATA(
        "데이터 없음",
        "모든 모드가 NO DATA로 응답한다."
    ),
    CAN_ERROR(
        "통신 오류",
        "OBD 명령에 CAN ERROR로 응답한다."
    ),
    TIMEOUT(
        "무응답",
        "OBD 명령에 아무 응답도 하지 않아 타임아웃이 발생한다."
    ),
    PROTOCOL_SEARCH_FAIL(
        "자동 프로토콜 실패",
        "자동 선택(ATSP0)으로는 UNABLE TO CONNECT 가 나고, 프로토콜 6으로 고정해야 통신된다. " +
            "실제 차량에서 겪은 증상을 재현한다."
    )
}

/**
 * 실제 어댑터 없이 동작하는 모의 통로.
 *
 * 주의: 여기에 들어 있는 코드 값은 **완전히 가짜**다.
 * 실제 Nissan Leaf에서 이런 코드가 나온다는 뜻이 아니며, 진단 근거로 쓸 수 없다.
 * UI는 이 통로를 쓰는 동안 "모의 데이터" 배지를 반드시 표시한다.
 */
class FakeElm327Transport(
    private val scenario: FakeScenario = FakeScenario.RECURRING
) : Elm327Transport {

    override val description: String = "모의 어댑터 (${scenario.label})"
    override val simulated: Boolean = true

    private var open = false
    private val pending = ArrayDeque<Byte>()

    /** 응답이 준비되는 시각. 실제 어댑터의 지연을 흉내 낸다. */
    private var releaseAt: Long = 0L

    /** Mode 04를 이미 한 번 실행했는지. 삭제 전후 비교를 흉내 내는 데 쓴다. */
    private var cleared = false

    /** `ATSP<n>` 으로 마지막에 지정된 프로토콜. 기본은 자동(0). */
    private var protocolCode = "0"

    /** `ATSH<주소>` 로 마지막에 지정된 요청 주소. 기본은 방송(7DF). */
    private var requestAddress = "7DF"

    override val isOpen: Boolean get() = open

    override fun open() {
        open = true
        pending.clear()
        cleared = false
        protocolCode = "0"
    }

    override fun write(data: ByteArray) {
        check(open) { "모의 어댑터가 열려 있지 않습니다." }
        val command = String(data, Charsets.US_ASCII).trim().uppercase()
        if (command == ObdClearCommand) cleared = true

        val response = respond(command) ?: return  // null이면 응답하지 않는다(타임아웃 시나리오)
        val text = "\r$response\r\r>"
        text.toByteArray(Charsets.US_ASCII).forEach { pending.addLast(it) }
        releaseAt = System.currentTimeMillis() + RESPONSE_DELAY_MS
    }

    override fun read(buffer: ByteArray): Int {
        if (!open) return -1
        if (pending.isEmpty()) return 0
        if (System.currentTimeMillis() < releaseAt) return 0

        var count = 0
        while (count < buffer.size && pending.isNotEmpty()) {
            buffer[count] = pending.removeFirst()
            count++
        }
        return count
    }

    override fun close() {
        open = false
        pending.clear()
    }

    private fun respond(command: String): String? {
        // AT 명령은 시나리오와 무관하게 항상 정상 응답한다.
        when (command) {
            "ATZ" -> return "ELM327 v2.3"
            "ATI" -> return "ELM327 v2.3"
            "ATRV" -> return "12.4V"
            "ATDP" -> return if (protocolCode == "0") {
                "AUTO, ISO 15765-4 (CAN 11/500)"
            } else {
                "ISO 15765-4 (CAN 11/500)"
            }
            "ATE0", "ATL0", "ATS0", "ATH0", "ATH1" -> return "OK"
        }
        // ATMA — 수신 전용 모드. 버스에 흐르는 프레임을 흉내 낸다.
        if (command == "ATMA") {
            return when (scenario) {
                // 게이트웨이가 브로드캐스트를 막는 상황(2018년 이후 Leaf) 재현
                FakeScenario.NO_DATA, FakeScenario.TIMEOUT -> ""
                FakeScenario.CAN_ERROR -> "CAN ERROR"
                else -> buildString {
                    repeat(3) {
                        append("358 00 08 80\r")
                        append("1DA 01 02 03 04 05 06 07\r")
                        append("5BC 12 34 56 78 90 AB CD\r")
                        append("11A 00 00 00 00\r")
                    }
                }.trimEnd('\r')
            }
        }

        // ATSH<주소> — 요청 주소를 기억한다. ECU 스캔 흉내에 쓴다.
        if (command.startsWith("ATSH")) {
            requestAddress = command.removePrefix("ATSH")
            return "OK"
        }

        // ATSP<n> — 지정된 프로토콜을 기억한다.
        if (command.startsWith("ATSP") && command.length == 5) {
            protocolCode = command.substring(4)
            return "OK"
        }
        if (command.startsWith("AT")) return "OK"

        // 자동 선택으로는 못 붙고 프로토콜 6에서만 통신되는 상황을 재현한다.
        if (scenario == FakeScenario.PROTOCOL_SEARCH_FAIL && protocolCode != "6") {
            return "SEARCHING...\rUNABLE TO CONNECT"
        }

        // UDS 요청(ECU 스캔) — 주소 7E0 에만 ECU 가 있다고 가정한다.
        udsResponse(command)?.let { return it }

        // Mode 01 / 02 / 09 는 시나리오와 무관하게 그럴듯한 값을 돌려준다.
        // (통신 오류·무응답 시나리오는 아래 when 에서 먼저 걸러진다)
        if (scenario != FakeScenario.TIMEOUT &&
            scenario != FakeScenario.CAN_ERROR &&
            scenario != FakeScenario.NO_DATA
        ) {
            infoResponse(command)?.let { return it }
        }

        return when (scenario) {
            FakeScenario.TIMEOUT -> null
            FakeScenario.CAN_ERROR -> "CAN ERROR"
            FakeScenario.NO_DATA -> "NO DATA"
            FakeScenario.NORMAL, FakeScenario.PROTOCOL_SEARCH_FAIL -> normalResponse(command)
            FakeScenario.RECURRING -> recurringResponse(command)
        }
    }

    /**
     * ECU 스캔용 UDS 모의 응답.
     *
     * 주소 `7E0` 에만 ECU 가 있다고 가정한다.
     * TesterPresent 는 정상 응답, 나머지는 부정 응답(7F)으로 돌려주어
     * "ECU 는 있는데 서비스는 거절" 상황까지 재현한다.
     */
    private fun udsResponse(command: String): String? {
        if (command !in UDS_COMMANDS) return null
        if (scenario == FakeScenario.NO_DATA || scenario == FakeScenario.TIMEOUT) return "NO DATA"
        if (scenario == FakeScenario.CAN_ERROR) return "CAN ERROR"
        if (requestAddress != "7E0") return "NO DATA"

        return when (command) {
            "3E00" -> "7E8 02 7E 00"
            // 0x31 = 요청 범위를 벗어남
            "22F190" -> "7E8 03 7F 22 31"
            // 0x12 = 지원하지 않는 하위 기능
            "1902FF" -> "7E8 03 7F 19 12"
            else -> "NO DATA"
        }
    }

    /**
     * Mode 01 / 02 / 09 모의 응답.
     *
     * 값은 모두 가짜다. 실제 Leaf 가 이런 값을 돌려준다는 뜻이 아니며,
     * 화면·파서·로그 저장 흐름을 검증하기 위한 것이다.
     */
    private fun infoResponse(command: String): String? = when (command) {
        // 지원 PID 비트맵 — 01, 04, 05 / 31 / 42 를 지원한다고 가정
        "0100" -> "41 00 98 00 00 01"
        "0120" -> "41 20 00 00 80 01"
        "0140" -> "41 40 40 00 00 00"

        // 경고등 점등 + DTC 개수 (시나리오별 코드 수에 맞춘다)
        "0101" -> if (scenario == FakeScenario.RECURRING) "41 01 84 07 E5 00" else "41 01 81 07 E5 00"

        "0104" -> "41 04 33"
        "0105" -> "41 05 5A"
        "0131" -> "41 31 00 7B"
        "0142" -> "41 42 30 D4"

        // 프리즈 프레임: PID 02(원인 DTC)와 05 를 지원한다고 가정
        "020000" -> "42 00 00 48 00 00 00"
        "020200" -> when {
            // 삭제하면 프리즈 프레임도 함께 사라진다. 삭제 전 저장이 중요한 이유다.
            cleared -> "42 02 00 00 00"
            scenario == FakeScenario.RECURRING -> "42 02 00 0A A6"
            else -> "42 02 00 01 33"
        }
        "020500" -> if (cleared) "NO DATA" else "42 05 00 5A"

        // 차대번호 — ELM327 이 다중 프레임을 줄 번호와 함께 보여 주는 형식
        "0902" -> "014\r0: 49 02 01 53 4A 4E\r1: 46 41 41 5A 45 30 55\r2: 36 30 31 32 33 34 35"

        else -> null
    }

    /** 지시서 14장의 예시 응답 그대로. */
    private fun normalResponse(command: String): String = when (command) {
        "03" -> if (cleared) "43 00" else "43 01 33 00 00"
        "07" -> "47 00 00"
        "0A" -> "4A 00 00"
        "04" -> "44"
        else -> "NO DATA"
    }

    /**
     * 삭제해도 코드가 다시 나타나는 상황.
     * "코드가 다시 나타났다면 활성 고장일 수 있음" 안내를 검증하기 위한 시나리오다.
     */
    private fun recurringResponse(command: String): String = when (command) {
        // 43 04 = Mode 03 응답 + DTC 4건 (CAN 형식의 개수 바이트 포함)
        "03" -> if (cleared) "43 01 0A A6" else "43 04 0A A6 0A 1F 01 33 21 04"
        "07" -> "47 00 00"
        // 영구 코드는 Mode 04로 지워지지 않는다.
        "0A" -> "4A 01 0A A6"
        "04" -> "44"
        else -> "NO DATA"
    }

    private companion object {
        const val ObdClearCommand = "04"

        /** ECU 스캔이 보내는 표준 UDS 읽기 요청. */
        val UDS_COMMANDS = setOf("3E00", "22F190", "1902FF")

        /** 실제 어댑터가 응답하는 데 걸리는 시간을 대략 흉내 낸다. */
        const val RESPONSE_DELAY_MS = 120L
    }
}
