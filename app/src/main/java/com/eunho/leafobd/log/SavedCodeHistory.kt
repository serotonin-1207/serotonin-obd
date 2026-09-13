package com.eunho.leafobd.log

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object SavedCodeHistory {
    fun parse(text: String): CodeHistoryRecord {
        require(text.length <= SavedWorkshopReport.MAX_BYTES) { "기록 크기가 비교 한도를 초과했습니다." }
        val root = JSONObject(text)
        require(root.opt("simulated") == false) { "모의 기록 또는 모의 여부 미확인 기록은 비교할 수 없습니다." }
        require(!root.optBoolean("batteryOnly")) { "배터리 전용 기록은 오류코드 비교에서 제외하세요." }
        val id = root.getString("sessionId")
        require(id.isNotBlank()) { "세션 식별값이 없는 기록입니다." }
        val time = LocalDateTime.parse(root.getString("startedAt"), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT))
        val observations = mutableListOf<CodeObservation>()
        fun add(array: JSONArray?, phase: String) {
            if (array == null) return
            require(array.length() <= 4096) { "코드 수가 비교 한도를 초과했습니다." }
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                val code = row.getString("code")
                require(Regex("[PCBU][0-3][0-9A-F]{3}(-[0-9A-F]{2})?").matches(code)) { "코드 형식을 확인할 수 없습니다." }
                val ecu = row.optString("ecu").takeIf { Regex("(?:[0-9A-F]{3}|[0-9A-F]{8})").matches(it) } ?: "미확인"
                val status = when (val value = row.opt("status")) {
                    "STORED" -> "저장"
                    "PENDING" -> "보류"
                    "PERMANENT" -> "영구"
                    is Number -> value.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 && it in 0.0..255.0 }?.let { String.format(Locale.ROOT, "0x%02X", it.toInt()) } ?: "미확인"
                    else -> "미확인"
                }
                observations.add(CodeObservation(code, ecu, phase, status))
            }
        }
        add(root.getJSONArray("dtcBeforeClear"), "표준 OBD 최초 조회")
        if (root.optBoolean("clearAttempted")) add(root.optJSONArray("dtcAfterClear"), "표준 OBD 삭제 후 수신")
        val schema = root.optInt("reportSchema", 0)
        if (root.has("reportSchema")) {
            require(schema in 1..2) { "지원하지 않는 기록 버전입니다." }
            add(root.optJSONArray("udsReportCodes"), "ECU 전용 조회")
            if (root.optBoolean("udsClearAttempted")) root.optJSONObject("udsComparison")?.let {
                add(it.optJSONArray("before"), "ECU 삭제 전 수신")
                add(it.optJSONArray("after"), "ECU 삭제 후 수신")
            }
        }
        val vehicleSnapshot = root.optJSONObject("vehicleProfile")
        val coverageId = vehicleSnapshot?.optString("coverageId")?.takeIf { Regex("[a-z0-9-]{3,100}").matches(it) }
        val coverageLabel = vehicleSnapshot?.optString("coverageLabel")?.trim()?.takeIf { it.length in 1..100 && it.none(Char::isISOControl) }
        val communication = root.optJSONObject("communication")
        fun optionalBoolean(name: String): Boolean? = communication?.opt(name) as? Boolean
        val udsCount = communication?.opt("udsRespondingEcuCount")?.let { value ->
            (value as? Number)?.toInt()?.takeIf { it in 0..4096 && value.toDouble() == it.toDouble() }
        }
        return CodeHistoryRecord(id, time, observations, legacyUds = !root.has("reportSchema"),
            coverageId = if (schema >= 2) coverageId else null,
            coverageLabel = if (schema >= 2 && coverageId != null) coverageLabel else null,
            protocolIdentified = if (schema >= 2) optionalBoolean("protocolIdentified") else null,
            standardDataObserved = if (schema >= 2) optionalBoolean("standardDataObserved") else null,
            udsRespondingEcuCount = if (schema >= 2) udsCount else null)
    }
}
