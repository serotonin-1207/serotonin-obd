package com.eunho.leafobd.log

import java.time.LocalDateTime
import java.util.Locale

data class UnknownCodeInput(
    val vehicleLabel: String?,
    val record: CodeHistoryRecord?
)

data class UnknownCodeCandidate(
    val vehicleLabel: String,
    val coverageId: String?,
    val coverageLabel: String?,
    val code: String,
    val ecu: String,
    val sessionCount: Int,
    val firstSeen: LocalDateTime,
    val lastSeen: LocalDateTime,
    val statuses: Set<String>
) {
    val formatLabel: String
        get() = if ('-' in code) "제조사 세부 상태 포함" else "5자리 DTC 형식"
}

data class UnknownCodeFamilySummary(
    val coverageId: String,
    val coverageLabel: String,
    val vehicleCount: Int,
    val recordCount: Int,
    val candidateCount: Int,
    val codeSessionCount: Int,
    val protocolObservedCount: Int,
    val protocolAssessedCount: Int,
    val standardDataObservedCount: Int,
    val standardDataAssessedCount: Int,
    val udsResponseObservedCount: Int,
    val udsResponseAssessedCount: Int
)

data class UnknownCodeQueueResult(
    val candidates: List<UnknownCodeCandidate>,
    val scannedRecords: Int,
    val skippedRecords: Int,
    val unclassifiedRecords: Int,
    val knownCodeObservations: Int,
    val uncataloguedRecords: Int,
    val familySummaries: List<UnknownCodeFamilySummary>
)

/** 기록에 실제로 수신된 코드만 묶는다. 미수신·실패 기록으로 코드가 사라졌다고 판단하지 않는다. */
object UnknownCodeQueue {
    private const val MAX_RECORDS = 500
    private const val MAX_OBSERVATIONS = 20_000

    fun analyze(inputs: List<UnknownCodeInput>, knownCodes: Set<String>): UnknownCodeQueueResult {
        require(inputs.size <= MAX_RECORDS) { "한 번에 분석할 수 있는 기록 수를 초과했습니다." }
        val cleanKnown = knownCodes.map { it.trim().uppercase(Locale.ROOT) }.toSet()
        var skipped = 0
        var unclassified = 0
        var known = 0
        var uncatalogued = 0
        var observationCount = 0
        data class Hit(val recordId: String, val time: LocalDateTime, val status: String)
        data class GroupKey(val vehicle: String, val coverageId: String?, val coverageLabel: String?, val code: String, val ecu: String)
        data class FamilyRecord(val vehicle: String, val record: CodeHistoryRecord)
        val groups = linkedMapOf<GroupKey, MutableList<Hit>>()
        val familyRecords = mutableListOf<FamilyRecord>()

        inputs.forEach { input ->
            val record = input.record
            if (record == null) {
                skipped++
                return@forEach
            }
            val vehicle = input.vehicleLabel?.trim()?.takeIf { it.isNotEmpty() }
            if (vehicle == null) {
                unclassified++
                return@forEach
            }
            if (record.coverageId == null) uncatalogued++
            if (record.coverageId != null && record.coverageLabel != null) familyRecords.add(FamilyRecord(vehicle, record))
            val unique = record.observations.distinctBy { Triple(it.ecu, it.code, it.status) }
            observationCount += unique.size
            require(observationCount <= MAX_OBSERVATIONS) { "분석할 오류코드 수를 초과했습니다." }
            unique.forEach { observation ->
                val code = observation.code.uppercase(Locale.ROOT)
                if (code in cleanKnown) {
                    known++
                } else {
                    groups.getOrPut(GroupKey(vehicle, record.coverageId, record.coverageLabel, code, observation.ecu)) { mutableListOf() }
                        .add(Hit(record.id, record.time, observation.status))
                }
            }
        }

        val candidates = groups.map { (key, hits) ->
            UnknownCodeCandidate(
                vehicleLabel = key.vehicle,
                coverageId = key.coverageId,
                coverageLabel = key.coverageLabel,
                code = key.code,
                ecu = key.ecu,
                sessionCount = hits.map { it.recordId }.distinct().size,
                firstSeen = hits.minOf { it.time },
                lastSeen = hits.maxOf { it.time },
                statuses = hits.map { it.status }.toSortedSet()
            )
        }.sortedWith(compareByDescending<UnknownCodeCandidate> { it.sessionCount }
            .thenBy { it.vehicleLabel }.thenBy { it.code }.thenBy { it.ecu })

        val candidateFamilies = candidates.filter { it.coverageId != null && it.coverageLabel != null }.groupBy { it.coverageId }
        val families = familyRecords.groupBy { it.record.coverageId!! to it.record.coverageLabel!! }
            .map { (key, rawRecords) ->
                val records = rawRecords.distinctBy { it.record.id }
                val familyCandidates = candidateFamilies[key.first].orEmpty()
                UnknownCodeFamilySummary(key.first, key.second,
                    vehicleCount = records.map { it.vehicle }.distinct().size,
                    recordCount = records.size,
                    candidateCount = familyCandidates.size,
                    codeSessionCount = familyCandidates.sumOf { it.sessionCount },
                    protocolObservedCount = records.count { it.record.protocolIdentified == true },
                    protocolAssessedCount = records.count { it.record.protocolIdentified != null },
                    standardDataObservedCount = records.count { it.record.standardDataObserved == true },
                    standardDataAssessedCount = records.count { it.record.standardDataObserved != null },
                    udsResponseObservedCount = records.count { (it.record.udsRespondingEcuCount ?: 0) > 0 },
                    udsResponseAssessedCount = records.count { it.record.udsRespondingEcuCount != null })
            }.sortedWith(compareByDescending<UnknownCodeFamilySummary> { it.recordCount }.thenBy { it.coverageLabel })

        return UnknownCodeQueueResult(candidates, inputs.count { it.record != null }, skipped, unclassified, known, uncatalogued, families)
    }

    /** 차량 별칭과 정확한 시각을 빼고 검증자가 필요한 최소 정보만 만든다. */
    fun export(result: UnknownCodeQueueResult): String {
        val anonymousVehicles = result.candidates.map { it.vehicleLabel }.distinct().sorted()
            .mapIndexed { index, label -> label to "차량 ${index + 1}" }.toMap()
        return buildString {
            appendLine("미해설 오류코드 검증 후보")
            appendLine("등록 해설과 일치하지 않은 수신 기록 · 자동 고장 판정 아님")
            appendLine("분석 ${result.scannedRecords}건 · 미분류 제외 ${result.unclassifiedRecords}건 · 손상/지원불가 제외 ${result.skippedRecords}건")
            if (result.candidates.isEmpty()) appendLine("내보낼 후보 없음")
            result.candidates.forEach { candidate ->
                val vehicle = anonymousVehicles.getValue(candidate.vehicleLabel)
                appendLine()
                appendLine("$vehicle · ${candidate.coverageLabel ?: "기존 기록 · 차량 계열 미저장"}")
                appendLine("${candidate.code} · ECU ${candidate.ecu} · 서로 다른 진단 ${candidate.sessionCount}건")
                appendLine("상태 ${candidate.statuses.joinToString()} · 기간 ${candidate.firstSeen.toLocalDate()}~${candidate.lastSeen.toLocalDate()}")
                appendLine("검증 상태: 정의 확인 전 · 제조사 원문과 차량 적용표 확인 필요")
            }
            appendLine()
            appendLine("차량 별칭·VIN·Bluetooth 주소·원시 응답은 포함하지 않음")
        }
    }
}
