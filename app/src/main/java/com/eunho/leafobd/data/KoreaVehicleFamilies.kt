package com.eunho.leafobd.data

data class KoreaVehicleFamily(
    val id: String,
    val label: String,
    val manufacturer: String,
    val modelInput: String,
    val aliases: Set<String>,
    val defaultPowertrain: VehiclePowertrain = VehiclePowertrain.OTHER
)

/** 모델 계열 분류용 목록이다. 세대·연식·진단 지원을 의미하지 않는다. */
object KoreaVehicleFamilies {
    private fun h(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("hyundai-$id", "현대 $name", "현대", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)
    private fun k(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("kia-$id", "기아 $name", "기아", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)
    private fun g(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("genesis-$id", "제네시스 $name", "제네시스", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)
    private fun n(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("nissan-$id", "닛산 $name", "닛산", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)
    private fun t(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("toyota-$id", "토요타 $name", "토요타", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)
    private fun c(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("chevrolet-$id", "쉐보레 $name", "쉐보레", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)
    private fun r(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("renault-$id", "르노코리아 $name", "르노코리아", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)
    private fun s(id: String, name: String, vararg aliases: String, powertrain: VehiclePowertrain = VehiclePowertrain.OTHER) =
        KoreaVehicleFamily("kgm-$id", "KGM $name", "KGM", name, (setOf(name) + aliases).map(::normalize).toSet(), powertrain)

    val all = listOf(
        h("casper", "캐스퍼", "casper"), h("accent", "엑센트", "accent"), h("avante", "아반떼", "avante", "elantra"),
        h("i30", "i30"), h("veloster", "벨로스터", "veloster"), h("sonata", "쏘나타", "sonata"),
        h("grandeur", "그랜저", "grandeur", "azera"), h("venue", "베뉴", "venue"), h("kona", "코나", "kona"),
        h("tucson", "투싼", "tucson"), h("santafe", "싼타페", "santafe", "santafe"),
        h("maxcruz", "맥스크루즈", "maxcruz", "grand santa fe"), h("palisade", "팰리세이드", "palisade"),
        h("staria", "스타리아", "staria"), h("ioniq", "아이오닉", "ioniq", powertrain = VehiclePowertrain.HYBRID),
        h("ioniq5", "아이오닉 5", "ioniq5", powertrain = VehiclePowertrain.ELECTRIC),
        h("ioniq6", "아이오닉 6", "ioniq6", powertrain = VehiclePowertrain.ELECTRIC),
        h("ioniq9", "아이오닉 9", "ioniq9", powertrain = VehiclePowertrain.ELECTRIC),
        h("nexo", "넥쏘", "nexo", powertrain = VehiclePowertrain.OTHER),
        k("morning", "모닝", "morning", "picanto"), k("ray", "레이", "ray"), k("pride", "프라이드", "pride", "rio"),
        k("k3", "K3", "forte", "cerato"), k("k5", "K5", "optima"), k("k8", "K8"), k("k9", "K9", "quoris"),
        k("stinger", "스팅어", "stinger"), k("soul", "쏘울", "soul"), k("carens", "카렌스", "carens", "rondo"),
        k("seltos", "셀토스", "seltos"), k("niro", "니로", "niro", powertrain = VehiclePowertrain.HYBRID),
        k("sportage", "스포티지", "sportage"), k("sorento", "쏘렌토", "sorento"), k("mohave", "모하비", "mohave", "borrego"),
        k("carnival", "카니발", "carnival", "sedona"), k("ev3", "EV3", powertrain = VehiclePowertrain.ELECTRIC),
        k("ev4", "EV4", powertrain = VehiclePowertrain.ELECTRIC), k("ev5", "EV5", powertrain = VehiclePowertrain.ELECTRIC),
        k("ev6", "EV6", powertrain = VehiclePowertrain.ELECTRIC), k("ev9", "EV9", powertrain = VehiclePowertrain.ELECTRIC),
        k("bongo", "봉고", "bongo"), k("tasman", "타스만", "tasman", powertrain = VehiclePowertrain.DIESEL),
        g("g70", "G70"), g("g80", "G80"), g("g90", "G90"), g("gv60", "GV60", powertrain = VehiclePowertrain.ELECTRIC),
        g("gv70", "GV70"), g("gv80", "GV80"),
        n("leaf", "리프", "leaf", powertrain = VehiclePowertrain.ELECTRIC), n("ariya", "아리야", "ariya", powertrain = VehiclePowertrain.ELECTRIC),
        n("altima", "알티마", "altima"), n("qashqai", "캐시카이", "qashqai"), n("xtrail", "엑스트레일", "xtrail"), n("juke", "쥬크", "juke"),
        t("prius", "프리우스", "prius", powertrain = VehiclePowertrain.HYBRID), t("camry", "캠리", "camry"),
        t("corolla", "코롤라", "corolla"), t("rav4", "RAV4"), t("sienna", "시에나", "sienna"),
        t("crown", "크라운", "crown", powertrain = VehiclePowertrain.HYBRID), t("bz4x", "bZ4X", powertrain = VehiclePowertrain.ELECTRIC),
        c("spark", "스파크", "spark"), c("cruze", "크루즈", "cruze"), c("malibu", "말리부", "malibu"),
        c("trax", "트랙스", "trax"), c("trailblazer", "트레일블레이저", "trailblazer"), c("equinox", "이쿼녹스", "equinox"),
        c("bolt", "볼트 EV", "boltev", powertrain = VehiclePowertrain.ELECTRIC),
        r("sm3", "SM3"), r("sm5", "SM5"), r("sm6", "SM6"), r("sm7", "SM7"), r("qm3", "QM3"), r("qm5", "QM5"), r("qm6", "QM6"),
        r("xm3", "XM3", "arkana"), r("grand-koleos", "그랑 콜레오스", "grandkoleos"),
        s("tivoli", "티볼리", "tivoli"), s("korando", "코란도", "korando"), s("torres", "토레스", "torres"),
        s("rexton", "렉스턴", "rexton"), s("musso", "무쏘", "musso"), s("actyon", "액티언", "actyon")
    )

    init { require(all.map { it.id }.distinct().size == all.size) }

    val manufacturers: List<String> = all.map { it.manufacturer }.distinct()

    fun match(profile: VehicleProfile): KoreaVehicleFamily? {
        if (profile.market != VehicleMarket.KOREA) return null
        val maker = normalize(profile.manufacturer)
        val model = normalize(profile.model)
        val expectedMaker = when {
            maker.contains("현대") || maker.contains("hyundai") -> "현대"
            maker.contains("기아") || maker == "kia" -> "기아"
            maker.contains("제네시스") || maker.contains("genesis") -> "제네시스"
            maker.contains("닛산") || maker.contains("nissan") -> "닛산"
            maker.contains("토요타") || maker.contains("toyota") -> "토요타"
            maker.contains("쉐보레") || maker.contains("chevrolet") -> "쉐보레"
            maker.contains("르노") || maker.contains("renault") -> "르노코리아"
            maker.contains("kgm") || maker.contains("쌍용") -> "KGM"
            else -> return null
        }
        // '아이오닉'과 '아이오닉 5'처럼 이름이 겹치면 가장 구체적인 별칭을 우선한다.
        return all.asSequence()
            .filter { it.manufacturer == expectedMaker }
            .mapNotNull { family -> family.aliases.filter(model::contains).maxByOrNull(String::length)?.let { family to it.length } }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
}
