package com.eunho.leafobd.data

import java.util.Locale

enum class VehicleDtcMatchState(val label: String) {
    DOCUMENT_CONDITIONS_MATCH("선택 차량과 공개 문서 조건 일치"),
    SPECIFICATION_REQUIRED("선택 차량의 세부 사양 확인 필요"),
    REGION_REVIEW_REQUIRED("판매 지역 적용 확인 필요"),
    SELECTED_VEHICLE_DIFFERS("선택 차량과 문서 적용 조건이 다름"),
    VEHICLE_NOT_SELECTED("차량을 선택하면 적용 조건을 비교할 수 있음")
}

data class VehicleDtcEvidence(
    val code: String,
    val systemId: String,
    val documentContext: String,
    val cautions: String,
    val secondarySources: List<VehicleDtcSecondarySource>,
    val promotionDecision: VehicleDtcPromotionDecision
)

enum class VehicleDtcPromotionDecision(val label: String) {
    GENERIC_EXPLANATION_SUPPORTED("공통 코드 해설 근거 충족"),
    MANUFACTURER_SCOPE_ONLY("제조사·적용 차량 범위 유지"),
    STANDARD_SOURCE_REQUIRED("표준 원문 추가 확인 필요")
}

enum class VehicleDtcEvidenceRelationship(val label: String) {
    SUPERSEDING_EXACT_APPLICATION("동일 적용 차량의 후속 공식 문서"),
    INDEPENDENT_DEFINITION_CONTEXT("별도 공식 문서의 코드 의미 대조"),
    STANDARD_FRAMEWORK("표준 코드 체계 확인 · 세부 표는 계약 필요")
}

data class VehicleDtcSecondarySource(
    val title: String,
    val url: String,
    val relationship: VehicleDtcEvidenceRelationship,
    val note: String,
    val reviewedAt: String = "2026-09-13"
)

enum class VehicleDtcVerificationLevel(val label: String) {
    SUCCESSOR_CONFIRMED("후속 공식 문서에서 동일 적용 범위 재확인"),
    INDEPENDENT_CONTEXT_CONFIRMED("후속 문서와 별도 공식 문서에서 의미 대조"),
    STANDARD_FRAMEWORK_AND_OEM_CONFIRMED("표준 체계와 복수 제조사 문서에서 의미 대조")
}

val VehicleDtcEvidence.verificationLevel: VehicleDtcVerificationLevel
    get() = when {
        secondarySources.any { it.relationship == VehicleDtcEvidenceRelationship.STANDARD_FRAMEWORK } ->
            VehicleDtcVerificationLevel.STANDARD_FRAMEWORK_AND_OEM_CONFIRMED
        secondarySources.any { it.relationship == VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT } ->
            VehicleDtcVerificationLevel.INDEPENDENT_CONTEXT_CONFIRMED
        else -> VehicleDtcVerificationLevel.SUCCESSOR_CONFIRMED
    }

data class VehicleDtcEvidenceMatch(
    val evidence: VehicleDtcEvidence,
    val system: PowertrainSystem,
    val state: VehicleDtcMatchState,
    val matchedApplication: PowertrainApplication? = null,
    val missingInformation: List<String> = emptyList()
)

/** 제조사 공지에 코드가 등장했다는 사실과 적용 조건을 연결한다. 코드의 범용 정의 DB가 아니다. */
object VehicleDtcEvidenceCatalog {
    const val revision = 3

    private val santaFeSuccessor = secondary(
        "Hyundai 23-EE-007H · TM HEV/PHEV DTC logic update",
        "https://static.nhtsa.gov/odi/tsbs/2023/MC-10235557-0001.pdf",
        VehicleDtcEvidenceRelationship.SUPERSEDING_EXACT_APPLICATION,
        "23-FL-001H를 대체한 문서에서 2021–2022 Santa Fe TM HEV/PHEV와 코드 목록을 다시 확인했습니다."
    )
    private val yfSuccessor = secondary(
        "Hyundai 23-HC-001H-1 · YF HEV power relay DTC",
        "https://static.nhtsa.gov/odi/tsbs/2023/MC-10241797-0001.pdf",
        VehicleDtcEvidenceRelationship.SUPERSEDING_EXACT_APPLICATION,
        "23-HC-001H를 대체한 문서에서 2011–2015 Sonata Hybrid YF와 네 코드의 설명·점검 대상을 다시 확인했습니다."
    )
    private val p00b7Independent = secondary(
        "Hyundai 23-01-023H · Sonata Hybrid DN8 P00B7",
        "https://static.nhtsa.gov/odi/tsbs/2023/MC-10233545-0001.pdf",
        VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT,
        "별도 DN8 HEV 공지에서도 P00B7을 엔진 냉각수 흐름 저하/성능 코드로 설명합니다. 이 자료의 수리 절차는 TM에 적용하지 않습니다."
    )
    private val p2118Independent = secondary(
        "Kia FUE 062 Rev.1 · Carnival KA4 P2118",
        "https://static.nhtsa.gov/odi/tsbs/2024/MC-10250785-0001.pdf",
        VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT,
        "별도 Kia KA4 공지에서 P211800을 스로틀 액추에이터 모터 전류 범위/성능 코드로 설명합니다. KA4의 동결 원인과 조치는 TM에 적용하지 않습니다."
    )
    private val p0401Independent = secondary(
        "GM 18-NA-089 · Chevrolet Volt P0401",
        "https://static.nhtsa.gov/odi/tsbs/2018/MC-10137422-9999.pdf",
        VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT,
        "별도 제조사 공지에서 P0401을 EGR 유량 부족 코드로 확인했습니다. GM 차량의 원인과 부품 조치는 현대 차량에 적용하지 않습니다."
    )
    private val boostIndependent = secondary(
        "Volkswagen Jetta/GLI DTC chart · P0236/P0299",
        "https://static.nhtsa.gov/odi/tsbs/2012/SB-10062541-7690.pdf",
        VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT,
        "별도 제조사의 공식 DTC 표에서 P0236의 부스트 센서 범위/성능과 P0299의 언더부스트 의미를 확인했습니다. 진단 임계값은 TM에 적용하지 않습니다."
    )
    private val lfRelayIndependent = secondary(
        "Hyundai 19-HC-001H · LF HEV/PHEV relay DTC",
        "https://static.nhtsa.gov/odi/tsbs/2019/MC-10160095-9999.pdf",
        VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT,
        "별도 LF HEV/PHEV 공지에서 P1B76·P1B77·P0A0D의 코드 설명을 확인했습니다. LF의 수리 절차는 YF에 적용하지 않습니다."
    )
    private val kiaInterlockIndependent = secondary(
        "Kia PS499 · HEV/EV interlock circuit DTC",
        "https://static.nhtsa.gov/odi/tsbs/2017/MC-10126935-9999.pdf",
        VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT,
        "별도 Kia Niro·Optima HEV/PHEV 자료에서 P0A0D가 고전압 커넥터 인터록 회로에 사용됨을 확인했습니다. 커넥터 위치와 절차는 해당 차량에만 적용합니다."
    )
    private val saeJ2012Framework = secondary(
        "SAE J2012_202509 · Diagnostic Trouble Code Definitions",
        "https://saemobilus.sae.org/standards/j2012_202509-diagnostic-trouble-code-definitions",
        VehicleDtcEvidenceRelationship.STANDARD_FRAMEWORK,
        "현재 표준 DTC 체계와 제조사 전용 범위를 확인했습니다. 개별 코드가 담긴 J2012 Digital Annex는 유료 자료이므로 앱에 복제하지 않습니다."
    )

    val entries = listOf(
        evidence("P1441", "hyundai-gamma2t-hev-tm", "EVAP 시스템 비퍼지 상태 유량 관련 진단 로직 개선 대상에 포함됩니다.", VehicleDtcPromotionDecision.MANUFACTURER_SCOPE_ONLY, santaFeSuccessor),
        evidence("P1A77", "hyundai-gamma2t-hev-tm", "HSG 벨트 점검 관련 진단 로직 개선 대상에 포함됩니다.", VehicleDtcPromotionDecision.MANUFACTURER_SCOPE_ONLY, santaFeSuccessor),
        evidence("P00B7", "hyundai-gamma2t-hev-tm", "엔진 냉각수 흐름 저하/성능 및 ITM 제어 관련 진단 로직 개선 대상에 포함됩니다.", VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED, santaFeSuccessor, p00b7Independent),
        evidence("P2118", "hyundai-gamma2t-hev-tm", "스로틀 액추에이터 모터 전류 범위/성능 관련 진단 로직 개선 대상에 포함됩니다.", VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED, santaFeSuccessor, p2118Independent),
        evidence("P0401", "hyundai-gamma2t-hev-tm", "EGR 유량 부족 관련 진단 로직 개선 대상에 포함됩니다.", VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED, santaFeSuccessor, p0401Independent),
        evidence("P0236", "hyundai-gamma2t-hev-tm", "터보차저 부스트 센서 A 회로 범위/성능 관련 진단 로직 개선 대상에 포함됩니다.", VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED, santaFeSuccessor, boostIndependent),
        evidence("P0299", "hyundai-gamma2t-hev-tm", "터보차저 언더부스트 관련 진단 로직 개선 대상에 포함됩니다.", VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED, santaFeSuccessor, boostIndependent),
        evidence("P0A0D", "hyundai-yf-hev-power-relay", "고전압 시스템 인터록 회로 High 코드 목록에 포함됩니다. High는 구동 배터리 과전압이 아니라 인터록 감시 회로 상태를 뜻합니다.", VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED, yfSuccessor, lfRelayIndependent, kiaInterlockIndependent, saeJ2012Framework),
        evidence("P1B25", "hyundai-yf-hev-power-relay", "고전압 경로 고장 코드 목록에 포함됩니다.", VehicleDtcPromotionDecision.MANUFACTURER_SCOPE_ONLY, yfSuccessor),
        evidence("P1B76", "hyundai-yf-hev-power-relay", "고전압 릴레이 고장 코드 목록에 포함됩니다.", VehicleDtcPromotionDecision.MANUFACTURER_SCOPE_ONLY, yfSuccessor, lfRelayIndependent),
        evidence("P1B77", "hyundai-yf-hev-power-relay", "고전압 프리차지 고장 코드 목록에 포함됩니다.", VehicleDtcPromotionDecision.MANUFACTURER_SCOPE_ONLY, yfSuccessor, lfRelayIndependent)
    )

    init {
        require(entries.map { it.code to it.systemId }.distinct().size == entries.size)
        require(entries.all { DiagnosticKnowledge.validCode(it.code) && PowertrainSystemCatalog.system(it.systemId) != null })
        require(entries.all { it.secondarySources.any { source -> source.relationship == VehicleDtcEvidenceRelationship.SUPERSEDING_EXACT_APPLICATION } })
        require(entries.flatMap { it.secondarySources }.all {
            it.url.startsWith("https://static.nhtsa.gov/") || it.url.startsWith("https://saemobilus.sae.org/")
        })
    }

    fun search(code: String, profile: VehicleProfile?): List<VehicleDtcEvidenceMatch> {
        val normalized = code.trim().uppercase(Locale.ROOT)
        if (!DiagnosticKnowledge.validCode(normalized)) return emptyList()
        val profileMatches = profile?.let(PowertrainSystemCatalog::assess).orEmpty().associateBy { it.system.id }
        return entries.filter { it.code == normalized }.map { item ->
            val system = requireNotNull(PowertrainSystemCatalog.system(item.systemId))
            val match = profileMatches[system.id]
            val state = when {
                profile == null -> VehicleDtcMatchState.VEHICLE_NOT_SELECTED
                match == null -> VehicleDtcMatchState.SELECTED_VEHICLE_DIFFERS
                match.state == ApplicationMatchState.DOCUMENT_MATCH -> VehicleDtcMatchState.DOCUMENT_CONDITIONS_MATCH
                match.state == ApplicationMatchState.SPECIFICATION_REQUIRED -> VehicleDtcMatchState.SPECIFICATION_REQUIRED
                else -> VehicleDtcMatchState.REGION_REVIEW_REQUIRED
            }
            VehicleDtcEvidenceMatch(item, system, state, match?.application, match?.missingInformation.orEmpty())
        }
    }

    private fun evidence(code: String, systemId: String, context: String, promotionDecision: VehicleDtcPromotionDecision,
        vararg secondarySources: VehicleDtcSecondarySource) = VehicleDtcEvidence(
        code, systemId, context,
        "공지에 코드가 포함됐다는 사실만으로 부품 고장이나 수리 방법을 확정하지 않습니다. 원문의 적용표·동반 코드·실측값을 함께 확인해야 합니다.",
        secondarySources.toList(), promotionDecision
    )

    private fun secondary(title: String, url: String, relationship: VehicleDtcEvidenceRelationship, note: String) =
        VehicleDtcSecondarySource(title, url, relationship, note)
}
