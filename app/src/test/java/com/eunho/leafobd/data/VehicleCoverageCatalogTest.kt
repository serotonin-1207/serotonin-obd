package com.eunho.leafobd.data

import com.eunho.leafobd.ev.EvProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleCoverageCatalogTest {
    private fun vehicle(manufacturer: String, model: String, year: Int, powertrain: VehiclePowertrain, ev: EvProfile? = null,
        market: VehicleMarket = VehicleMarket.UNKNOWN, engine: String = "") = VehicleProfile(alias = "시험", manufacturer = manufacturer,
        model = model, modelYear = year, powertrain = powertrain, evProfile = ev, market = market, engine = engine)

    @Test fun `사용자 확인 2019 리프와 니로는 전용 항목에 일치한다`() {
        assertEquals("nissan-leaf-ze1-2019", VehicleCoverageCatalog.match(vehicle("닛산", "리프 ZE1", 2019, VehiclePowertrain.ELECTRIC, EvProfile.LEAF_ZE1)).id)
        assertEquals("kia-niro-ev-de-2019", VehicleCoverageCatalog.match(vehicle("기아", "니로 EV DE", 2019, VehiclePowertrain.ELECTRIC, EvProfile.NIRO_DE)).id)
    }

    @Test fun `다른 연식 리프를 2019 검증 대상으로 확대하지 않는다`() {
        assertEquals("generic-electric", VehicleCoverageCatalog.match(vehicle("Nissan", "Leaf ZE1", 2021, VehiclePowertrain.ELECTRIC, EvProfile.LEAF_ZE1)).id)
    }

    @Test fun `현대 기아 내연기관과 다른 제조사를 분리한다`() {
        assertEquals("hyundai-kia-combustion", VehicleCoverageCatalog.match(vehicle("현대", "아반떼", 2020, VehiclePowertrain.GASOLINE)).id)
        assertEquals("hyundai-kia-combustion", VehicleCoverageCatalog.match(vehicle("KIA", "쏘렌토", 2020, VehiclePowertrain.DIESEL)).id)
        assertEquals("generic-combustion", VehicleCoverageCatalog.match(vehicle("Toyota", "Corolla", 2020, VehiclePowertrain.HYBRID)).id)
    }

    @Test fun `프로필 이름만 맞고 배터리 규격이 다르면 전용 항목이 아니다`() {
        assertEquals("generic-electric", VehicleCoverageCatalog.match(vehicle("닛산", "리프 ZE1", 2019, VehiclePowertrain.ELECTRIC, null)).id)
    }

    @Test fun `북미 공식 문서 네 항목은 지역 연식 세대 사양이 모두 맞아야 한다`() {
        assertEquals("hyundai-sonata-hybrid-yf-na-2011-2015", VehicleCoverageCatalog.match(vehicle("현대", "Sonata Hybrid YF", 2013, VehiclePowertrain.HYBRID, market = VehicleMarket.NORTH_AMERICA)).id)
        assertEquals("kia-soul-ps-na-2015-1.6", VehicleCoverageCatalog.match(vehicle("기아", "Soul PS", 2015, VehiclePowertrain.GASOLINE, market = VehicleMarket.NORTH_AMERICA, engine = "1.6L GDI")).id)
        assertEquals("kia-rio-jb-na-2008-1.6", VehicleCoverageCatalog.match(vehicle("KIA", "Rio5 JB", 2008, VehiclePowertrain.GASOLINE, market = VehicleMarket.NORTH_AMERICA, engine = "1.6 L")).id)
        assertEquals("hyundai-ioniq-hybrid-na-2017", VehicleCoverageCatalog.match(vehicle("Hyundai", "IONIQ Hybrid", 2017, VehiclePowertrain.HYBRID, market = VehicleMarket.NORTH_AMERICA)).id)
    }

    @Test fun `북미 공지 차량명과 같아도 국내 차량이면 대상군으로만 분류한다`() {
        assertEquals("kr-hyundai-sonata-family", VehicleCoverageCatalog.match(vehicle("현대", "쏘나타 하이브리드 YF", 2013, VehiclePowertrain.HYBRID, market = VehicleMarket.KOREA)).id)
        assertEquals("hyundai-kia-combustion", VehicleCoverageCatalog.match(vehicle("기아", "쏘울 PS", 2015, VehiclePowertrain.GASOLINE, market = VehicleMarket.NORTH_AMERICA, engine = "2.0L")).id)
    }

    @Test fun `국내 주요 차종은 모델 계열별 검증 대상으로 분류한다`() {
        assertEquals("kr-hyundai-avante-family", VehicleCoverageCatalog.match(vehicle("현대", "아반떼 CN7", 2022, VehiclePowertrain.GASOLINE, market = VehicleMarket.KOREA)).id)
        assertEquals("kr-kia-sorento-family", VehicleCoverageCatalog.match(vehicle("기아", "쏘렌토 MQ4", 2022, VehiclePowertrain.DIESEL, market = VehicleMarket.KOREA)).id)
        assertEquals("kr-kia-niro-family", VehicleCoverageCatalog.match(vehicle("기아", "니로 SG2", 2023, VehiclePowertrain.ELECTRIC, market = VehicleMarket.KOREA)).id)
    }

    @Test fun `국내 모델명이라도 판매 지역 모름이면 넓은 제조사 대상군을 사용한다`() {
        assertEquals("hyundai-kia-combustion", VehicleCoverageCatalog.match(vehicle("현대", "아반떼 CN7", 2022, VehiclePowertrain.GASOLINE)).id)
    }

    @Test fun `명시적으로 다른 지역인 리프와 니로를 사용자 확인 차량으로 분류하지 않는다`() {
        assertEquals("generic-electric", VehicleCoverageCatalog.match(vehicle("닛산", "리프 ZE1", 2019, VehiclePowertrain.ELECTRIC, EvProfile.LEAF_ZE1, VehicleMarket.NORTH_AMERICA)).id)
        assertEquals("generic-electric", VehicleCoverageCatalog.match(vehicle("기아", "니로 EV DE", 2019, VehiclePowertrain.ELECTRIC, EvProfile.NIRO_DE, VehicleMarket.OTHER)).id)
    }
}
