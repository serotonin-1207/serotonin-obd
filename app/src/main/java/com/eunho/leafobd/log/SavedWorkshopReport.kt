package com.eunho.leafobd.log

import org.json.JSONArray
import org.json.JSONObject

data class ReportCode(val code: String, val ecu: String, val status: String) {
    val identity: Pair<String, String> get() = ecu to code
    fun line() = "$code · ECU $ecu · 상태 $status"
}

object ReportComparison {
    fun describe(before: List<ReportCode>, after: List<ReportCode>, verified: Boolean): String = buildString {
        val old = before.map { it.identity }.toSet()
        val now = after.map { it.identity }.toSet()
        appendLine(if (verified) "재조회 완료 · 수리 완료를 뜻하지 않음" else "재조회 불완전/미기록 · 사라진 코드 판정 불가")
        fun group(label: String, values: List<ReportCode>) {
            appendLine("$label: ${values.size}건")
            values.forEach { appendLine("  ${it.line()}") }
        }
        group("삭제 전", before)
        group("삭제 후 수신", after)
        if (verified) group("재조회에서 사라진 코드", before.filter { it.identity !in now })
        group("다시 확인된 코드", after.filter { it.identity in old })
        group(if (verified) "새로 확인된 코드" else "이전 목록에 없던 수신 코드 · 신규 발생 여부 미확인", after.filter { it.identity !in old })
        val previous = before.associateBy { it.identity }
        after.forEach { current ->
            previous[current.identity]?.takeIf { it.status != current.status }?.let {
                appendLine("상태 변화: ${current.code} · ECU ${current.ecu} · ${it.status} → ${current.status}")
            }
        }
    }
}

/** Only validated structural fields enter reports; no raw/free-text payload is copied. */
object SavedWorkshopReport {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun create(json: String): String {
        require(json.length <= MAX_BYTES) { "기록이 보고서 처리 한도를 초과했습니다." }
        val root = JSONObject(json)
        require(root.has("startedAt") && root.has("dtcBeforeClear")) { "지원하는 진단 기록 형식이 아닙니다." }
        fun codes(array: JSONArray?): List<ReportCode> {
            if (array == null) return emptyList()
            require(array.length() <= 4096) { "코드 수가 보고서 처리 한도를 초과했습니다." }
            return (0 until array.length()).map { i ->
                val row = array.getJSONObject(i)
                val code = row.optString("code").takeIf { Regex("[PCBU][0-3][0-9A-F]{3}(-[0-9A-F]{2})?").matches(it) }
                    ?: throw IllegalArgumentException("코드 형식을 확인할 수 없습니다.")
                val ecu = row.optString("ecu").takeIf { Regex("(?:[0-9A-F]{3}|[0-9A-F]{8})").matches(it) } ?: "미확인"
                val status = when (val value = row.opt("status")) {
                    is Number -> value.toInt().takeIf { it in 0..255 }?.let { "0x%02X".format(it) } ?: "미확인"
                    "STORED" -> "저장"
                    "PENDING" -> "보류"
                    "PERMANENT" -> "영구"
                    else -> "미확인"
                }
                ReportCode(code, ecu, status)
            }
        }
        val reportCodes = mutableListOf<ReportCode>()
        val before = codes(root.optJSONArray("dtcBeforeClear"))
        val after = codes(root.optJSONArray("dtcAfterClear"))
        reportCodes.addAll(before + after)
        val timestamp = runCatching { java.time.LocalDateTime.parse(root.getString("startedAt"), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toString() }.getOrDefault("미확인")
        return buildString {
            appendLine("정비소 전달 보고서")
            appendLine("진단 시각: $timestamp · 원본 기록의 현지 시각")
            appendLine(if (root.optBoolean("simulated")) "모의 데이터 · 실제 차량 결과 아님" else "저장된 진단 기록 기준")
            appendLine("차량명·VIN·장치 주소·원시 응답 제외")
            appendLine("\n표준 OBD")
            if (root.optBoolean("batteryOnly")) appendLine("배터리 전용 조회 기록 · 오류코드 미조회")
            else if (root.optBoolean("clearAttempted")) {
                val verified = root.opt("clearVerificationComplete") == true && root.optJSONArray("dtcAfterClear") != null && before.all { it.ecu != "미확인" }
                appendLine(ReportComparison.describe(before, after, verified))
            } else {
                appendLine("삭제 시도 없음 · 코드 ${before.size}건")
                before.forEach { appendLine(it.line()) }
                appendLine("코드 없음만으로 정상/미지원/미조회를 구분할 수 없습니다.")
            }
            appendLine("\nECU 전용 진단")
            if (!root.has("reportSchema")) appendLine("이전 형식 · ECU 전용 코드와 삭제 비교가 구조화되어 있지 않아 복원 불가")
            else if (root.optBoolean("udsClearAttempted")) {
                val comparison = root.optJSONObject("udsComparison")
                if (comparison == null) appendLine("삭제 시도 있음 · 비교 결과 미기록") else {
                    val old = codes(comparison.optJSONArray("before")); val next = codes(comparison.optJSONArray("after"))
                    reportCodes.addAll(old + next)
                    val verified = comparison.opt("verificationComplete") == true && comparison.optJSONArray("before") != null && comparison.optJSONArray("after") != null && (old + next).all { it.ecu != "미확인" }
                    appendLine(ReportComparison.describe(old, next, verified))
                }
            } else {
                appendLine("삭제 시도 없음 · ${if (root.opt("udsReportComplete") == true) "조회 완전" else "조회 불완전/미조회"}")
                codes(root.optJSONArray("udsReportCodes")).forEach { reportCodes.add(it); appendLine(it.line()) }
            }
            appendLine(CodeReportNotes.create(reportCodes.map { it.code }))
            appendLine("\n배터리 그래프: 이 진단 JSON에는 셀 측정값이 저장되지 않아 포함하지 않았습니다.")
            appendLine("0x 상태는 ECU가 보고한 상태 비트입니다. 코드가 남아 있어도 상태가 달라질 수 있습니다.")
            appendLine("삭제 후 소거는 수리 완료 또는 운행 가능 판정이 아닙니다. 서비스센터가 안내한 후속 점검 조건을 확인하세요.")
        }
    }
}
