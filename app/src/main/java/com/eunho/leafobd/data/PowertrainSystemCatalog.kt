package com.eunho.leafobd.data

enum class PowertrainSystemType(val label: String) {
    ENGINE("엔진"), TRANSMISSION("변속기"), HYBRID_SYSTEM("하이브리드 시스템"), EV_SYSTEM("전기차 시스템")
}

enum class ApplicationMatchState(val label: String) {
    DOCUMENT_MATCH("공개 문서 조건 일치"),
    SPECIFICATION_REQUIRED("엔진·변속기 정보 필요"),
    REGION_REVIEW_REQUIRED("판매 지역 적용 확인 필요")
}

data class PowertrainDocumentSource(val title: String, val url: String, val reviewedAt: String = "2026-09-13")

data class PowertrainApplication(
    val id: String,
    val manufacturerAliases: Set<String>,
    val modelAliases: Set<String>,
    val chassisAliases: Set<String>,
    val fromYear: Int?,
    val toYear: Int?,
    val market: VehicleMarket,
    val powertrains: Set<VehiclePowertrain>,
    val engineTokens: Set<String> = emptySet(),
    val transmissionTokens: Set<String> = emptySet(),
    val label: String
)

data class PowertrainSystem(
    val id: String,
    val label: String,
    val types: Set<PowertrainSystemType>,
    val applications: List<PowertrainApplication>,
    val source: PowertrainDocumentSource,
    val reuseRule: String
)

data class PowertrainApplicationMatch(
    val system: PowertrainSystem,
    val application: PowertrainApplication,
    val state: ApplicationMatchState,
    val missingInformation: List<String>
)

/**
 * 공개 제조사 문서의 적용표를 구조화한 초기 카탈로그다.
 * 북미 문서의 모델명·연식·시스템 관계를 국내 차량에 자동 확대하지 않는다.
 */
object PowertrainSystemCatalog {
    const val revision = 1

    private fun source(number: String, subject: String, url: String) =
        PowertrainDocumentSource("$number · $subject", url)

    val systems = listOf(
        PowertrainSystem(
            "hyundai-dry-7dct-2016", "현대 7단 건식 DCT 적용군", setOf(PowertrainSystemType.TRANSMISSION),
            listOf(
                app("hyundai-elantra-ad-7dct", "현대", setOf("아반떼", "elantra"), setOf("ad"), null, null, VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), transmission = setOf("7dct", "7단dct"), label = "Elantra Eco AD"),
                app("hyundai-sonata-lf-7dct", "현대", setOf("쏘나타", "sonata"), setOf("lf"), null, null, VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), transmission = setOf("7dct", "7단dct"), label = "Sonata Eco LF"),
                app("hyundai-tucson-tl-7dct", "현대", setOf("투싼", "tucson"), setOf("tl"), null, null, VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), transmission = setOf("7dct", "7단dct"), label = "Tucson TL"),
                app("hyundai-veloster-fs-7dct", "현대", setOf("벨로스터", "veloster"), setOf("fs"), null, null, VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), transmission = setOf("7dct", "7단dct"), label = "Veloster Turbo FS")
            ),
            source("Hyundai 16-GI-001", "7-speed dry-type DCT", "https://static.nhtsa.gov/odi/tsbs/2016/SB-10135096-0699.pdf"),
            "7DCT 일반 특성 자료입니다. 개별 DTC 정의나 부품 호환 근거로 사용하지 않습니다."
        ),
        PowertrainSystem(
            "kia-gamma16t-7dct-jfa", "기아 Gamma 1.6 T-GDI · 7DCT", setOf(PowertrainSystemType.ENGINE, PowertrainSystemType.TRANSMISSION),
            listOf(app("kia-optima-jfa-gamma16t-7dct", "기아", setOf("k5", "optima"), setOf("jfa"), 2016, 2020,
                VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), setOf("gamma1.6tgdi", "감마1.6터보", "1.6tgdi"), setOf("7dct", "7단dct"), "Optima JFa 1.6 T-GDI 7DCT")),
            source("Kia TRA098", "7DCT anti-judder logic improvement", "https://static.nhtsa.gov/odi/tsbs/2021/MC-10200552-0001.pdf"),
            "해당 북미 생산기간과 ROM ID 확인이 필요한 소프트웨어 사례입니다. 국내 K5에 자동 적용하지 않습니다."
        ),
        PowertrainSystem(
            "kia-niro-de-atkinson16-6dct", "기아 니로 DE 1.6 Atkinson · 6DCT", setOf(PowertrainSystemType.ENGINE, PowertrainSystemType.TRANSMISSION, PowertrainSystemType.HYBRID_SYSTEM),
            listOf(app("kia-niro-de-atkinson16-6dct", "기아", setOf("니로", "niro"), setOf("de"), 2017, null,
                VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.HYBRID), setOf("1.6atkinson", "1.6앳킨슨"), setOf("6dct", "6단dct"), "Niro DE Hybrid 1.6 Atkinson 6DCT")),
            source("Kia PS481", "DCT characteristics", "https://static.nhtsa.gov/odi/tsbs/2017/MC-10109854-9999.pdf"),
            "니로 EV에는 적용하지 않습니다. 하이브리드 DE의 변속기 특성 분류에만 사용합니다."
        ),
        PowertrainSystem(
            "hyundai-gamma2t-hev-tm", "현대 Gamma II 1.6T 하이브리드", setOf(PowertrainSystemType.ENGINE, PowertrainSystemType.HYBRID_SYSTEM),
            listOf(app("hyundai-santafe-tm-gamma2t-hev", "현대", setOf("싼타페", "santafe"), setOf("tm"), 2021, 2022,
                VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.HYBRID), setOf("gamma2tgdi", "감마ii1.6터보", "1.6tgdi"), label = "Santa Fe TM HEV/PHEV")),
            source("Hyundai 23-FL-001H", "ECM DTC logic update", "https://static.nhtsa.gov/odi/tsbs/2023/MC-10232200-0001.pdf"),
            "P1441/P1A77/P00B7/P2118/P0401/P0236/P0299 사례는 특정 북미 TM HEV/PHEV에만 연결합니다."
        ),
        PowertrainSystem(
            "hyundai-yf-hev-theta24", "현대 YF HEV · Theta II 2.4", setOf(PowertrainSystemType.ENGINE, PowertrainSystemType.HYBRID_SYSTEM),
            listOf(app("hyundai-sonata-yf-hev-theta24", "현대", setOf("쏘나타", "sonata"), setOf("yf"), 2013, 2015,
                VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.HYBRID), setOf("thetaii2.4", "세타ii2.4", "2.4theta"), label = "Sonata Hybrid YF Theta II 2.4")),
            source("Hyundai 23-01-054H", "Safety plug and fuse campaign 994", "https://static.nhtsa.gov/odi/tsbs/2023/MC-10243375-0001.pdf"),
            "생산일과 캠페인 대상 VIN 확인이 필요합니다. 국내 YF HEV의 수리 지침으로 사용하지 않습니다."
        ),
        PowertrainSystem(
            "hyundai-yf-hev-power-relay", "현대 YF HEV 고전압 인터록·전력 릴레이", setOf(PowertrainSystemType.HYBRID_SYSTEM),
            listOf(app("hyundai-sonata-yf-hev-power-relay", "현대", setOf("쏘나타", "sonata"), setOf("yf"), 2011, 2015,
                VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.HYBRID), label = "2011–2015 Sonata Hybrid YF")),
            source("Hyundai 23-HC-001H", "Hybrid power relay DTC", "https://static.nhtsa.gov/odi/tsbs/2023/MC-10238380-0001.pdf"),
            "고전압 인터록과 전력 릴레이 관련 북미 점검 공지입니다. 고전압 부품 직접 점검·분해를 안내하지 않습니다."
        ),
        PowertrainSystem(
            "hyundai-theta25t-8wdct", "현대 2.5T · 8단 습식 DCT", setOf(PowertrainSystemType.ENGINE, PowertrainSystemType.TRANSMISSION),
            listOf(
                app("hyundai-santafe-tma-theta25t-8wdct", "현대", setOf("싼타페", "santafe"), setOf("tma"), 2021, null, VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), setOf("theta2.5t", "세타2.5터보", "2.5t"), setOf("8wdct", "8단습식dct"), "Santa Fe TMa 2.5T"),
                app("hyundai-sonata-dn8a-theta25t-8wdct", "현대", setOf("쏘나타", "sonata"), setOf("dn8a"), 2021, null, VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), setOf("theta2.5t", "세타2.5터보", "2.5t"), setOf("8wdct", "8단습식dct"), "Sonata DN8a N-Line 2.5T"),
                app("hyundai-santacruz-nxt-theta25t-8wdct", "현대", setOf("santacruz", "산타크루즈"), setOf("nxt"), 2022, null, VehicleMarket.NORTH_AMERICA, setOf(VehiclePowertrain.GASOLINE), setOf("theta2.5t", "세타2.5터보", "2.5t"), setOf("8wdct", "8단습식dct"), "Santa Cruz NXT 2.5T")
            ),
            source("Hyundai 23-AT-011H", "8-speed wet DCT replacement instructions", "https://static.nhtsa.gov/odi/tsbs/2023/MC-10241891-0001.pdf"),
            "하드웨어와 TCU 소프트웨어 일치가 필요한 북미 서비스 사례입니다. 앱은 교환·학습 기능을 수행하지 않습니다."
        )
    )

    init {
        require(systems.map { it.id }.distinct().size == systems.size)
        require(systems.flatMap { it.applications }.map { it.id }.distinct().size == systems.sumOf { it.applications.size })
        systems.forEach { system ->
            require(system.source.url.startsWith("https://static.nhtsa.gov/"))
            require(system.applications.isNotEmpty() && system.types.isNotEmpty())
            system.applications.forEach { application ->
                require(application.manufacturerAliases.isNotEmpty() && application.modelAliases.isNotEmpty())
                require(application.fromYear == null || application.toYear == null || application.fromYear <= application.toYear)
            }
        }
    }

    fun assess(profile: VehicleProfile): List<PowertrainApplicationMatch> = systems.flatMap { system ->
        system.applications.mapNotNull { application -> match(profile, system, application) }
    }.sortedWith(compareBy({ it.state.ordinal }, { it.system.label }))

    fun system(id: String): PowertrainSystem? = systems.firstOrNull { it.id == id }

    private fun match(profile: VehicleProfile, system: PowertrainSystem, application: PowertrainApplication): PowertrainApplicationMatch? {
        val maker = normalize(profile.manufacturer)
        val model = normalize(profile.model)
        if (application.manufacturerAliases.none { maker.contains(normalize(it)) }) return null
        if (application.modelAliases.none { model.contains(normalize(it)) }) return null
        if (application.fromYear?.let { profile.modelYear < it } == true || application.toYear?.let { profile.modelYear > it } == true) return null
        if (profile.powertrain !in application.powertrains) return null

        val missing = mutableListOf<String>()
        if (application.chassisAliases.isNotEmpty() && application.chassisAliases.none { model.contains(normalize(it)) }) missing += "세대 코드"
        val engine = normalize(profile.engine)
        if (application.engineTokens.isNotEmpty() && application.engineTokens.none { engine.contains(normalize(it)) }) missing += "엔진 형식"
        val transmission = normalize(profile.transmission)
        if (application.transmissionTokens.isNotEmpty() && application.transmissionTokens.none { transmission.contains(normalize(it)) }) missing += "변속기 형식"
        val regionMismatch = profile.market != application.market
        val state = when {
            regionMismatch -> ApplicationMatchState.REGION_REVIEW_REQUIRED
            missing.isNotEmpty() -> ApplicationMatchState.SPECIFICATION_REQUIRED
            else -> ApplicationMatchState.DOCUMENT_MATCH
        }
        if (regionMismatch) missing += "판매 지역"
        return PowertrainApplicationMatch(system, application, state, missing.distinct())
    }

    private fun app(
        id: String, manufacturer: String, models: Set<String>, chassis: Set<String>, from: Int?, to: Int?, market: VehicleMarket,
        powertrains: Set<VehiclePowertrain>, engine: Set<String> = emptySet(), transmission: Set<String> = emptySet(), label: String
    ) = PowertrainApplication(id, setOf(manufacturer, if (manufacturer == "현대") "hyundai" else "kia"), models, chassis,
        from, to, market, powertrains, engine, transmission, label)

    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
}
