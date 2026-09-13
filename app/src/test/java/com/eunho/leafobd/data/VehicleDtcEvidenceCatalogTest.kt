package com.eunho.leafobd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDtcEvidenceCatalogTest {
    private fun santaFe(market: VehicleMarket, engine: String = "", chassis: String = "TM") = VehicleProfile(
        alias = "시험", manufacturer = "현대", model = "싼타페 $chassis", modelYear = 2022,
        powertrain = VehiclePowertrain.HYBRID, market = market, engine = engine
    )

    @Test fun `차량 미선택 상태에서도 코드의 문서 존재를 찾는다`() {
        val result = VehicleDtcEvidenceCatalog.search("p1b76", null).single()
        assertEquals(VehicleDtcMatchState.VEHICLE_NOT_SELECTED, result.state)
        assertEquals("hyundai-yf-hev-power-relay", result.system.id)
    }

    @Test fun `북미 TM 사양 일치는 문서 조건 일치로 표시한다`() {
        val result = VehicleDtcEvidenceCatalog.search("P0401", santaFe(VehicleMarket.NORTH_AMERICA, "Gamma II 1.6 T-GDI")).single()
        assertEquals(VehicleDtcMatchState.DOCUMENT_CONDITIONS_MATCH, result.state)
    }

    @Test fun `국내 동일 차종은 판매 지역 검토로 남긴다`() {
        val result = VehicleDtcEvidenceCatalog.search("P0299", santaFe(VehicleMarket.KOREA, "Gamma II 1.6 T-GDI")).single()
        assertEquals(VehicleDtcMatchState.REGION_REVIEW_REQUIRED, result.state)
        assertTrue("판매 지역" in result.missingInformation)
    }

    @Test fun `다른 차량에는 문서 코드를 적용하지 않는다`() {
        val leaf = VehicleProfile(alias = "리프", manufacturer = "닛산", model = "리프 ZE1", modelYear = 2019,
            powertrain = VehiclePowertrain.ELECTRIC, market = VehicleMarket.KOREA)
        assertEquals(VehicleDtcMatchState.SELECTED_VEHICLE_DIFFERS, VehicleDtcEvidenceCatalog.search("P1B25", leaf).single().state)
    }

    @Test fun `형식이 아니거나 등록되지 않은 코드는 근거 결과가 없다`() {
        assertTrue(VehicleDtcEvidenceCatalog.search("P9999", null).isEmpty())
        assertTrue(VehicleDtcEvidenceCatalog.search("고장", null).isEmpty())
    }

    @Test fun `11개 코드 모두 동일 적용 범위의 후속 공식 문서를 가진다`() {
        assertEquals(11, VehicleDtcEvidenceCatalog.entries.size)
        assertTrue(VehicleDtcEvidenceCatalog.entries.all { evidence -> evidence.secondarySources.any {
            it.relationship == VehicleDtcEvidenceRelationship.SUPERSEDING_EXACT_APPLICATION
        } })
    }

    @Test fun `7개 코드는 별도 공식 문서에서 의미를 대조하고 P0A0D는 표준 체계도 확인했다`() {
        assertEquals(7, VehicleDtcEvidenceCatalog.entries.count {
            it.verificationLevel == VehicleDtcVerificationLevel.INDEPENDENT_CONTEXT_CONFIRMED
        })
        assertEquals(1, VehicleDtcEvidenceCatalog.entries.count {
            it.verificationLevel == VehicleDtcVerificationLevel.STANDARD_FRAMEWORK_AND_OEM_CONFIRMED
        })
    }

    @Test fun `P1B25는 후속 개정판 확인을 독립 검증으로 과장하지 않는다`() {
        val evidence = VehicleDtcEvidenceCatalog.search("P1B25", null).single().evidence
        assertEquals(VehicleDtcVerificationLevel.SUCCESSOR_CONFIRMED, evidence.verificationLevel)
        assertEquals(1, evidence.secondarySources.size)
    }

    @Test fun `P00B7은 동일 적용 후속 문서와 별도 차종 문서를 구분한다`() {
        val evidence = VehicleDtcEvidenceCatalog.search("P00B7", null).single().evidence
        assertEquals(2, evidence.secondarySources.size)
        assertTrue(evidence.secondarySources.map { it.relationship }.containsAll(listOf(
            VehicleDtcEvidenceRelationship.SUPERSEDING_EXACT_APPLICATION,
            VehicleDtcEvidenceRelationship.INDEPENDENT_DEFINITION_CONTEXT
        )))
    }

    @Test fun `공통 해설 승격과 제조사 범위 유지 결정을 분리한다`() {
        assertEquals(6, VehicleDtcEvidenceCatalog.entries.count {
            it.promotionDecision == VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED
        })
        assertEquals(5, VehicleDtcEvidenceCatalog.entries.count {
            it.promotionDecision == VehicleDtcPromotionDecision.MANUFACTURER_SCOPE_ONLY
        })
        assertEquals(0, VehicleDtcEvidenceCatalog.entries.count {
            it.promotionDecision == VehicleDtcPromotionDecision.STANDARD_SOURCE_REQUIRED
        })
        val p0a0d = VehicleDtcEvidenceCatalog.search("P0A0D", null).single().evidence
        assertEquals(VehicleDtcPromotionDecision.GENERIC_EXPLANATION_SUPPORTED, p0a0d.promotionDecision)
        assertEquals(4, p0a0d.secondarySources.size)
    }
}
