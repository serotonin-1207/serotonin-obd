package com.eunho.leafobd.data

import android.content.Context
import java.util.Locale

/** Original Korean summaries only. Linked OEM documents are not bundled or licensed for redistribution. */
data class KnowledgeEntry(
    val code: String,
    val title: String,
    val explanation: String,
    val evidence: String,
    val sourceUrl: String,
    val sourceScope: String,
    val reviewedAt: String = "2026-09-11",
    val verification: String = "공개 문서 대조 · 국내 차량 적용 미검증",
    val applicability: KnowledgeApplicability = KnowledgeApplicability(),
    val redistribution: String = "자체 작성 해설만 배포 · 원문과 그림의 재배포 권한 미확보",
    val sourceLabel: String = "제조사 공개 문서",
    val nextChecks: List<String> = emptyList(),
    val limitations: String = "",
    val sourceId: String? = null,
    val verificationGrade: EvidenceGrade = EvidenceGrade.OEM_DOCUMENT,
    val codeScope: CodeScope = CodeScope.MANUFACTURER_SPECIFIC,
    val urgency: DiagnosticUrgency = DiagnosticUrgency.CONTEXT_REQUIRED,
    val drivingAdvice: String = "경고등과 차량 증상, 동반 코드를 확인하고 차량별 정비 절차에 따라 판단하세요.",
    val ownerChecks: List<String> = emptyList(),
    val technicianHandoff: List<String> = emptyList(),
    val beforeClear: List<String> = listOf("코드와 ECU", "발생 상태", "프리즈 프레임")
)

enum class DiagnosticUrgency(val label: String, val rank: Int) {
    STOP_AND_TOW("운행 중단·견인 검토", 4),
    PROMPT_SERVICE("빠른 전문 점검", 3),
    SERVICE_SOON("조속한 점검 예약", 2),
    CONTEXT_REQUIRED("증상·동반 코드 우선 확인", 1)
}

enum class CodeScope(val label: String) {
    COMMON_OBD("공통 OBD 코드"),
    MANUFACTURER_SPECIFIC("제조사 전용 코드")
}

/** 코드 일반 의미와 특정 정비 공지의 적용 범위는 별도로 관리한다. */
data class KnowledgeApplicability(
    val manufacturer: String = "미확인",
    val model: String = "원문 적용표 확인 필요",
    val region: String = "북미",
    val modelYears: String = "원문 적용표 확인 필요",
    val powertrain: String = "원문 적용표 확인 필요",
    val domesticVerified: Boolean = false
)

object DiagnosticKnowledge {
    const val BUNDLED_REVISION = 7
    @Volatile private var activePack: PublicKnowledgePack? = null
    @Volatile var activeRevision: Int = BUNDLED_REVISION
        private set
    @Volatile var sourceLabel: String = "앱 내장 데이터"
        private set
    @Volatile var loadError: Boolean = false
        private set

    private fun pack(): PublicKnowledgePack = activePack ?: synchronized(this) {
        activePack ?: runCatching {
            val stream = DiagnosticKnowledge::class.java.classLoader
                ?.getResourceAsStream(PublicKnowledgePackCodec.ASSET_NAME)
                ?: error("내장 공개 지식 데이터 팩을 찾지 못했습니다.")
            stream.use { PublicKnowledgePackCodec.decode(it.readBytes()) }
        }.getOrElse {
            loadError = true
            PublicKnowledgePack(1, "0000.00.00-unavailable", "0000-00-00", "미확인", emptyList(), emptyList())
        }.also { activePack = it }
    }

    val VERSION: String get() = pack().version
    val entries: List<KnowledgeEntry> get() = pack().entries
    val sources: List<KnowledgeSource> get() = pack().sources

    /** 내장 팩을 먼저 설치하고, 더 최신인 저장 팩이 서명 검증을 통과하면 그 팩을 사용한다. */
    fun installBundled(context: Context): Boolean = runCatching {
        val bytes = context.assets.open(PublicKnowledgePackCodec.ASSET_NAME).use { input ->
            input.readBytes()
        }
        val bundled = PublicKnowledgePackCodec.decode(bytes)
        activePack = bundled
        activeRevision = BUNDLED_REVISION
        sourceLabel = "앱 내장 데이터"
        KnowledgePackStore(context).load()?.takeIf { it.manifest.revision > BUNDLED_REVISION }?.let(::activateDownloaded)
    }.fold(
        onSuccess = { loadError = false; true },
        onFailure = { loadError = true; false }
    )

    internal fun activateDownloaded(value: VerifiedKnowledgePack) {
        activePack = value.pack
        activeRevision = value.manifest.revision
        sourceLabel = "다운로드 데이터"
        loadError = false
    }

    fun normalize(input: String): String = input.trim().uppercase(Locale.ROOT)
    fun validCode(input: String): Boolean = Regex("[PCBU][0-3][0-9A-F]{3}(-[0-9A-F]{2})?").matches(normalize(input))
    fun search(query: String): List<KnowledgeEntry> {
        val q = normalize(query)
        return entries.filter { q.isEmpty() || it.code.contains(q) ||
            listOf(it.title, it.applicability.manufacturer, it.applicability.model).any { text -> text.contains(query.trim(), ignoreCase = true) } }
    }

    val catalogStatus: CatalogStatus get() = KnowledgeCatalog.audit(entries, sources)
}
