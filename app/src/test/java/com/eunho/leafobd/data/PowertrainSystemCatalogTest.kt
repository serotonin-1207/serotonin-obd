package com.eunho.leafobd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowertrainSystemCatalogTest {
    private fun vehicle(model: String, year: Int, powertrain: VehiclePowertrain, market: VehicleMarket,
        engine: String = "", transmission: String = "") = VehicleProfile(
        alias = "시험", manufacturer = "기아", model = model, modelYear = year,
        powertrain = powertrain, market = market, engine = engine, transmission = transmission
    )

    @Test fun `북미 Optima JFa의 엔진과 변속기가 모두 맞으면 문서 일치다`() {
        val matches = PowertrainSystemCatalog.assess(vehicle("Optima JFa", 2019, VehiclePowertrain.GASOLINE,
            VehicleMarket.NORTH_AMERICA, "Gamma 1.6 T-GDI", "7DCT"))
        assertEquals(ApplicationMatchState.DOCUMENT_MATCH, matches.single { it.application.id == "kia-optima-jfa-gamma16t-7dct" }.state)
    }

    @Test fun `차종과 연식만 맞으면 필요한 사양을 명시한다`() {
        val match = PowertrainSystemCatalog.assess(vehicle("Optima JFa", 2019, VehiclePowertrain.GASOLINE,
            VehicleMarket.NORTH_AMERICA)).single { it.application.id == "kia-optima-jfa-gamma16t-7dct" }
        assertEquals(ApplicationMatchState.SPECIFICATION_REQUIRED, match.state)
        assertTrue(match.missingInformation.containsAll(listOf("엔진 형식", "변속기 형식")))
    }

    @Test fun `국내 K5는 북미 적용표를 자동 적용하지 않는다`() {
        val match = PowertrainSystemCatalog.assess(vehicle("K5 JFa", 2019, VehiclePowertrain.GASOLINE,
            VehicleMarket.KOREA, "Gamma 1.6 T-GDI", "7DCT")).single { it.application.id == "kia-optima-jfa-gamma16t-7dct" }
        assertEquals(ApplicationMatchState.REGION_REVIEW_REQUIRED, match.state)
        assertTrue("판매 지역" in match.missingInformation)
    }

    @Test fun `니로 EV를 하이브리드 6DCT 적용표에 연결하지 않는다`() {
        assertTrue(PowertrainSystemCatalog.assess(vehicle("니로 DE", 2019, VehiclePowertrain.ELECTRIC,
            VehicleMarket.NORTH_AMERICA, "전기 모터", "감속기")).none { it.system.id == "kia-niro-de-atkinson16-6dct" })
    }

    @Test fun `시스템과 적용 조건 식별자는 중복되지 않는다`() {
        assertEquals(PowertrainSystemCatalog.systems.size, PowertrainSystemCatalog.systems.map { it.id }.distinct().size)
        val applications = PowertrainSystemCatalog.systems.flatMap { it.applications }
        assertEquals(applications.size, applications.map { it.id }.distinct().size)
    }
}
