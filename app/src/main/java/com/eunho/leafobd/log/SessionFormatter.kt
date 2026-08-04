package com.eunho.leafobd.log

import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.obd.PidValue
import com.eunho.leafobd.util.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 진단 세션을 파일 내용(텍스트/JSON)과 파일명으로 바꾸는 순수 함수 모음.
 *
 * Android API에 의존하지 않으므로 JVM 단위 테스트로 형식을 검증할 수 있다.
 */
object SessionFormatter {

    private val FILE_NAME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

    private val DISPLAY_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 앱 표시 이름(리포트·JSON 등에 쓴다). */
    const val APP_NAME = "Serotonin OBD"

    /** `SerotoninOBD_2026-07-29_223500` (확장자 없음) */
    fun fileBaseName(session: DiagnosticSession, zone: ZoneId = ZoneId.systemDefault()): String =
        "SerotoninOBD_" + FILE_NAME_FORMAT.format(session.startedAt.atZone(zone))

    fun formatInstant(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DISPLAY_FORMAT.format(instant.atZone(zone))

    // ------------------------------------------------------------------
    // 사람이 읽는 텍스트
    // ------------------------------------------------------------------

    fun toText(session: DiagnosticSession, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()

        sb.appendLine("[$APP_NAME 진단 결과]")
        sb.appendLine()
        if (session.simulated) {
            sb.appendLine("*** 주의: 이 세션은 모의 데이터입니다. 실제 차량 진단 결과가 아닙니다. ***")
            sb.appendLine()
        }
        sb.appendLine("차량: ${session.vehicle}")
        sb.appendLine("차대번호(마스킹): ${session.vin ?: "-"}")
        sb.appendLine("진단 시각: ${formatInstant(session.startedAt, zone)}")
        sb.appendLine("Android 버전: ${session.androidVersion ?: "-"}")
        sb.appendLine("앱 버전: ${session.appVersion ?: "-"}")
        sb.appendLine("어댑터 이름: ${session.deviceName ?: "-"}")
        sb.appendLine("어댑터 주소(마스킹): ${session.deviceAddressMasked ?: "-"}")
        sb.appendLine("어댑터 정보 ATI: ${session.adapterInfo ?: "-"}")
        sb.appendLine("어댑터 전압 ATRV: ${session.adapterVoltage ?: "-"}")
        sb.appendLine("연결 프로토콜: ${session.protocol ?: "-"}")
        session.protocolAttempts?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("프로토콜 시도 내역: $it")
        }
        sb.appendLine("CAN 헤더 표시: ${if (session.headersOn) "켬 (ATH1)" else "끔 (ATH0)"}")
        sb.appendLine()

        session.monitorStatus?.let { status ->
            sb.appendLine("[경고등 및 모니터 상태 — 0101]")
            sb.appendLine("경고등(MIL): ${status.milLabel}")
            sb.appendLine("ECU 보고 DTC 개수: ${status.dtcCount}")
            status.ecu?.let { sb.appendLine("응답 ECU: $it") }
            sb.appendLine("원시: ${status.rawHex}")
            sb.appendLine()
        }

        session.supportedPidsHex?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("[지원 PID 비트맵]")
            sb.appendLine(it)
            sb.appendLine()
        }

        if (session.liveValues.isNotEmpty()) {
            sb.appendLine("[실시간 값 — Mode 01]")
            session.liveValues.forEach { value ->
                sb.appendLine("  - ${value.label}: ${value.display}  (원시 ${value.rawHex})")
            }
            sb.appendLine()
        }

        session.freezeFrame?.takeIf { !it.isEmpty }?.let { frame ->
            sb.appendLine("[프리즈 프레임 — Mode 02]")
            sb.appendLine("※ 고장이 확정되던 순간의 스냅샷입니다. 오류코드를 삭제하면 차량에서는 사라집니다.")
            if (!frame.hasData) {
                sb.appendLine("!! 해석된 값이 없습니다. 차량에 저장된 값이 없거나 통신이 실패한 경우입니다.")
                sb.appendLine("   아래 원시 응답으로 어느 쪽인지 판단하십시오.")
            }
            sb.appendLine("원인 DTC: ${frame.triggerDtc ?: "없음 또는 확인 불가"}")
            frame.values.forEach { value ->
                sb.appendLine("  - ${value.label}: ${value.display}  (원시 ${value.rawHex})")
            }
            if (frame.rawResponses.isNotEmpty()) {
                sb.appendLine("  원시 응답:")
                frame.rawResponses.forEach { (command, raw) ->
                    val cleaned = raw.replace("\r", "\\r").replace("\n", "\\n")
                    sb.appendLine("    $command -> $cleaned")
                }
            }
            sb.appendLine()
        }

        if (session.udsResults.isNotEmpty()) {
            sb.appendLine("[ECU별 오류코드 — UDS 19 02]")
            sb.appendLine("※ 표준 OBD 로는 읽히지 않는 코드입니다. ECU 주소를 직접 지정해 읽었습니다.")
            sb.appendLine("※ 표기는 코드-고장유형(FTB) 형식입니다. 정비소에는 뒤 2자리까지 전달하십시오.")
            sb.appendLine()

            val withCodes = session.udsResults.filter { it.hasCodes }
            withCodes.forEach { result ->
                sb.appendLine("ECU ${result.ecu} — ${result.codes.size}건")
                result.message?.let { sb.appendLine("  !! $it") }
                result.codes.forEach { code ->
                    sb.appendLine(
                        "  - ${code.fullCode} [${code.systemLabel}] " +
                            "상태 %02X (%s)".format(code.statusByte, code.statusLabels.joinToString(", "))
                    )
                    sb.appendLine("    원본 ${code.rawBytes}")
                }
                sb.appendLine()
            }

            val clean = session.udsResults.filterNot { it.hasCodes }.map { it.ecu }
            if (clean.isNotEmpty()) {
                sb.appendLine("코드 없음: ${clean.joinToString(", ")}")
                sb.appendLine()
            }
        }

        appendCodes(sb, "저장 DTC (Mode 03)", session.dtcBeforeClear.filter { it.source.command == "03" })
        appendCodes(sb, "보류 DTC (Mode 07)", session.dtcBeforeClear.filter { it.source.command == "07" })
        appendCodes(sb, "영구 DTC (Mode 0A)", session.dtcBeforeClear.filter { it.source.command == "0A" })

        sb.appendLine("삭제 시도 여부: ${if (session.clearAttempted) "예" else "아니오"}")
        if (session.clearAttempted) {
            sb.appendLine("삭제 응답: ${session.clearResponse?.trim() ?: "-"}")
            sb.appendLine()
            appendCodes(sb, "삭제 후 DTC", session.dtcAfterClear)
            appendCodeList(sb, "사라진 코드", session.clearedCodes)
            appendCodeList(sb, "남은 코드(재발 가능)", session.remainingCodes)
            appendCodeList(sb, "새로 나타난 코드", session.newCodes)
            if (session.remainingCodes.isNotEmpty() || session.newCodes.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("!! 코드가 다시 나타났습니다. 활성 고장일 수 있습니다. 전문 점검을 받으십시오.")
            }
        }
        sb.appendLine()

        sb.appendLine("[원시 로그]")
        session.commands.forEach { log ->
            sb.appendLine("-".repeat(50))
            sb.appendLine("시각: ${formatInstant(log.timestamp, zone)}")
            sb.appendLine("명령: ${log.command}")
            sb.appendLine("소요: ${log.elapsedMs} ms")
            sb.appendLine("성공: ${if (log.success) "예" else "아니오"}")
            log.errorMessage?.let { sb.appendLine("오류: $it") }
            sb.appendLine("원시 응답: ${log.rawResponse.replace("\r", "\\r").replace("\n", "\\n")}")
            log.normalizedResponse?.let { sb.appendLine("정규화: $it") }
        }
        sb.appendLine("-".repeat(50))
        sb.appendLine()
        sb.appendLine("특이사항:")
        sb.appendLine()
        sb.appendLine("※ 이 앱은 개인 진단 보조 도구이며 정비소의 전문 진단을 대체하지 않습니다.")
        sb.appendLine("※ 오류코드 삭제는 고장을 수리하지 않습니다.")

        return sb.toString()
    }

    private fun appendCodes(sb: StringBuilder, title: String, codes: List<DtcCode>) {
        sb.appendLine("$title: ${if (codes.isEmpty()) "없음" else "${codes.size}건"}")
        codes.forEach { code ->
            val ecu = code.ecu?.let { " ECU $it" }.orEmpty()
            sb.appendLine("  - ${code.code} [${code.systemLabel}]$ecu (원본 ${code.rawBytes})")
        }
        sb.appendLine()
    }

    private fun appendCodeList(sb: StringBuilder, title: String, codes: List<DtcCode>) {
        sb.appendLine("$title: ${if (codes.isEmpty()) "없음" else codes.joinToString(", ") { it.code }}")
    }

    // ------------------------------------------------------------------
    // 구조화된 JSON
    // ------------------------------------------------------------------

    fun toJson(session: DiagnosticSession, zone: ZoneId = ZoneId.systemDefault()): String {
        fun codeJson(code: DtcCode): String = Json.obj(
            "code" to Json.str(code.code),
            "status" to Json.str(code.status.name),
            "source" to Json.str(code.source.command),
            "rawBytes" to Json.str(code.rawBytes),
            "system" to Json.str(code.systemLabel),
            "ecu" to Json.str(code.ecu),
            "description" to Json.str(code.description),
            indent = "    "
        )

        fun pidJson(value: PidValue): String = Json.obj(
            "pid" to Json.str("0x%02X".format(value.id)),
            "label" to Json.str(value.label),
            "value" to Json.num(value.value),
            "unit" to Json.str(value.pid?.unit),
            "rawBytes" to Json.str(value.rawHex),
            indent = "    "
        )

        fun commandJson(log: CommandLog): String = Json.obj(
            "timestamp" to Json.str(formatInstant(log.timestamp, zone)),
            "command" to Json.str(log.command),
            "rawResponse" to Json.str(log.rawResponse),
            "normalizedResponse" to Json.str(log.normalizedResponse),
            "elapsedMs" to Json.num(log.elapsedMs),
            "success" to Json.bool(log.success),
            "errorMessage" to Json.str(log.errorMessage),
            indent = "    "
        )

        return Json.obj(
            "app" to Json.str(APP_NAME),
            "appVersion" to Json.str(session.appVersion),
            "androidVersion" to Json.str(session.androidVersion),
            "vehicle" to Json.str(session.vehicle),
            "vinMasked" to Json.str(session.vin),
            "simulated" to Json.bool(session.simulated),
            "headersOn" to Json.bool(session.headersOn),
            "sessionId" to Json.str(session.id),
            "startedAt" to Json.str(formatInstant(session.startedAt, zone)),
            "deviceName" to Json.str(session.deviceName),
            "deviceAddressMasked" to Json.str(session.deviceAddressMasked),
            "adapterInfo" to Json.str(session.adapterInfo),
            "adapterVoltage" to Json.str(session.adapterVoltage),
            "protocol" to Json.str(session.protocol),
            "milOn" to (session.monitorStatus?.let { Json.bool(it.milOn) } ?: "null"),
            "dtcCountReported" to Json.num(session.monitorStatus?.dtcCount),
            "supportedPidsHex" to Json.str(session.supportedPidsHex),
            "liveValues" to Json.array(session.liveValues.map(::pidJson), indent = "  "),
            "freezeFrame" to (
                session.freezeFrame?.let { frame ->
                    Json.obj(
                        "triggerDtc" to Json.str(frame.triggerDtc),
                        "values" to Json.array(frame.values.map(::pidJson), indent = "    "),
                        "rawResponses" to Json.obj(
                            *frame.rawResponses.map { (k, v) -> k to Json.str(v) }.toTypedArray(),
                            indent = "    "
                        ),
                        indent = "  "
                    )
                } ?: "null"
                ),
            "dtcBeforeClear" to Json.array(session.dtcBeforeClear.map(::codeJson), indent = "  "),
            "clearAttempted" to Json.bool(session.clearAttempted),
            "clearResponse" to Json.str(session.clearResponse),
            "dtcAfterClear" to Json.array(session.dtcAfterClear.map(::codeJson), indent = "  "),
            "clearedCodes" to Json.array(session.clearedCodes.map { Json.str(it.code) }, indent = "  "),
            "remainingCodes" to Json.array(session.remainingCodes.map { Json.str(it.code) }, indent = "  "),
            "newCodes" to Json.array(session.newCodes.map { Json.str(it.code) }, indent = "  "),
            "commands" to Json.array(session.commands.map(::commandJson), indent = "  "),
            "disclaimer" to Json.str(
                "이 앱은 개인 진단 보조 도구이며 정비소의 전문 진단을 대체하지 않습니다. " +
                    "오류코드 삭제는 고장을 수리하지 않습니다."
            )
        )
    }

    /**
     * 결과 기록 양식(지시서 22장)에 맞춘 복사용 요약.
     * 사용자가 한 번에 복사해 게시판·정비소에 전달할 수 있게 한다.
     */
    fun toClipboardSummary(session: DiagnosticSession, zone: ZoneId = ZoneId.systemDefault()): String {
        val stored = session.dtcBeforeClear.filter { it.source.command == "03" }
        val pending = session.dtcBeforeClear.filter { it.source.command == "07" }
        val permanent = session.dtcBeforeClear.filter { it.source.command == "0A" }

        fun list(codes: List<DtcCode>) =
            if (codes.isEmpty()) "없음" else codes.joinToString(", ") { it.code }

        return buildString {
            appendLine("[$APP_NAME 진단 결과]")
            appendLine()
            if (session.simulated) appendLine("(모의 데이터 — 실제 차량 결과 아님)")
            appendLine("차량: ${session.vehicle}")
            appendLine("차대번호(마스킹): ${session.vin ?: "-"}")
            appendLine("진단 시각: ${formatInstant(session.startedAt, zone)}")
            appendLine("Android 버전: ${session.androidVersion ?: "-"}")
            appendLine("앱 버전: ${session.appVersion ?: "-"}")
            appendLine("어댑터 이름: ${session.deviceName ?: "-"}")
            appendLine("어댑터 정보 ATI: ${session.adapterInfo ?: "-"}")
            appendLine("어댑터 전압 ATRV: ${session.adapterVoltage ?: "-"}")
            appendLine("연결 프로토콜: ${session.protocol ?: "-"}")
            session.monitorStatus?.let {
                appendLine("경고등(MIL): ${it.milLabel} / ECU 보고 DTC 개수: ${it.dtcCount}")
            }
            session.freezeFrame?.takeIf { it.hasData }?.let {
                appendLine("프리즈 프레임 원인 DTC: ${it.triggerDtc ?: "확인 불가"}")
            }
            appendLine("저장 DTC: ${list(stored)}")
            appendLine("보류 DTC: ${list(pending)}")
            appendLine("영구 DTC: ${list(permanent)}")
            appendLine("삭제 시도 여부: ${if (session.clearAttempted) "예" else "아니오"}")
            appendLine("삭제 응답: ${session.clearResponse?.trim() ?: "-"}")
            appendLine("삭제 후 DTC: ${list(session.dtcAfterClear)}")
            appendLine("원시 로그: ${session.commands.size}건 (첨부 파일 참조)")
            appendLine("특이사항:")
        }
    }
}
