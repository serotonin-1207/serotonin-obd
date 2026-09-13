package com.eunho.leafobd.data

import com.eunho.leafobd.ev.EvProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticDataHierarchyTest {
    private fun vehicle(maker: String, model: String, year: Int = 2020, ev: EvProfile? = null) = VehicleProfile(
        alias = "시험", manufacturer = maker, model = model, modelYear = year,
        powertrain = if (ev == null) VehiclePowertrain.GASOLINE else VehiclePowertrain.ELECTRIC,
        evProfile = ev, market = VehicleMarket.KOREA
    )

    @Test fun `모든 차량은 공통 계층을 가지며 알려진 제조사만 그룹 계층을 가진다`() {
        val toyota = DiagnosticDataHierarchy.assess(vehicle("토요타", "캠리"))
        assertEquals(DiagnosticDataLayer.STANDARD, toyota.first().layer)
        assertTrue(toyota.any { it.id == "maker-toyota" })
        assertTrue(toyota.any { it.id == "kr-toyota-camry" })
        assertFalse(DiagnosticDataHierarchy.assess(vehicle("기타 제작사", "시험차")).any { it.layer == DiagnosticDataLayer.MANUFACTURER_GROUP })
    }

    @Test fun `E-GMP는 확인된 모델만 플랫폼 후보로 묶는다`() {
        assertTrue(DiagnosticDataHierarchy.assess(vehicle("현대", "아이오닉 5")).any { it.id == "hmg-egmp" })
        assertTrue(DiagnosticDataHierarchy.assess(vehicle("기아", "EV6")).any { it.id == "hmg-egmp" })
        assertFalse(DiagnosticDataHierarchy.assess(vehicle("현대", "코나 EV")).any { it.id == "hmg-egmp" })
    }

    @Test fun `리프 세대를 구분하고 사용자 차량만 정확 적용 계층을 만든다`() {
        val exact = DiagnosticDataHierarchy.assess(vehicle("닛산", "리프 ZE1", 2019, EvProfile.LEAF_ZE1))
        assertTrue(exact.any { it.id == "nissan-leaf-ze1" })
        assertTrue(exact.any { it.layer == DiagnosticDataLayer.EXACT_APPLICATION })
        val unknown = DiagnosticDataHierarchy.assess(vehicle("닛산", "리프", 2020, EvProfile.LEAF_ZE1))
        assertTrue(unknown.any { it.id == "nissan-leaf-generation-unknown" })
        assertFalse(unknown.any { it.layer == DiagnosticDataLayer.EXACT_APPLICATION })
    }

    @Test fun `니로 DE 계열과 정확한 2019 EV 적용 범위를 분리한다`() {
        val exact = DiagnosticDataHierarchy.assess(vehicle("기아", "니로 EV DE", 2019, EvProfile.NIRO_DE))
        assertTrue(exact.any { it.id == "kia-niro-de" && it.layer == DiagnosticDataLayer.PLATFORM_OR_SYSTEM })
        assertTrue(exact.any { it.id == "kia-niro-ev-de-2019" && it.layer == DiagnosticDataLayer.EXACT_APPLICATION })
        val otherYear = DiagnosticDataHierarchy.assess(vehicle("기아", "니로 EV DE", 2021, EvProfile.NIRO_DE))
        assertTrue(otherYear.any { it.id == "kia-niro-de" })
        assertFalse(otherYear.any { it.layer == DiagnosticDataLayer.EXACT_APPLICATION })
    }
}
