package com.eunho.leafobd.data

import com.eunho.leafobd.ev.EvProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleSupportMatrixTest {
    private fun profile(powertrain: VehiclePowertrain, ev: EvProfile? = null) = VehicleProfile(
        alias = "시험 차량", manufacturer = if (ev == EvProfile.LEAF_ZE1) "닛산" else "기아",
        model = ev?.label ?: "가솔린 차량", modelYear = 2019, powertrain = powertrain, evProfile = ev
    )

    private fun VehicleSupportAssessment.level(capability: VehicleCapability) = items.single { it.capability == capability }.level

    @Test fun `리프는 제조사 진단 부분 지원이고 배터리는 실차 검증 필요다`() {
        val result = VehicleSupportMatrix.assess(profile(VehiclePowertrain.ELECTRIC, EvProfile.LEAF_ZE1))
        assertEquals(VehicleSupportLevel.PARTIAL, result.level(VehicleCapability.MANUFACTURER_DTC))
        assertEquals(VehicleSupportLevel.PARTIAL, result.level(VehicleCapability.MANUFACTURER_CLEAR))
        assertEquals(VehicleSupportLevel.FIELD_TEST_NEEDED, result.level(VehicleCapability.EV_BATTERY))
        assertEquals(VehicleSupportLevel.NOT_SUPPORTED, result.level(VehicleCapability.ACTUATOR_CODING))
    }

    @Test fun `니로 배터리는 실차 검증 필요이고 제조사 삭제는 미지원이다`() {
        val result = VehicleSupportMatrix.assess(profile(VehiclePowertrain.ELECTRIC, EvProfile.NIRO_DE))
        assertEquals(VehicleSupportLevel.FIELD_TEST_NEEDED, result.level(VehicleCapability.EV_BATTERY))
        assertEquals(VehicleSupportLevel.NOT_SUPPORTED, result.level(VehicleCapability.MANUFACTURER_CLEAR))
    }

    @Test fun `내연기관 공통 코드는 부분 지원이고 배터리와 안전 제어는 미지원이다`() {
        val result = VehicleSupportMatrix.assess(profile(VehiclePowertrain.GASOLINE))
        assertEquals(VehicleSupportLevel.PARTIAL, result.level(VehicleCapability.COMMON_DTC))
        assertEquals(VehicleSupportLevel.NOT_SUPPORTED, result.level(VehicleCapability.EV_BATTERY))
        assertEquals(VehicleSupportLevel.NOT_SUPPORTED, result.level(VehicleCapability.ABS_AIRBAG))
    }

    @Test fun `모든 기능은 중복 없이 한 번씩 평가한다`() {
        val result = VehicleSupportMatrix.assess(profile(VehiclePowertrain.DIESEL))
        assertEquals(VehicleCapability.entries.size, result.items.size)
        assertEquals(VehicleCapability.entries.toSet(), result.items.map { it.capability }.toSet())
    }

    @Test fun `다른 연식 리프를 2019 제조사 및 배터리 지원으로 확대하지 않는다`() {
        val result = VehicleSupportMatrix.assess(profile(VehiclePowertrain.ELECTRIC, EvProfile.LEAF_ZE1).copy(modelYear = 2021))
        assertEquals(VehicleSupportLevel.FIELD_TEST_NEEDED, result.level(VehicleCapability.MANUFACTURER_DTC))
        assertEquals(VehicleSupportLevel.NOT_SUPPORTED, result.level(VehicleCapability.MANUFACTURER_CLEAR))
        assertEquals(VehicleSupportLevel.NOT_SUPPORTED, result.level(VehicleCapability.EV_BATTERY))
    }
}
