package com.eunho.leafobd.log

import java.time.LocalDateTime

data class CodeObservation(val code: String, val ecu: String, val phase: String, val status: String)
data class CodeHistoryRecord(
    val id: String,
    val time: LocalDateTime,
    val observations: List<CodeObservation>,
    val legacyUds: Boolean = false,
    val coverageId: String? = null,
    val coverageLabel: String? = null,
    val protocolIdentified: Boolean? = null,
    val standardDataObserved: Boolean? = null,
    val udsRespondingEcuCount: Int? = null
)

/** Positive observations only: an empty or failed query never proves disappearance. */
object CodeHistory {
    fun describe(records: List<CodeHistoryRecord>): String {
        require(records.size in 2..30) { "같은 차량의 기록을 2~30개 선택하세요." }
        require(records.map { it.id }.distinct().size == records.size) { "같은 진단 세션이 중복되었습니다. 다른 기록을 선택하세요." }
        val ordered = records.sortedBy { it.time }
        val observations = ordered.flatMap { record -> record.observations.map { record to it } }
        val groups = observations.filter { (_, code) -> code.ecu != "미확인" }
            .groupBy { (_, code) -> code.ecu to code.code }
        return buildString {
            appendLine("선택한 같은 차량 기록 ${records.size}건 비교")
            appendLine("차량 일치는 사용자 확인 기준 · 시각은 원본 기록의 현지 시각")
            appendLine("반복 수신은 고장 재발·원인 일치 판정이 아닙니다. 조회 실패·미지원·기록 공백으로 소거 여부를 판단하지 않습니다.")
            appendLine("이전 형식으로 ECU 전용 기록을 복원할 수 없는 세션: ${records.count { it.legacyUds }}건")
            appendLine("ECU 미확인으로 비교에서 제외한 수신 항목: ${observations.count { it.second.ecu == "미확인" }}건")
            if (groups.isEmpty()) appendLine("비교 가능한 수신 코드 없음 · 정상 판정 아님")
            groups.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).forEach { (key, values) ->
                val sessions = values.map { it.first.id }.distinct().size
                appendLine("\n${key.second} · ECU ${key.first}")
                appendLine(if (sessions > 1) "서로 다른 진단 ${sessions}건에서 반복 수신" else "선택 범위의 진단 1건에서 수신")
                values.distinctBy { Triple(it.first.id, it.second.phase, it.second.status) }.forEach { (record, observation) ->
                    appendLine("${record.time} · ${observation.phase} · 상태 ${observation.status}")
                }
            }
            appendLine("\n표시되지 않은 시점은 코드 없음으로 간주하지 않습니다. 삭제 후 실제 재발 여부는 해당 ECU의 완전한 재조회와 정비 결과로 확인하세요.")
        }
    }
}
