package com.eunho.leafobd.data

import android.content.Context
import com.eunho.leafobd.ev.EvProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class VehiclePowertrain(val label: String) { GASOLINE("가솔린"), DIESEL("디젤"), HYBRID("하이브리드"), ELECTRIC("전기차"), OTHER("기타") }
enum class VehicleMarket(val label: String) { KOREA("대한민국"), NORTH_AMERICA("북미"), OTHER("기타 지역"), UNKNOWN("모름") }

data class VehicleProfile(
    val id: String = UUID.randomUUID().toString(),
    val alias: String,
    val manufacturer: String,
    val model: String,
    val modelYear: Int,
    val powertrain: VehiclePowertrain,
    val evProfile: EvProfile? = null,
    val market: VehicleMarket = VehicleMarket.UNKNOWN,
    val engine: String = "",
    val transmission: String = ""
) {
    val description: String get() = "$manufacturer $model · ${modelYear}년 · ${powertrain.label} · ${market.label}"
    val specification: String get() = listOf(engine.takeIf(String::isNotBlank), transmission.takeIf(String::isNotBlank)).filterNotNull().joinToString(" · ").ifBlank { "엔진·변속기 미입력" }
}

object VehicleProfiles {
    fun validate(profile: VehicleProfile): VehicleProfile {
        require(profile.id == UUID.fromString(profile.id).toString())
        fun text(value: String, name: String) = value.trim().also { require(it.length in 1..40 && it.none(Char::isISOControl)) { "$name 항목을 1~40자로 입력하세요." } }
        fun optional(value: String, name: String) = value.trim().also { require(it.length <= 40 && it.none(Char::isISOControl)) { "$name 항목은 40자 이하로 입력하세요." } }
        val clean = profile.copy(alias = text(profile.alias, "별칭"), manufacturer = text(profile.manufacturer, "제조사"), model = text(profile.model, "차종"),
            engine = optional(profile.engine, "엔진"), transmission = optional(profile.transmission, "변속기"))
        require(clean.modelYear in 1980..(java.time.Year.now().value + 1)) { "연식을 확인하세요." }
        require(clean.powertrain == VehiclePowertrain.ELECTRIC || clean.evProfile == null) { "배터리 프로필은 전기차에만 지정할 수 있습니다." }
        return clean
    }

    fun put(existing: List<VehicleProfile>, profile: VehicleProfile): List<VehicleProfile> {
        val clean = validate(profile)
        require(existing.none { it.id != clean.id && it.alias.equals(clean.alias, true) }) { "같은 차량 별칭이 이미 있습니다." }
        return (existing.filterNot { it.id == clean.id } + clean).sortedBy { it.alias }
    }
}

class VehicleProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("vehicle_profiles", Context.MODE_PRIVATE)
    fun load(): List<VehicleProfile> = runCatching {
        val rows = JSONArray(prefs.getString("profiles", "[]"))
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            VehicleProfiles.validate(VehicleProfile(row.getString("id"), row.getString("alias"), row.getString("manufacturer"), row.getString("model"),
                row.getInt("modelYear"), VehiclePowertrain.valueOf(row.getString("powertrain")), row.optString("evProfile").takeIf(String::isNotBlank)?.let(EvProfile::valueOf),
                row.optString("market").takeIf(String::isNotBlank)?.let { runCatching { VehicleMarket.valueOf(it) }.getOrNull() } ?: VehicleMarket.UNKNOWN,
                row.optString("engine"), row.optString("transmission")))
        }.fold(emptyList<VehicleProfile>(), VehicleProfiles::put)
    }.getOrDefault(emptyList())

    fun save(profiles: List<VehicleProfile>) {
        val clean = profiles.fold(emptyList<VehicleProfile>(), VehicleProfiles::put)
        val rows = JSONArray()
        clean.forEach { p -> rows.put(JSONObject().put("id", p.id).put("alias", p.alias).put("manufacturer", p.manufacturer)
            .put("model", p.model).put("modelYear", p.modelYear).put("powertrain", p.powertrain.name).put("evProfile", p.evProfile?.name ?: "")
            .put("market", p.market.name).put("engine", p.engine).put("transmission", p.transmission)) }
        check(prefs.edit().putString("profiles", rows.toString()).commit()) { "차량 프로필을 저장하지 못했습니다." }
    }
}
