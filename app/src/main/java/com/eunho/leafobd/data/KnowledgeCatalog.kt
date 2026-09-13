package com.eunho.leafobd.data

/** 진단 해설에 사용한 근거의 성격. 높은 등급도 차량별 적용 검증을 대신하지 않는다. */
enum class EvidenceGrade(val label: String) {
    INDUSTRY_STANDARD("산업 표준"),
    OEM_DOCUMENT("제조사 문서"),
    GOVERNMENT_RECORD("정부 공개 기록"),
    OPEN_SOURCE_OBSERVATION("공개 프로젝트 관찰"),
    FIELD_OBSERVATION("실차 관찰"),
    UNVERIFIED("정의 미검증")
}

/** 현재 프로젝트가 해당 원문에 대해 확보한 이용 범위다. */
enum class ContentRight(val label: String) {
    ORIGINAL_SUMMARY_AND_LINK_ONLY("자체 해설·원문 링크만 가능"),
    OPEN_LICENSE_REVIEW_REQUIRED("오픈 라이선스 검토 후 사용"),
    COMMERCIAL_CONTRACT_REQUIRED("상업용 데이터 계약 필요"),
    UNKNOWN_DO_NOT_BUNDLE("권리 미확인·원문 수록 금지")
}

data class KnowledgeSource(
    val id: String,
    val publisher: String,
    val title: String,
    val url: String,
    val grade: EvidenceGrade,
    val right: ContentRight,
    val coverage: String,
    val reviewedAt: String
)

data class CatalogStatus(
    val entryCount: Int,
    val sourceCount: Int,
    val commonObdEntries: Int,
    val manufacturerSpecificEntries: Int,
    val oemDocumentEntries: Int,
    val pendingDefinitionEntries: Int,
    val issues: List<String>
)

data class PublicKnowledgePack(
    val schemaVersion: Int,
    val version: String,
    val publishedAt: String,
    val license: String,
    val sources: List<KnowledgeSource>,
    val entries: List<KnowledgeEntry>
)

/**
 * 원문을 앱에 복제하지 않고, 출처와 사용 권한만 식별한다.
 * 유료 구독·API 자료는 계약 범위가 확정되기 전 이 목록이나 해설 DB에 넣지 않는다.
 */
object KnowledgeCatalog {
    fun source(id: String?): KnowledgeSource? = DiagnosticKnowledge.sources.firstOrNull { it.id == id }

    fun audit(entries: List<KnowledgeEntry>, sources: List<KnowledgeSource> = DiagnosticKnowledge.sources): CatalogStatus {
        val sourceIds = sources.map { it.id }
        val issues = mutableListOf<String>()
        if (sourceIds.size != sourceIds.distinct().size) issues += "출처 ID가 중복되었습니다."
        val entryKeys = entries.map { listOf(it.code, it.applicability.manufacturer, it.applicability.model,
            it.applicability.region, it.applicability.modelYears, it.applicability.powertrain).joinToString("|") }
        if (entryKeys.size != entryKeys.distinct().size) issues += "같은 차량 적용 조건의 오류코드가 중복되었습니다."
        sources.forEach { source ->
            if (source.id.isBlank()) issues += "빈 출처 ID가 있습니다."
            if (!source.url.startsWith("https://")) issues += "${source.id}: HTTPS 출처가 아닙니다."
            if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(source.reviewedAt)) issues += "${source.id}: 출처 대조일 형식이 잘못되었습니다."
        }

        entries.forEach { entry ->
            val source = sources.firstOrNull { it.id == entry.sourceId }
            if (entry.sourceId != null && source == null) issues += "${entry.code}: 출처 ID를 찾을 수 없습니다."
            if (source != null && entry.sourceUrl != source.url) issues += "${entry.code}: 출처 URL이 원장과 다릅니다."
            if (source != null && entry.verificationGrade != source.grade) issues += "${entry.code}: 해설과 출처의 근거 등급이 다릅니다."
            if (entry.sourceUrl.isNotBlank() && entry.sourceId == null) issues += "${entry.code}: URL에 출처 ID가 없습니다."
            if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(entry.reviewedAt)) issues += "${entry.code}: 대조일 형식이 잘못되었습니다."
            if (entry.verificationGrade == EvidenceGrade.UNVERIFIED && entry.sourceUrl.isNotBlank()) {
                issues += "${entry.code}: 미검증 항목에 원문 URL이 연결되었습니다."
            }
            if (entry.codeScope == CodeScope.COMMON_OBD && entry.code.length != 5) {
                issues += "${entry.code}: failure type이 붙은 코드를 공통 OBD 코드로 표시했습니다."
            }
            if (entry.drivingAdvice.isBlank()) issues += "${entry.code}: 운행 안내가 없습니다."
            if (entry.beforeClear.isEmpty()) issues += "${entry.code}: 삭제 전 보존 항목이 없습니다."
        }

        return CatalogStatus(
            entryCount = entries.size,
            sourceCount = sources.size,
            commonObdEntries = entries.count { it.codeScope == CodeScope.COMMON_OBD },
            manufacturerSpecificEntries = entries.count { it.codeScope == CodeScope.MANUFACTURER_SPECIFIC },
            oemDocumentEntries = entries.count { it.verificationGrade == EvidenceGrade.OEM_DOCUMENT },
            pendingDefinitionEntries = entries.count { it.verificationGrade == EvidenceGrade.UNVERIFIED },
            issues = issues
        )
    }
}
