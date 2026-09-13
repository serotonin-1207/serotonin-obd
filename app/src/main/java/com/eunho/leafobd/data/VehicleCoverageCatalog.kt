package com.eunho.leafobd.data

import com.eunho.leafobd.ev.EvProfile

enum class CoverageEvidenceState(val label: String) {
    USER_VEHICLE_IDENTIFIED("대상 차량 식별됨"),
    PUBLIC_DOCUMENT_MATCH("공식 공개 문서 적용표 일치"),
    FIELD_TEST_RECRUITING("실차 검증 자료 모집 중"),
    GENERIC_RULE_ONLY("범용 규칙만 적용")
}

data class CoverageSource(val title: String, val url: String)

data class VehicleCoverageEntry(
    val id: String,
    val label: String,
    val evidenceState: CoverageEvidenceState,
    val note: String,
    val sources: List<CoverageSource> = emptyList()
)

/**
 * 지원 완료 차량 목록이 아니라, 어떤 범위의 근거로 지원표를 만들었는지 식별하는 카탈로그다.
 * 사용자가 확인한 차량 외에는 특정 세대·연식을 임의로 확장하지 않는다.
 */
object VehicleCoverageCatalog {
    const val revision = 4

    val leafZe1_2019 = VehicleCoverageEntry(
        "nissan-leaf-ze1-2019", "2019 닛산 리프 ZE1",
        CoverageEvidenceState.USER_VEHICLE_IDENTIFIED,
        "사용자 차량과 리프 후보 프로필은 확인됐으며 배터리 값의 기준 진단기 대조가 남았습니다."
    )
    val niroDe_2019 = VehicleCoverageEntry(
        "kia-niro-ev-de-2019", "2019 기아 니로 EV DE",
        CoverageEvidenceState.USER_VEHICLE_IDENTIFIED,
        "사용자 차량과 니로 후보 프로필은 확인됐으며 전체 응답과 배터리 값 대조가 남았습니다."
    )
    val hyundaiKiaCombustion = VehicleCoverageEntry(
        "hyundai-kia-combustion", "현대·기아 내연기관·하이브리드",
        CoverageEvidenceState.FIELD_TEST_RECRUITING,
        "표준 OBD 기능을 우선 적용합니다. 국내 연식·엔진·변속기별 기준 진단기 대조는 아직 완료되지 않았습니다."
    )
    val sonataHybridYf = VehicleCoverageEntry(
        "hyundai-sonata-hybrid-yf-na-2011-2015", "북미 2011–2015 현대 Sonata Hybrid YF",
        CoverageEvidenceState.PUBLIC_DOCUMENT_MATCH,
        "현대 21-FL-002H의 NVLD 관련 사례 범위입니다. 국내 쏘나타 하이브리드나 다른 고장에 자동 적용하지 않습니다.",
        listOf(CoverageSource("Hyundai 21-FL-002H · NHTSA 공개본", "https://static.nhtsa.gov/odi/tsbs/2021/MC-10199108-0001.pdf"))
    )
    val soulPs2015 = VehicleCoverageEntry(
        "kia-soul-ps-na-2015-1.6", "북미 2015 기아 Soul PS 1.6L",
        CoverageEvidenceState.PUBLIC_DOCUMENT_MATCH,
        "기아 ENG156의 P0128 ECM 로직 사례 범위입니다. 생산기간·ROM ID와 국내 적용 여부는 별도 확인해야 합니다.",
        listOf(CoverageSource("Kia ENG156 · NHTSA 공개본", "https://static.nhtsa.gov/odi/tsbs/2016/SB-10089605-5448.pdf"))
    )
    val rioJb2008 = VehicleCoverageEntry(
        "kia-rio-jb-na-2008-1.6", "북미 2008 기아 Rio/Rio5 JB 1.6L",
        CoverageEvidenceState.PUBLIC_DOCUMENT_MATCH,
        "기아 ENG066의 ECM 로직 사례 범위입니다. 원문 생산기간과 차량 ROM ID를 확인해야 합니다.",
        listOf(CoverageSource("Kia ENG066 · NHTSA 공개본", "https://static.nhtsa.gov/odi/tsbs/2021/MC-10194564-0001.pdf"))
    )
    val ioniqHybrid2017 = VehicleCoverageEntry(
        "hyundai-ioniq-hybrid-na-2017", "북미 2017 현대 IONIQ Hybrid",
        CoverageEvidenceState.PUBLIC_DOCUMENT_MATCH,
        "현대 17-01-057-1 캠페인 사례 범위입니다. 캠페인 대상 VIN 확인이 필요하며 국내 차량에 적용하지 않습니다.",
        listOf(CoverageSource("Hyundai 17-01-057-1 · NHTSA 공개본", "https://static.nhtsa.gov/odi/tsbs/2017/MC-10125162-9999.pdf"))
    )
    val genericCombustion = VehicleCoverageEntry(
        "generic-combustion", "범용 내연기관·하이브리드",
        CoverageEvidenceState.GENERIC_RULE_ONLY,
        "표준 OBD 규칙만 적용하며 제조사 전용 ECU 지원을 뜻하지 않습니다."
    )
    val genericElectric = VehicleCoverageEntry(
        "generic-electric", "범용 전기차",
        CoverageEvidenceState.GENERIC_RULE_ONLY,
        "전기차의 표준 OBD 응답과 제조사 배터리 데이터는 차종별로 따로 검증해야 합니다."
    )
    val genericOther = VehicleCoverageEntry(
        "generic-other", "기타 차량",
        CoverageEvidenceState.GENERIC_RULE_ONLY,
        "현재 등록 정보만으로 적용 가능한 진단 규격을 특정할 수 없습니다."
    )

    private val koreaFamilyEntries = KoreaVehicleFamilies.all.associate { family -> family.id to VehicleCoverageEntry(
        "kr-${family.id}-family", "대한민국 ${family.label} 계열",
        CoverageEvidenceState.FIELD_TEST_RECRUITING,
        "모델 계열만 분류했습니다. 세대 코드·연식·엔진·변속기별 표준 OBD와 제조사 ECU 검증 자료가 필요합니다."
    ) }

    val entries = listOf(leafZe1_2019, niroDe_2019, sonataHybridYf, soulPs2015, rioJb2008, ioniqHybrid2017,
        *koreaFamilyEntries.values.toTypedArray(), hyundaiKiaCombustion, genericCombustion, genericElectric, genericOther)

    fun match(profile: VehicleProfile): VehicleCoverageEntry {
        val maker = profile.manufacturer.trim().lowercase()
        val model = profile.model.trim().lowercase()
        val engine = profile.engine.trim().lowercase()
        val nissan = "닛산" in maker || "nissan" in maker
        val kia = "기아" in maker || maker == "kia"
        val hyundai = "현대" in maker || "hyundai" in maker
        val leaf = ("리프" in model || "leaf" in model) && "ze1" in model
        val niro = ("니로" in model || "niro" in model) && ("de" in model || profile.evProfile == EvProfile.NIRO_DE)
        val combustion = profile.powertrain in setOf(VehiclePowertrain.GASOLINE, VehiclePowertrain.DIESEL, VehiclePowertrain.HYBRID)
        val koreaFamily = KoreaVehicleFamilies.match(profile)
        val userVehicleMarket = profile.market == VehicleMarket.KOREA || profile.market == VehicleMarket.UNKNOWN

        return when {
            nissan && leaf && userVehicleMarket && profile.modelYear == 2019 && profile.evProfile == EvProfile.LEAF_ZE1 -> leafZe1_2019
            kia && niro && userVehicleMarket && profile.modelYear == 2019 && profile.evProfile == EvProfile.NIRO_DE -> niroDe_2019
            hyundai && profile.market == VehicleMarket.NORTH_AMERICA && profile.powertrain == VehiclePowertrain.HYBRID &&
                profile.modelYear in 2011..2015 && ("sonata" in model || "쏘나타" in model) && "yf" in model -> sonataHybridYf
            kia && profile.market == VehicleMarket.NORTH_AMERICA && profile.powertrain == VehiclePowertrain.GASOLINE &&
                profile.modelYear == 2015 && ("soul" in model || "쏘울" in model) && "ps" in model && "1.6" in engine -> soulPs2015
            kia && profile.market == VehicleMarket.NORTH_AMERICA && profile.powertrain == VehiclePowertrain.GASOLINE &&
                profile.modelYear == 2008 && ("rio" in model || "리오" in model) && "jb" in model && "1.6" in engine -> rioJb2008
            hyundai && profile.market == VehicleMarket.NORTH_AMERICA && profile.powertrain == VehiclePowertrain.HYBRID &&
                profile.modelYear == 2017 && ("ioniq" in model || "아이오닉" in model) -> ioniqHybrid2017
            koreaFamily != null -> koreaFamilyEntries.getValue(koreaFamily.id)
            (hyundai || kia) && combustion -> hyundaiKiaCombustion
            combustion -> genericCombustion
            profile.powertrain == VehiclePowertrain.ELECTRIC -> genericElectric
            else -> genericOther
        }
    }
}
