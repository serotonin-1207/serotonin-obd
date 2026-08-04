package com.eunho.leafobd.obd

import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.Elm327Command
import com.eunho.leafobd.log.CommandLog
import kotlinx.coroutines.delay

/** 스캔 진행 상황. 화면에 실시간으로 보여 주기 위한 것. */
data class EcuScanProgress(
    val current: Int,
    val total: Int,
    val address: String,
    val hits: Int
)

/**
 * 응답하는 ECU 주소를 찾는다.
 *
 * ## 왜 필요한가
 *
 * 지금까지 이 앱은 요청을 **방송 주소(7DF)** 로만 보냈다.
 * "거기 누구든 대답해" 방식인데, 2018년 이후 Leaf 처럼 OBD 포트에
 * 게이트웨이가 있는 차량은 이 방식을 통과시키지 않을 수 있다.
 *
 * 여기서는 `ATSH` 로 **개별 ECU 주소를 직접 지정해서** 부른다.
 *
 * ## 안전
 *
 * 보내는 것은 [EcuProbeRequest] 에 정의된 **ISO 14229 표준 읽기 요청뿐**이다.
 * 진단 세션 변경, 보안 접근, 데이터 쓰기, 루틴 실행, ECU 리셋은 하지 않는다.
 * 응답하지 않는 주소는 아무 일도 일어나지 않는다(해당 ID 를 모르는 노드는 무시한다).
 */
class EcuScanner(
    private val client: Elm327Client,
    private val headersOn: Boolean = true
) {

    /**
     * @param addresses 확인할 주소 목록
     * @param deepRequests 응답한 주소에 추가로 보낼 요청들
     * @param onProgress 진행 상황 콜백
     */
    suspend fun scan(
        addresses: List<String>,
        deepRequests: List<EcuProbeRequest> = listOf(
            EcuProbeRequest.READ_VIN,
            EcuProbeRequest.READ_DTC
        ),
        onProgress: suspend (EcuScanProgress) -> Unit = {}
    ): Pair<List<CommandLog>, EcuScanResult> {
        val logs = ArrayList<CommandLog>()
        val hits = ArrayList<EcuScanHit>()
        val startedAt = System.currentTimeMillis()

        // 스캔 준비: 헤더를 켜야 어느 주소가 응답했는지 알 수 있다.
        // 응답 대기 시간을 줄여야 주소를 많이 훑을 수 있다.
        logs.add(client.send(Elm327Command.HEADERS_ON))
        logs.add(client.send(Elm327Command("ATAL", "긴 메시지 허용", optional = true)))
        logs.add(client.send(Elm327Command("ATAR", "수신 주소 자동", optional = true)))
        logs.add(client.send(Elm327Command(Config.SET_TIMEOUT, "응답 대기 시간 단축", optional = true)))

        try {
            addresses.forEachIndexed { index, address ->
                onProgress(EcuScanProgress(index + 1, addresses.size, address, hits.size))

                val setHeader = client.send(Elm327Command("ATSH$address", "요청 주소 $address"))
                logs.add(setHeader)
                if (!setHeader.success) return@forEachIndexed

                // 다중 프레임 응답을 끝까지 받으려면 흐름 제어 프레임을 우리가 보내야 한다.
                // ATSH 로 주소를 직접 지정하면 어댑터가 자동 흐름 제어를 하지 못하기 때문이다.
                flowControlCommands(address).forEach { logs.add(client.send(it)) }

                // 1단계 — 가장 가벼운 요청으로 존재 여부만 본다.
                val probe = EcuProbeRequest.TESTER_PRESENT
                val log = client.send(probe.command, Config.SCAN_TIMEOUT_MS)
                logs.add(log)

                // 늦게 오는 응답이 다음 주소로 새지 않도록 잠깐 쉬어 준다.
                delay(Config.SETTLE_MS)

                val hit = EcuScanParser.parse(log.rawResponse, address, probe, headersOn = true)
                    ?: return@forEachIndexed

                hits.add(hit)

                // 2단계 — 응답한 주소에만 실제 읽기 요청을 보낸다.
                for (request in deepRequests) {
                    val deepLog = client.send(request.command, Config.DEEP_TIMEOUT_MS)
                    logs.add(deepLog)
                    EcuScanParser.parse(deepLog.rawResponse, address, request, headersOn = true)
                        ?.let { hits.add(it) }
                }
            }
        } finally {
            // 설정을 원래대로 되돌린다.
            logs.add(client.send(Elm327Command("ATSH7DF", "요청 주소 방송으로 복귀", optional = true)))
            logs.add(client.send(Elm327Command(Config.RESET_TIMEOUT, "응답 대기 시간 복귀", optional = true)))
            if (!headersOn) logs.add(client.send(Elm327Command.HEADERS_OFF))
        }

        return logs to EcuScanResult(
            hits = hits,
            scannedAddresses = addresses.size,
            durationMs = System.currentTimeMillis() - startedAt,
            deep = addresses.size > EcuAddresses.STANDARD.size
        )
    }

    companion object {
        /**
         * ISO-TP 흐름 제어 설정.
         *
         * 다중 프레임 응답(첫 프레임 `1x`)을 받으면 수신 측이 "계속 보내라"는
         * 흐름 제어 프레임을 돌려줘야 나머지가 온다.
         * `ATSH` 로 주소를 직접 지정하면 어댑터가 이 프레임의 주소를 알 수 없으므로
         * 아래 세 명령으로 직접 지정한다. 모두 ELM327 표준 명령이다.
         *
         *  - `ATFCSH<주소>` — 흐름 제어 프레임을 보낼 주소
         *  - `ATFCSD3000<간격>` — 30(계속 보내라) 00(블록 크기 무제한) 간격(STmin)
         *  - `ATFCSM1` — 위에서 지정한 헤더와 데이터를 사용
         *
         * ### 간격(STmin)을 0 으로 두지 않는 이유
         *
         * 0 이면 ECU 가 프레임을 쉬지 않고 몰아서 보낸다.
         * 실제 차량에서 51바이트 응답을 받을 때 연속 프레임 하나가 유실되어
         * 그 뒤 데이터의 정렬이 어긋난 적이 있다(2026-08-04).
         * Bluetooth 구간이 그 속도를 못 따라간 것으로 보인다.
         *
         * 프레임 간 최소 간격을 두면 조금 느려지지만 유실이 줄어든다.
         *
         * @param stMinMs 프레임 간 최소 간격(ms). 0~127 사이 값.
         */
        fun flowControlCommands(address: String, stMinMs: Int = DEFAULT_ST_MIN_MS): List<Elm327Command> {
            val separation = "%02X".format(stMinMs.coerceIn(0, 0x7F))
            return listOf(
                Elm327Command("ATFCSH$address", "흐름 제어 주소", optional = true),
                Elm327Command("ATFCSD3000$separation", "흐름 제어 데이터 (간격 ${stMinMs}ms)", optional = true),
                Elm327Command("ATFCSM1", "흐름 제어 사용", optional = true)
            )
        }

        /** 기본 프레임 간격. 유실을 줄이면서도 체감 지연이 크지 않은 값. */
        const val DEFAULT_ST_MIN_MS = 20

        /** 재시도할 때 쓰는 더 여유 있는 간격. */
        const val SLOW_ST_MIN_MS = 50
    }

    private object Config {
        /**
         * `ATST hh` — 어댑터 응답 대기 시간을 hh(16진) × 4ms.
         *
         * 0x32 = 50 → 200ms.
         *
         * 너무 짧게 잡으면(0x20 = 128ms) 응답이 느린 ECU 의 답이
         * **다음 주소를 물어본 뒤에 도착해 엉뚱한 주소의 응답으로 기록된다.**
         * 2026-08-04 실차에서 실제로 이 현상이 발생해 존재하지 않는 주소가
         * 응답 목록에 들어갔다.
         */
        const val SET_TIMEOUT = "ATST32"

        /** 기본값 복귀. */
        const val RESET_TIMEOUT = "ATST64"

        /** 존재 확인 요청 하나에 기다릴 시간. */
        const val SCAN_TIMEOUT_MS = 2_000L

        /** 응답한 주소에 보내는 실제 읽기 요청 대기 시간. */
        const val DEEP_TIMEOUT_MS = 3_000L

        /**
         * 주소를 넘어가기 전 잠깐 쉬는 시간.
         *
         * 늦게 도착한 응답이 다음 주소의 것으로 기록되지 않도록,
         * 여기서 도착시킨 뒤 [Elm327Client] 의 전송 전 버퍼 비우기로 걸러 낸다.
         */
        const val SETTLE_MS = 120L
    }
}
