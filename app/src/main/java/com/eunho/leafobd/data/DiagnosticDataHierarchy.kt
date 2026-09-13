package com.eunho.leafobd.data

enum class DiagnosticDataLayer(val label: String, val order: Int) {
    STANDARD("국제 표준", 1),
    MANUFACTURER_GROUP("제조사 그룹", 2),
    PLATFORM_OR_SYSTEM("플랫폼·시스템", 3),
    VEHICLE_FAMILY("차종 계열", 4),
    EXACT_APPLICATION("연식·사양 일치", 5)
}

data class DiagnosticDataSource(val title: String, val url: String, val reviewedAt: String)

data class DiagnosticDataMatch(
    val id: String,
    val layer: DiagnosticDataLayer,
    val label: String,
    val status: String,
    val reuseRule: String,
    val source: DiagnosticDataSource? = null
)

/**
 * 오류코드 해설의 재사용 범위를 판정한다. 같은 제조사나 플랫폼이라는 사실만으로
 * 코드 의미·점검 절차를 복사하지 않고, 상위 계층은 검색 후보를 좁히는 데만 쓴다.
 */
object DiagnosticDataHierarchy {
    const val revision = 1

    private val standardSource = DiagnosticDataSource(
        "미국 EPA OBD 시스템 평가 보고서",
        "https://nepis.epa.gov/Exe/ZyPURL.cgi?Dockey=P100KPTW.txt",
        "2026-09-13"
    )
    private val egmpSource = DiagnosticDataSource(
        "Hyundai · IONIQ 5 E-GMP 공개 자료",
        "https://www.hyundai.com/worldwide/en/newsroom/detail/0000000551",
        "2026-09-13"
    )
    private val leafSource = DiagnosticDataSource(
        "Nissan · 리튬이온 배터리 재활용 차형 목록",
        "https://www.nissan-global.com/JP/SUSTAINABILITY/ENVIRONMENT/A_RECYCLE/BATTERY/",
        "2026-09-13"
    )

    fun assess(profile: VehicleProfile): List<DiagnosticDataMatch> {
        val result = mutableListOf(
            DiagnosticDataMatch(
                "standard-obd", DiagnosticDataLayer.STANDARD, "공통 OBD 진단 계층", "해설 데이터 있음",
                "P0 계열 등 표준으로 확인된 코드만 제조사 사이에서 공유합니다. 차량이 지원한다고 응답한 항목만 읽습니다.",
                standardSource
            )
        )
        val maker = normalize(profile.manufacturer)
        manufacturerGroup(maker)?.let { (id, label) ->
            result += DiagnosticDataMatch(
                id, DiagnosticDataLayer.MANUFACTURER_GROUP, label, "분류 인덱스 확보",
                "같은 그룹의 제조사 전용 코드는 검색 후보로만 묶습니다. 코드 뜻과 ECU 주소는 원문 적용표가 일치할 때만 사용합니다."
            )
        }

        val model = normalize(profile.model)
        if ((maker.contains("현대") || maker.contains("hyundai") || maker.contains("기아") || maker == "kia") &&
            listOf("아이오닉5", "ioniq5", "아이오닉6", "ioniq6", "ev6").any(model::contains)) {
            result += DiagnosticDataMatch(
                "hmg-egmp", DiagnosticDataLayer.PLATFORM_OR_SYSTEM, "현대차그룹 E-GMP", "공식 플랫폼 관계 확인",
                "플랫폼 관계는 부품·ECU 조사 범위를 좁히는 근거입니다. 배터리 PID와 고장코드는 차종·연식별 검증 후 사용합니다.",
                egmpSource
            )
        }
        if ((maker.contains("닛산") || maker.contains("nissan")) && (model.contains("리프") || model.contains("leaf"))) {
            val generation = when {
                model.contains("aze0") -> "AZE0"
                model.contains("ze1") -> "ZE1"
                model.contains("ze0") -> "ZE0"
                else -> null
            }
            result += DiagnosticDataMatch(
                "nissan-leaf-${generation?.lowercase() ?: "generation-unknown"}",
                DiagnosticDataLayer.PLATFORM_OR_SYSTEM,
                "닛산 리프 ${generation ?: "세대 미입력"}",
                if (generation == null) "세대 정보 필요" else "공식 차형 구분 확인",
                "ZE0·AZE0·ZE1 자료를 서로 자동 적용하지 않습니다. 차형과 배터리 사양이 일치해야 진단 후보로 사용합니다.",
                leafSource
            )
        }
        if ((maker.contains("기아") || maker == "kia") && (model.contains("니로") || model.contains("niro")) &&
            (model.contains("de") || profile.evProfile == com.eunho.leafobd.ev.EvProfile.NIRO_DE)) {
            result += DiagnosticDataMatch(
                "kia-niro-de", DiagnosticDataLayer.PLATFORM_OR_SYSTEM, "기아 니로 DE 계열", "사용자 대상 차량 계열",
                "DE 계열 안에서도 하이브리드와 전기차, 연식·배터리 사양을 구분합니다. 현재 배터리 진단 후보는 2019 니로 EV DE에만 연결합니다."
            )
        }

        KoreaVehicleFamilies.match(profile)?.let { family ->
            result += DiagnosticDataMatch(
                "kr-${family.id}", DiagnosticDataLayer.VEHICLE_FAMILY, "대한민국 ${family.label} 계열", "차종명 인덱스 확보",
                "차종명이 같아도 세대·연식·엔진·변속기·배터리가 다르면 정비 절차를 공유하지 않습니다."
            )
        }
        val coverage = VehicleCoverageCatalog.match(profile)
        if (coverage.evidenceState in setOf(CoverageEvidenceState.USER_VEHICLE_IDENTIFIED, CoverageEvidenceState.PUBLIC_DOCUMENT_MATCH)) {
            result += DiagnosticDataMatch(
                coverage.id, DiagnosticDataLayer.EXACT_APPLICATION, coverage.label, coverage.evidenceState.label,
                "표시된 지역·연식·세대·동력계 조건 안에서만 연결된 해설을 사용합니다.",
                coverage.sources.firstOrNull()?.let { DiagnosticDataSource(it.title, it.url, "2026-09-13") }
            )
        }
        return result.distinctBy { it.id }.sortedBy { it.layer.order }
    }

    private fun manufacturerGroup(maker: String): Pair<String, String>? = when {
        maker.contains("현대") || maker.contains("hyundai") || maker.contains("기아") || maker == "kia" || maker.contains("제네시스") || maker.contains("genesis") ->
            "maker-hyundai-motor-group" to "현대자동차그룹"
        maker.contains("닛산") || maker.contains("nissan") || maker.contains("인피니티") || maker.contains("infiniti") ->
            "maker-nissan" to "닛산 계열"
        maker.contains("토요타") || maker.contains("toyota") || maker.contains("렉서스") || maker.contains("lexus") ->
            "maker-toyota" to "토요타 계열"
        maker.contains("쉐보레") || maker.contains("chevrolet") || maker == "gm" || maker.contains("캐딜락") || maker.contains("cadillac") ->
            "maker-gm" to "GM 계열"
        maker.contains("르노") || maker.contains("renault") -> "maker-renault" to "르노 계열"
        maker.contains("kgm") || maker.contains("쌍용") -> "maker-kgm" to "KGM 계열"
        maker.contains("폭스바겐") || maker.contains("volkswagen") || maker.contains("아우디") || maker.contains("audi") ->
            "maker-volkswagen-group" to "폭스바겐그룹"
        maker.contains("bmw") || maker.contains("미니") || maker.contains("mini") -> "maker-bmw-group" to "BMW 그룹"
        maker.contains("벤츠") || maker.contains("mercedes") -> "maker-mercedes-benz" to "메르세데스-벤츠"
        else -> null
    }

    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
}
