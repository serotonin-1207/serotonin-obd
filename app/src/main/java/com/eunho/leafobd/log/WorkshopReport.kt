package com.eunho.leafobd.log

import com.eunho.leafobd.data.BatterySnapshot
import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.obd.UdsDtcReadResult
import java.util.Locale

/** Allowlist export: never copy free-text vehicle names, VIN, MAC, raw frames or sensor labels. */
object WorkshopReport {
    private fun code(value: String) = value.takeIf { Regex("[PCBU][0-3][0-9A-F]{3}(-[0-9A-F]{2})?").matches(it) } ?: "형식 미확인"
    private fun ecu(value: String?) = value?.takeIf { Regex("(?:[0-9A-F]{3}|[0-9A-F]{8})").matches(it) } ?: "미확인"
    private fun number(value: Double?, digits: Int) = value?.takeIf { it.isFinite() }?.let { String.format(Locale.ROOT, "%.${digits}f", it) } ?: "미확인"

    fun create(codes: List<DtcCode>, uds: List<UdsDtcReadResult>, battery: BatterySnapshot?, simulated: Boolean): String = buildString {
        appendLine("정비소 전달용 요약")
        appendLine(if (simulated) "모의 진단 데이터 포함 · 실제 차량 판정 금지" else "현재 앱에 열린 결과 요약")
        appendLine("차량 이름·VIN·차량번호·장치 주소·원시 응답은 포함하지 않았습니다.")
        appendLine("오류코드와 배터리는 서로 다른 조회 결과일 수 있습니다. 정비소에서 대상 차량과 측정 조건을 확인하세요.")
        appendLine("\n표준 오류코드 (${codes.size}건)")
        if (codes.isEmpty()) appendLine("표시할 코드 없음 · 미조회/미지원/정상 여부는 이 요약만으로 구분 불가")
        codes.forEach { appendLine("${code(it.code)} · ${it.status.label} · ECU ${ecu(it.ecu)}") }
        appendLine("\nECU 전용 조회 (${uds.size}곳)")
        if (uds.isEmpty()) appendLine("조회 결과 없음")
        uds.forEach { result ->
            appendLine("ECU ${ecu(result.ecu)} · ${if (result.complete) "응답 완전" else "응답 불완전/미확인"}")
            result.codes.forEach { appendLine("  ${code(it.fullCode)} · 상태 ${it.statusByte.and(255).toString(16).uppercase(Locale.ROOT).padStart(2, '0')}") }
            if (result.codes.isEmpty()) appendLine("  표시할 코드 없음")
        }
        appendLine(CodeReportNotes.create(codes.map { it.code } + uds.flatMap { it.codes }.map { it.fullCode }))
        appendLine("\n별도로 열린 배터리 기록")
        if (battery == null) appendLine("열린 기록 없음") else {
            if (battery.demo) appendLine("가상 배터리 예시 · 실제 측정 아님")
            val timestamp = runCatching { java.time.OffsetDateTime.parse(battery.capturedAt).toString() }.getOrDefault("시각 미확인")
            appendLine("측정 시각: $timestamp")
            appendLine("SOC ${number(battery.soc, 1)}% · BMS 보고 SOH ${number(battery.reportedSoh, 2)}%")
            appendLine("셀 전압 ${battery.channels.count { it.volts != null }}/${battery.expectedChannels} · 전체 편차 ${number(battery.spreadMv, 1)} mV")
            val temperatures = (battery.temperatureSensors.map { it.second } + battery.channels.mapNotNull { it.celsius }).filter { it.isFinite() }
            appendLine("온도 ${number(temperatures.minOrNull(), 1)}~${number(temperatures.maxOrNull(), 1)} °C")
        }
        appendLine("\n이 요약에는 삭제 전후 비교가 포함되지 않습니다. 삭제 결과는 앱의 해당 진단 기록에서 확인하세요.")
        appendLine("정비 확인: 발생 증상 / 발생 조건 / 동반 코드 / 제조사 적용 절차 / 후속 점검")
        appendLine("SOH는 BMS 추정값이며 코드 소거는 수리 완료나 운행 가능 판정이 아닙니다.")
    }
}
