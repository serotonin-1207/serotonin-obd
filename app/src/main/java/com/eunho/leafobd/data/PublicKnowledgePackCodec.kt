package com.eunho.leafobd.data

import org.json.JSONArray
import org.json.JSONObject

/** 앱에 포함할 공개 지식 팩만 해석한다. 임의의 차량 명령이나 원문 파일은 이 형식에 넣을 수 없다. */
object PublicKnowledgePackCodec {
    const val ASSET_NAME = "diagnostic_knowledge_pack.json"
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ITEMS = 20_000

    fun decode(bytes: ByteArray): PublicKnowledgePack {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "데이터 팩 크기가 허용 범위를 벗어났습니다." }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == 1) { "지원하지 않는 데이터 팩 형식입니다." }
        val sources = root.getJSONArray("sources").mapObjects(::source)
        val entries = root.getJSONArray("entries").mapObjects(::entry)
        require(sources.size <= MAX_ITEMS && entries.size <= MAX_ITEMS) { "데이터 팩 항목이 너무 많습니다." }
        val pack = PublicKnowledgePack(
            schemaVersion = 1,
            version = root.required("version", 40),
            publishedAt = root.required("publishedAt", 10),
            license = root.required("license", 200),
            sources = sources,
            entries = entries
        )
        require(Regex("\\d{4}\\.\\d{2}\\.\\d{2}[-a-z0-9.]*").matches(pack.version)) { "데이터 팩 버전 형식이 잘못되었습니다." }
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(pack.publishedAt)) { "데이터 팩 발행일 형식이 잘못되었습니다." }
        require(KnowledgeCatalog.audit(pack.entries, pack.sources).issues.isEmpty()) {
            KnowledgeCatalog.audit(pack.entries, pack.sources).issues.joinToString()
        }
        return pack
    }

    private fun source(value: JSONObject) = KnowledgeSource(
        id = value.required("id", 100),
        publisher = value.required("publisher", 200),
        title = value.required("title", 300),
        url = value.required("url", 1_000).also { require(it.startsWith("https://")) },
        grade = enumValueOf(value.required("grade", 50)),
        right = enumValueOf(value.required("right", 80)),
        coverage = value.required("coverage", 500),
        reviewedAt = value.required("reviewedAt", 10)
    )

    private fun entry(value: JSONObject): KnowledgeEntry {
        val applicability = value.getJSONObject("applicability")
        return KnowledgeEntry(
            code = value.required("code", 8),
            title = value.required("title", 200),
            explanation = value.required("explanation", 2_000),
            evidence = value.required("evidence", 2_000),
            sourceUrl = value.optString("sourceUrl"),
            sourceScope = value.required("sourceScope", 1_000),
            reviewedAt = value.required("reviewedAt", 10),
            verification = value.required("verification", 1_000),
            applicability = KnowledgeApplicability(
                manufacturer = applicability.required("manufacturer", 100),
                model = applicability.required("model", 200),
                region = applicability.required("region", 100),
                modelYears = applicability.required("modelYears", 100),
                powertrain = applicability.required("powertrain", 200),
                domesticVerified = applicability.getBoolean("domesticVerified")
            ),
            redistribution = value.required("redistribution", 500),
            sourceLabel = value.required("sourceLabel", 100),
            nextChecks = value.getJSONArray("nextChecks").mapStrings(),
            limitations = value.optString("limitations"),
            sourceId = value.optString("sourceId").ifBlank { null },
            verificationGrade = enumValueOf(value.required("verificationGrade", 50)),
            codeScope = enumValueOf(value.required("codeScope", 50)),
            urgency = enumValueOf(value.optString("urgency").ifBlank { "CONTEXT_REQUIRED" }),
            drivingAdvice = value.optString("drivingAdvice").ifBlank {
                "경고등과 차량 증상, 동반 코드를 확인하고 차량별 정비 절차에 따라 판단하세요."
            }.also { require(it.length <= 1_000) { "운행 안내가 너무 깁니다." } },
            ownerChecks = value.optionalStrings("ownerChecks"),
            technicianHandoff = value.optionalStrings("technicianHandoff"),
            beforeClear = value.optionalStrings("beforeClear").ifEmpty { listOf("코드와 ECU", "발생 상태", "프리즈 프레임") }
        ).also { require(DiagnosticKnowledge.validCode(it.code)) { "올바르지 않은 오류코드입니다: ${it.code}" } }
    }

    private fun JSONObject.required(name: String, maxLength: Int): String =
        getString(name).trim().also { require(it.isNotEmpty() && it.length <= maxLength) { "$name 값이 없거나 너무 깁니다." } }

    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
        List(length()) { index -> block(getJSONObject(index)) }

    private fun JSONArray.mapStrings(): List<String> = List(length()) { index ->
        getString(index).trim().also { require(it.isNotEmpty() && it.length <= 500) { "확인 순서가 없거나 너무 깁니다." } }
    }

    private fun JSONObject.optionalStrings(name: String): List<String> =
        optJSONArray(name)?.mapStrings().orEmpty()
}
