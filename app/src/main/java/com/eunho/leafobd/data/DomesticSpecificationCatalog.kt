package com.eunho.leafobd.data

enum class DomesticSpecificationMatchState(val label: String) {
    PROFILE_MATCH("국내 공식 사양과 프로필 일치"),
    CHASSIS_REQUIRED("세대 코드 확인 필요"),
    SPECIFICATION_REQUIRED("엔진·배터리 세부 사양 확인 필요")
}

data class DomesticSpecificationSource(val title: String, val url: String, val reviewedAt: String = "2026-09-13")

data class DomesticVehicleSpecification(
    val id: String,
    val label: String,
    val manufacturerAliases: Set<String>,
    val modelAliases: Set<String>,
    val chassisAliases: Set<String>,
    val fromYear: Int,
    val toYear: Int,
    val powertrain: VehiclePowertrain,
    val engineLabel: String,
    val transmissionLabel: String,
    val source: DomesticSpecificationSource,
    val batteryLabel: String = "",
    val driveLabel: String = "",
    /** 각 그룹에서 한 표현 이상이 입력되어야 복수 사양 중 하나를 확정할 수 있다. */
    val specificationTokenGroups: List<Set<String>> = emptyList(),
    val relatedForeignSystemIds: Set<String> = emptySet(),
    val comparisonNote: String = "",
    val limitation: String = "사양 확인 자료이며 국내 DTC 정의나 정비 절차의 근거가 아닙니다."
)

data class DomesticSpecificationMatch(
    val specification: DomesticVehicleSpecification,
    val state: DomesticSpecificationMatchState
)

/** 국내 제조사 공개 페이지·카탈로그에서 확인한 사양만 저장한다. */
object DomesticSpecificationCatalog {
    const val revision = 2

    private val hKona = source("현대자동차 · KONA 2026-06 가격표", "https://www.hyundai.com/kr/ko/e/vehicles/the-all-new-kona/price")
    private val hGrandeur = source("현대자동차 · 2026 GRANDEUR 가격표", "https://www.hyundai.com/contents/repn-car/catalog/grandeur-2026-price.pdf")
    private val hTucson = source("현대자동차 · 2026 TUCSON 가격표", "https://www.hyundai.com/contents/repn-car/catalog/tucson-2026-price.pdf")
    private val hTucsonHev = source("현대자동차 · 2026 TUCSON Hybrid 가격표", "https://www.hyundai.com/contents/repn-car/catalog/tucson-hybrid-2026-price.pdf")
    private val hSonata = source("현대자동차 · 2025 SONATA The Edge 가격표", "https://www.hyundai.com/contents/repn-car/catalog/sonata-the-edge-2025-price.pdf")
    private val hAvante = source("현대자동차 · AVANTE 카탈로그", "https://www.hyundai.com/contents/repn-car/catalog/avante-catalog.pdf.pdf")
    private val kK8 = source("기아 · The 2027 K8 제원", "https://www.kia.com/kr/vehicles/k8/specification")
    private val kSportage = source("기아 · The 2027 Sportage 제원", "https://www.kia.com/kr/vehicles/sportage/specification")
    private val kSorento = source("기아 · 2026 Sorento 카탈로그", "https://www.kia.com/content/dam/kwp/kr/ko/vehicles/pdf/catalog/catalog_sorento.pdf")
    private val kCarnival = source("기아 · 2026 Carnival 가격표", "https://kwp1.kia.com/content/dam/kwp/kr/ko/vehicles/pdf/price/price_carnival.pdf")
    private val kMorning = source("기아 · The 2025 Morning 가격", "https://www.kia.com/kr/vehicles/morning/price")
    private val kEv3 = source("기아 · The 2026 EV3 제원", "https://www.kia.com/kr/vehicles/ev3/specification")
    private val kEv6 = source("기아 · The 2027 EV6 제원", "https://www.kia.com/kr/vehicles/ev6/specification")

    val entries = listOf(
        DomesticVehicleSpecification(
            "kr-hyundai-santafe-tm-hev-2021-2022", "대한민국 2021–2022 싼타페 TM 하이브리드",
            makers("현대"), setOf("싼타페", "santafe"), setOf("tm"), 2021, 2022, VehiclePowertrain.HYBRID,
            "Smartstream G1.6 하이브리드", "6단 자동변속기",
            source("현대자동차 · 2021 SANTA FE Hybrid", "https://www.hyundai.com/kr/ko/brand/brandstory/heritage/2021-santafe-hybrid"),
            relatedForeignSystemIds = setOf("hyundai-gamma2t-hev-tm"),
            comparisonNote = "북미 DTC 공지와 TM·하이브리드·1.6 계통이 겹칩니다. 국내 자료는 Smartstream G1.6과 6단 자동변속기로 표시하며 북미 DTC의 국내 적용은 확인되지 않았습니다."
        ),
        DomesticVehicleSpecification(
            "kr-kia-niro-de-hev-2022", "대한민국 2022 니로 DE HEV/PHEV",
            makers("기아"), setOf("니로", "niro"), setOf("de"), 2022, 2022, VehiclePowertrain.HYBRID,
            "1.6 하이브리드", "6단 DCT",
            source("기아 · 니로 HEV & PHEV 2021-11 카탈로그", "https://www.kia.com/content/dam/kwcms/kr/ko/files/GDE/catalog/catalog_niro.pdf"),
            relatedForeignSystemIds = setOf("kia-niro-de-atkinson16-6dct"),
            comparisonNote = "북미 자료와 DE·하이브리드·1.6·6DCT가 겹칩니다. 국내 카탈로그만으로 Atkinson 표기와 북미 진단 자료의 국내 적용은 확정하지 않습니다."
        ),
        DomesticVehicleSpecification(
            "kr-hyundai-santafe-mx5-hev-2024", "대한민국 2024 싼타페 MX5 하이브리드",
            makers("현대"), setOf("싼타페", "santafe"), setOf("mx5"), 2024, 2024, VehiclePowertrain.HYBRID,
            "1.6 터보 하이브리드", "6단 자동변속기",
            source("현대자동차 · 더 올 뉴 싼타페 하이브리드 가격표", "https://www.hyundai.com/contents/repn-car/catalog/the-all-new-santafe-hybrid-price.pdf")
        ),
        spec("kr-kia-niro-hev-2026", "대한민국 2026 니로 하이브리드", "기아", setOf("니로", "niro"), 2026, VehiclePowertrain.HYBRID, "Smartstream G1.6 하이브리드", "2세대 6DCT", source("기아 · 니로 2026-05 카탈로그", "https://kwp1.kia.com/content/dam/kwp/kr/ko/vehicles/pdf/catalog/catalog_niro.pdf")),
        spec("kr-hyundai-avante-hev-2026", "대한민국 2026 아반떼 하이브리드", "현대", setOf("아반떼", "avante", "elantra"), 2026, VehiclePowertrain.HYBRID, "가솔린 1.6 하이브리드", "6단 DCT", source("현대자동차 · AVANTE Hybrid 가격표", "https://www.hyundai.com/kr/ko/e/vehicles/the-new-avante-hybrid/price")),

        spec("kr-hyundai-kona-16t-2026", "대한민국 2026 코나 1.6 터보", "현대", setOf("코나", "kona"), 2026, VehiclePowertrain.GASOLINE, "Smartstream 가솔린 1.6 터보", "Smartstream 8단 자동변속기", hKona),
        spec("kr-hyundai-grandeur-25-2026", "대한민국 2026 그랜저 2.5", "현대", setOf("그랜저", "grandeur", "azera"), 2026, VehiclePowertrain.GASOLINE, "Smartstream 가솔린 2.5", "8단 자동변속기", hGrandeur, tokenGroup("2.5", "2497")),
        spec("kr-hyundai-grandeur-35-2026", "대한민국 2026 그랜저 3.5", "현대", setOf("그랜저", "grandeur", "azera"), 2026, VehiclePowertrain.GASOLINE, "Smartstream 가솔린 3.5", "8단 자동변속기", hGrandeur, tokenGroup("3.5", "3470")),
        spec("kr-hyundai-tucson-16t-2026", "대한민국 2026 투싼 1.6 터보", "현대", setOf("투싼", "tucson"), 2026, VehiclePowertrain.GASOLINE, "Smartstream 가솔린 1.6 터보", "Smartstream 7DCT", hTucson),
        spec("kr-hyundai-tucson-hev-2026", "대한민국 2026 투싼 하이브리드", "현대", setOf("투싼", "tucson"), 2026, VehiclePowertrain.HYBRID, "Smartstream 가솔린 1.6 터보 하이브리드", "6단 자동변속기", hTucsonHev),
        spec("kr-hyundai-sonata-16t-2025", "대한민국 2025 쏘나타 1.6 터보", "현대", setOf("쏘나타", "sonata"), 2025, VehiclePowertrain.GASOLINE, "Smartstream 가솔린 1.6 터보", "8단 자동변속기", hSonata, tokenGroup("1.6", "1598")),
        spec("kr-hyundai-sonata-20-2025", "대한민국 2025 쏘나타 2.0", "현대", setOf("쏘나타", "sonata"), 2025, VehiclePowertrain.GASOLINE, "Smartstream 가솔린 2.0", "6단 자동변속기", hSonata, tokenGroup("2.0", "1999")),
        spec("kr-hyundai-avante-20-2026", "대한민국 2026 아반떼 2.0", "현대", setOf("아반떼", "avante", "elantra"), 2026, VehiclePowertrain.GASOLINE, "Smartstream 가솔린 2.0", "Smartstream IVT", hAvante),

        spec("kr-kia-k8-25-2027", "대한민국 2027 K8 2.5", "기아", setOf("k8"), 2027, VehiclePowertrain.GASOLINE, "2.5 가솔린", "8단 자동변속기", kK8, tokenGroup("2.5", "2497")),
        spec("kr-kia-k8-35-2027", "대한민국 2027 K8 3.5", "기아", setOf("k8"), 2027, VehiclePowertrain.GASOLINE, "3.5 가솔린", "8단 자동변속기", kK8, tokenGroup("3.5", "3470")),
        spec("kr-kia-k8-hev-2027", "대한민국 2027 K8 하이브리드", "기아", setOf("k8"), 2027, VehiclePowertrain.HYBRID, "1.6 터보 하이브리드", "6단 자동변속기", kK8),
        spec("kr-kia-sportage-16t-2027", "대한민국 2027 스포티지 1.6 터보", "기아", setOf("스포티지", "sportage"), 2027, VehiclePowertrain.GASOLINE, "1.6 가솔린 터보", "8단 자동변속기", kSportage),
        spec("kr-kia-sportage-hev-2027", "대한민국 2027 스포티지 하이브리드", "기아", setOf("스포티지", "sportage"), 2027, VehiclePowertrain.HYBRID, "1.6 터보 하이브리드", "6단 자동변속기", kSportage),
        spec("kr-kia-sorento-25t-2026", "대한민국 2026 쏘렌토 2.5 터보", "기아", setOf("쏘렌토", "sorento"), 2026, VehiclePowertrain.GASOLINE, "Smartstream G2.5 터보", "8단 습식 DCT", kSorento),
        spec("kr-kia-sorento-hev-2026", "대한민국 2026 쏘렌토 하이브리드", "기아", setOf("쏘렌토", "sorento"), 2026, VehiclePowertrain.HYBRID, "1.6 터보 하이브리드", "6단 자동변속기", kSorento),
        spec("kr-kia-carnival-35-2026", "대한민국 2026 카니발 3.5", "기아", setOf("카니발", "carnival"), 2026, VehiclePowertrain.GASOLINE, "3.5 가솔린", "8단 자동변속기", kCarnival),
        spec("kr-kia-carnival-hev-2026", "대한민국 2026 카니발 하이브리드", "기아", setOf("카니발", "carnival"), 2026, VehiclePowertrain.HYBRID, "1.6 터보 하이브리드", "Smartstream 6AT", kCarnival),
        spec("kr-kia-morning-10-2025", "대한민국 2025 모닝 1.0", "기아", setOf("모닝", "morning", "picanto"), 2025, VehiclePowertrain.GASOLINE, "Smartstream G1.0", "4단 자동변속기", kMorning),

        evSpec("kr-kia-ev3-standard-2wd-2026", "대한민국 2026 EV3 스탠다드 2WD", "기아", setOf("ev3"), 2026, "150 kW 모터", "58.3 kWh", "2WD", kEv3, tokenGroup("58.3", "스탠다드"), tokenGroup("2wd")),
        evSpec("kr-kia-ev3-long-2wd-2026", "대한민국 2026 EV3 롱레인지 2WD", "기아", setOf("ev3"), 2026, "150 kW 모터", "81.4 kWh", "2WD", kEv3, tokenGroup("81.4", "롱레인지"), tokenGroup("2wd")),
        evSpec("kr-kia-ev3-long-4wd-2026", "대한민국 2026 EV3 롱레인지 4WD", "기아", setOf("ev3"), 2026, "195 kW 모터", "81.4 kWh", "4WD", kEv3, tokenGroup("81.4", "롱레인지"), tokenGroup("4wd", "awd", "사륜")),
        evSpec("kr-kia-ev6-standard-2wd-2027", "대한민국 2027 EV6 스탠다드 2WD", "기아", setOf("ev6"), 2027, "125 kW 모터", "62.9 kWh", "2WD", kEv6, tokenGroup("62.9", "스탠다드"), tokenGroup("2wd")),
        evSpec("kr-kia-ev6-long-2wd-2027", "대한민국 2027 EV6 롱레인지 2WD", "기아", setOf("ev6"), 2027, "168 kW 모터", "84.0 kWh", "2WD", kEv6, tokenGroup("84", "롱레인지"), tokenGroup("2wd")),
        evSpec("kr-kia-ev6-long-4wd-2027", "대한민국 2027 EV6 롱레인지 4WD", "기아", setOf("ev6"), 2027, "239 kW 모터", "84.0 kWh", "4WD", kEv6, tokenGroup("84", "롱레인지"), tokenGroup("4wd", "awd", "사륜")),
        DomesticVehicleSpecification(
            "kr-hyundai-ioniq5n-2026", "대한민국 2026 아이오닉 5 N", makers("현대"),
            setOf("아이오닉5n", "ioniq5n"), emptySet(), 2026, 2026, VehiclePowertrain.ELECTRIC,
            "448 kW 듀얼 모터", "전기 구동",
            source("현대자동차 · 2026 IONIQ 5 N 가격표", "https://www.hyundai.com/contents/repn-car/catalog/ioniq5n-2026-price.pdf"),
            batteryLabel = "84.0 kWh", driveLabel = "AWD"
        )
    )

    init {
        require(entries.size >= 30)
        require(entries.map { it.id }.distinct().size == entries.size)
        entries.forEach { entry ->
            require(entry.fromYear <= entry.toYear)
            require(entry.source.url.startsWith("https://"))
            require(entry.specificationTokenGroups.all { it.isNotEmpty() })
            require(entry.relatedForeignSystemIds.all { PowertrainSystemCatalog.system(it) != null })
        }
    }

    fun assess(profile: VehicleProfile): List<DomesticSpecificationMatch> {
        if (profile.market != VehicleMarket.KOREA) return emptyList()
        val maker = normalize(profile.manufacturer)
        val model = normalize(profile.model)
        val basic = entries.filter { entry ->
            entry.manufacturerAliases.any { maker.contains(normalize(it)) } &&
                entry.modelAliases.any { model.contains(normalize(it)) } &&
                profile.modelYear in entry.fromYear..entry.toYear && profile.powertrain == entry.powertrain
        }
        if (basic.isEmpty()) return emptyList()
        val specificity = basic.maxOf { entry -> entry.modelAliases.filter { model.contains(normalize(it)) }.maxOf { normalize(it).length } }
        val candidates = basic.filter { entry -> entry.modelAliases.any { model.contains(normalize(it)) && normalize(it).length == specificity } }
        val entered = normalize(profile.engine + " " + profile.transmission)
        val narrowed = if (entered.isBlank()) emptyList() else candidates.filter { entry ->
            entry.specificationTokenGroups.isEmpty() || entry.specificationTokenGroups.all { group -> group.any { entered.contains(normalize(it)) } }
        }
        val selected = narrowed.ifEmpty { candidates }
        val needsSpecification = candidates.size > 1 && narrowed.isEmpty()
        return selected.map { entry ->
            val chassisMissing = entry.chassisAliases.isNotEmpty() && entry.chassisAliases.none { model.contains(normalize(it)) }
            val state = when {
                chassisMissing -> DomesticSpecificationMatchState.CHASSIS_REQUIRED
                needsSpecification -> DomesticSpecificationMatchState.SPECIFICATION_REQUIRED
                else -> DomesticSpecificationMatchState.PROFILE_MATCH
            }
            DomesticSpecificationMatch(entry, state)
        }
    }

    fun relatedTo(profile: VehicleProfile?, foreignSystemId: String): List<DomesticSpecificationMatch> =
        profile?.let(::assess).orEmpty().filter { foreignSystemId in it.specification.relatedForeignSystemIds }

    private fun spec(id: String, label: String, maker: String, models: Set<String>, year: Int,
        powertrain: VehiclePowertrain, engine: String, transmission: String, source: DomesticSpecificationSource,
        groups: List<Set<String>> = emptyList()) = DomesticVehicleSpecification(
        id, label, makers(maker), models, emptySet(), year, year, powertrain, engine, transmission, source,
        specificationTokenGroups = groups
    )

    private fun evSpec(id: String, label: String, maker: String, models: Set<String>, year: Int,
        motor: String, battery: String, drive: String, source: DomesticSpecificationSource,
        vararg groups: List<Set<String>>) = DomesticVehicleSpecification(
        id, label, makers(maker), models, emptySet(), year, year, VehiclePowertrain.ELECTRIC,
        motor, "전기 구동", source, batteryLabel = battery, driveLabel = drive,
        specificationTokenGroups = groups.flatMap { it }
    )

    private fun source(title: String, url: String) = DomesticSpecificationSource(title, url)
    private fun makers(maker: String) = setOf(maker, if (maker == "현대") "hyundai" else "kia")
    private fun tokenGroup(vararg alternatives: String): List<Set<String>> = listOf(alternatives.toSet())
    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
}
