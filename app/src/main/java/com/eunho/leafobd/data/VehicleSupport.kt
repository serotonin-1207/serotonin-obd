package com.eunho.leafobd.data

import com.eunho.leafobd.ev.EvProfile

enum class VehicleSupportLevel(val label: String, val rank: Int) {
    SUPPORTED("지원", 4),
    PARTIAL("부분 지원", 3),
    FIELD_TEST_NEEDED("실차 검증 필요", 2),
    NOT_SUPPORTED("미지원", 1)
}

enum class VehicleCapability(val label: String) {
    COMMON_DTC("공통 엔진·배출가스 코드 읽기"),
    LIVE_DATA("범용 실시간 센서값"),
    STANDARD_CLEAR("표준 오류코드 삭제"),
    MANUFACTURER_DTC("제조사 ECU 코드 읽기"),
    MANUFACTURER_CLEAR("제조사 ECU 코드 삭제"),
    EV_BATTERY("EV 배터리 SOC·SOH·셀·온도"),
    ABS_AIRBAG("ABS·에어백 전용 진단"),
    BODY("차체·편의장치 전용 진단"),
    ACTUATOR_CODING("액추에이터 제어·코딩·프로그래밍"),
    LOCAL_REPORT("진단 기록·정비 보고서")
}

data class VehicleSupportItem(
    val capability: VehicleCapability,
    val level: VehicleSupportLevel,
    val explanation: String,
    val evidence: String
)

data class VehicleSupportAssessment(
    val profile: VehicleProfile,
    val coverage: VehicleCoverageEntry,
    val items: List<VehicleSupportItem>
) {
    val supportedCount: Int get() = items.count { it.level == VehicleSupportLevel.SUPPORTED }
    val partialCount: Int get() = items.count { it.level == VehicleSupportLevel.PARTIAL }
    val fieldTestCount: Int get() = items.count { it.level == VehicleSupportLevel.FIELD_TEST_NEEDED }
    val unsupportedCount: Int get() = items.count { it.level == VehicleSupportLevel.NOT_SUPPORTED }
}

/** 현재 앱에 실제로 구현된 기능과 실차 검증 상태만 표시한다. */
object VehicleSupportMatrix {
    fun assess(profile: VehicleProfile): VehicleSupportAssessment {
        val isElectric = profile.powertrain == VehiclePowertrain.ELECTRIC
        val coverage = VehicleCoverageCatalog.match(profile)
        val isLeafZe1 = coverage.id == VehicleCoverageCatalog.leafZe1_2019.id
        val isNiroDe = coverage.id == VehicleCoverageCatalog.niroDe_2019.id
        val standardObdCandidate = profile.powertrain == VehiclePowertrain.GASOLINE ||
            profile.powertrain == VehiclePowertrain.DIESEL ||
            profile.powertrain == VehiclePowertrain.HYBRID

        fun item(capability: VehicleCapability, level: VehicleSupportLevel, explanation: String, evidence: String) =
            VehicleSupportItem(capability, level, explanation, evidence)

        val commonLevel = if (standardObdCandidate) VehicleSupportLevel.PARTIAL else VehicleSupportLevel.FIELD_TEST_NEEDED
        val commonNote = if (!standardObdCandidate) {
            "전기차·기타 동력계의 표준 OBD 응답 범위는 차종마다 다릅니다. 응답 없음은 정상 판정이 아닙니다."
        } else {
            "Mode 03·07·0A 읽기는 구현됐지만 연식·판매 지역·ECU에 따라 응답 범위가 달라 부분 지원으로 표시합니다."
        }
        val manufacturerRead = if (isLeafZe1) VehicleSupportLevel.PARTIAL else VehicleSupportLevel.FIELD_TEST_NEEDED
        val manufacturerReadNote = if (isLeafZe1) {
            "리프 대상 ECU 응답 스캔과 UDS 코드 읽기를 제공하지만 모든 ECU·코드 정의를 보장하지 않습니다."
        } else {
            "범용 UDS 응답 스캔은 있으나 이 차종의 ECU 주소와 코드 정의를 검증하지 않았습니다."
        }
        val manufacturerClear = if (isLeafZe1) VehicleSupportLevel.PARTIAL else VehicleSupportLevel.NOT_SUPPORTED
        val batteryLevel = if (isLeafZe1 || isNiroDe) VehicleSupportLevel.FIELD_TEST_NEEDED else VehicleSupportLevel.NOT_SUPPORTED
        val batteryNote = when {
            isLeafZe1 -> "2019 리프 ZE1 읽기·해석은 구현됐습니다. 기준 진단기와 SOC·SOH·셀 전압·온도 대조가 남았습니다."
            isNiroDe -> "2019 니로 EV DE 읽기·해석은 구현됐습니다. 차량 전체 응답과 기준 진단기 대조가 남았습니다."
            isElectric -> "이 연식·차종에 정확히 일치하는 검증 후보 배터리 프로필이 없습니다."
            else -> "내연기관 차량에는 해당하지 않습니다."
        }

        return VehicleSupportAssessment(profile, coverage, listOf(
            item(VehicleCapability.COMMON_DTC, commonLevel, commonNote, "앱 표준 OBD 진단 흐름·차량 응답 확인"),
            item(VehicleCapability.LIVE_DATA, commonLevel,
                "차량이 지원한다고 보고한 표준 PID만 읽습니다. 제조사 전용 값은 포함하지 않습니다.", "지원 PID 조회 후 값·단위·범위 검사"),
            item(VehicleCapability.STANDARD_CLEAR, commonLevel,
                "Mode 04를 지원하는 ECU에서만 가능하며 프리즈 프레임 저장, 확인 문구, 세션당 1회, 삭제 후 재조회가 필요합니다.", "ClearEligibility 안전 조건"),
            item(VehicleCapability.MANUFACTURER_DTC, manufacturerRead, manufacturerReadNote, "UDS 19 읽기·ECU 응답 스캔 구현 상태"),
            item(VehicleCapability.MANUFACTURER_CLEAR, manufacturerClear,
                if (isLeafZe1) "서비스센터가 지정한 ECU·코드·절차가 있을 때만 조건부로 사용합니다." else "이 차종에서 검증된 ECU 주소와 삭제 절차가 없어 제공하지 않습니다.",
                "UDS 삭제 안전 조건·차종별 검증 기록"),
            item(VehicleCapability.EV_BATTERY, batteryLevel, batteryNote, "OVMS 공개 구현 대조·실차 기준값 대조 상태"),
            item(VehicleCapability.ABS_AIRBAG, VehicleSupportLevel.NOT_SUPPORTED,
                "제조사별 전용 주소·정의·안전 절차가 확보되지 않아 전용 진단을 제공하지 않습니다.", "현재 구현 범위"),
            item(VehicleCapability.BODY, VehicleSupportLevel.NOT_SUPPORTED,
                "차체·편의장치의 제조사 전용 코드 정의와 기능 검증이 완료되지 않았습니다.", "현재 구현 범위"),
            item(VehicleCapability.ACTUATOR_CODING, VehicleSupportLevel.NOT_SUPPORTED,
                "장치 강제 작동, ECU 코딩과 펌웨어 변경 기능은 제공하지 않습니다.", "프로젝트 안전 제한"),
            item(VehicleCapability.LOCAL_REPORT, VehicleSupportLevel.SUPPORTED,
                "코드·상태·프리즈 프레임·배터리 결과와 정비 전후 기록을 기기 안에 저장하고 사용자가 직접 공유할 수 있습니다.", "앱 기록·PDF 생성 기능")
        ))
    }
}
