package com.eunho.leafobd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomesticSpecificationCatalogTest {
    private fun profile(model: String, year: Int, powertrain: VehiclePowertrain = VehiclePowertrain.HYBRID,
        market: VehicleMarket = VehicleMarket.KOREA, maker: String = "현대", engine: String = "", transmission: String = "") = VehicleProfile(
        alias = "시험", manufacturer = maker, model = model, modelYear = year, powertrain = powertrain, market = market,
        engine = engine, transmission = transmission
    )

    @Test fun `국내 싼타페 TM 하이브리드의 공식 사양을 찾는다`() {
        val match = DomesticSpecificationCatalog.assess(profile("싼타페 TM", 2022)).single()
        assertEquals(DomesticSpecificationMatchState.PROFILE_MATCH, match.state)
        assertEquals("6단 자동변속기", match.specification.transmissionLabel)
    }

    @Test fun `세대 코드가 없으면 정확 일치로 올리지 않는다`() {
        assertEquals(DomesticSpecificationMatchState.CHASSIS_REQUIRED,
            DomesticSpecificationCatalog.assess(profile("싼타페", 2022)).single().state)
    }

    @Test fun `북미 차량과 다른 동력계는 국내 사양에 연결하지 않는다`() {
        assertTrue(DomesticSpecificationCatalog.assess(profile("싼타페 TM", 2022, market = VehicleMarket.NORTH_AMERICA)).isEmpty())
        assertTrue(DomesticSpecificationCatalog.assess(profile("니로 DE", 2022, VehiclePowertrain.ELECTRIC, maker = "기아")).isEmpty())
    }

    @Test fun `국내 사양과 관련 북미 시스템의 관계를 별도로 찾는다`() {
        val matches = DomesticSpecificationCatalog.relatedTo(profile("싼타페 TM", 2021), "hyundai-gamma2t-hev-tm")
        assertEquals("kr-hyundai-santafe-tm-hev-2021-2022", matches.single().specification.id)
    }

    @Test fun `공식 사양 카탈로그를 30개 조합으로 확대했다`() {
        assertEquals(30, DomesticSpecificationCatalog.entries.size)
        assertTrue(DomesticSpecificationCatalog.entries.all { it.source.url.startsWith("https://") })
    }

    @Test fun `복수 엔진 차량은 입력 전 후보를 확정하지 않는다`() {
        val matches = DomesticSpecificationCatalog.assess(profile("그랜저", 2026, VehiclePowertrain.GASOLINE))
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.state == DomesticSpecificationMatchState.SPECIFICATION_REQUIRED })
    }

    @Test fun `배기량 입력으로 그랜저 세부 사양을 좁힌다`() {
        val match = DomesticSpecificationCatalog.assess(profile("그랜저", 2026, VehiclePowertrain.GASOLINE, engine = "가솔린 3.5")).single()
        assertEquals(DomesticSpecificationMatchState.PROFILE_MATCH, match.state)
        assertEquals("kr-hyundai-grandeur-35-2026", match.specification.id)
    }

    @Test fun `배터리 용량과 구동방식으로 EV3 세부 사양을 좁힌다`() {
        val match = DomesticSpecificationCatalog.assess(profile("EV3", 2026, VehiclePowertrain.ELECTRIC,
            maker = "기아", engine = "81.4 kWh 4WD")).single()
        assertEquals(DomesticSpecificationMatchState.PROFILE_MATCH, match.state)
        assertEquals("kr-kia-ev3-long-4wd-2026", match.specification.id)
        assertEquals("81.4 kWh", match.specification.batteryLabel)
    }

    @Test fun `배터리 용량만으로 구동방식을 추정하지 않는다`() {
        val matches = DomesticSpecificationCatalog.assess(profile("EV3", 2026, VehiclePowertrain.ELECTRIC,
            maker = "기아", engine = "81.4 kWh"))
        assertEquals(3, matches.size)
        assertTrue(matches.all { it.state == DomesticSpecificationMatchState.SPECIFICATION_REQUIRED })
    }
}
