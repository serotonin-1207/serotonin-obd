package com.eunho.leafobd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreaVehicleFamiliesTest {
    private fun vehicle(maker: String, model: String, market: VehicleMarket = VehicleMarket.KOREA) = VehicleProfile(
        alias = "시험", manufacturer = maker, model = model, modelYear = 2020,
        powertrain = VehiclePowertrain.GASOLINE, market = market
    )

    @Test fun `국내 한글과 해외 모델명을 같은 계열로 찾는다`() {
        assertEquals("hyundai-avante", KoreaVehicleFamilies.match(vehicle("현대", "아반떼 CN7"))?.id)
        assertEquals("hyundai-avante", KoreaVehicleFamilies.match(vehicle("Hyundai", "Elantra CN7"))?.id)
        assertEquals("kia-k5", KoreaVehicleFamilies.match(vehicle("기아", "K5 DL3"))?.id)
        assertEquals("kia-k5", KoreaVehicleFamilies.match(vehicle("Kia", "Optima JF"))?.id)
    }

    @Test fun `국내가 아니거나 제조사가 다르면 계열을 연결하지 않는다`() {
        assertNull(KoreaVehicleFamilies.match(vehicle("현대", "아반떼", VehicleMarket.NORTH_AMERICA)))
        assertNull(KoreaVehicleFamilies.match(vehicle("Toyota", "K5")))
    }

    @Test fun `모든 계열 ID와 화면 이름은 중복되지 않는다`() {
        assertTrue(KoreaVehicleFamilies.all.size >= 75)
        assertEquals(KoreaVehicleFamilies.all.size, KoreaVehicleFamilies.all.map { it.id }.distinct().size)
        assertEquals(KoreaVehicleFamilies.all.size, KoreaVehicleFamilies.all.map { it.label }.distinct().size)
    }

    @Test fun `국내 판매 주요 제조사와 전기차 계열을 분류한다`() {
        assertEquals("genesis-gv60", KoreaVehicleFamilies.match(vehicle("제네시스", "GV60"))?.id)
        assertEquals("nissan-leaf", KoreaVehicleFamilies.match(vehicle("Nissan", "LEAF ZE1"))?.id)
        assertEquals("toyota-prius", KoreaVehicleFamilies.match(vehicle("토요타", "프리우스 5세대"))?.id)
        assertEquals("chevrolet-bolt", KoreaVehicleFamilies.match(vehicle("쉐보레", "볼트 EV"))?.id)
        assertEquals("renault-xm3", KoreaVehicleFamilies.match(vehicle("르노코리아", "XM3 E-TECH"))?.id)
        assertEquals("kgm-torres", KoreaVehicleFamilies.match(vehicle("KGM", "토레스 EVX"))?.id)
    }
}
