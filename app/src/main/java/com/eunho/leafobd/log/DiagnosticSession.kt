package com.eunho.leafobd.log

import com.eunho.leafobd.obd.FreezeFrame
import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.obd.MonitorStatus
import com.eunho.leafobd.obd.PidValue
import com.eunho.leafobd.obd.UdsDtcReadResult
import com.eunho.leafobd.data.VehicleCoverageCatalog
import com.eunho.leafobd.data.VehicleProfile
import java.time.Instant

data class DiagnosticVehicleSnapshot(
    val profileId: String,
    val coverageId: String,
    val coverageLabel: String,
    val catalogRevision: Int,
    val manufacturer: String,
    val model: String,
    val modelYear: Int,
    val powertrain: String,
    val market: String,
    val engine: String,
    val transmission: String
) {
    val description: String get() = "$manufacturer $model · ${modelYear}년 · $powertrain · $market"

    companion object {
        fun from(profile: VehicleProfile): DiagnosticVehicleSnapshot {
            val coverage = VehicleCoverageCatalog.match(profile)
            return DiagnosticVehicleSnapshot(profile.id, coverage.id, coverage.label, VehicleCoverageCatalog.revision,
                profile.manufacturer, profile.model, profile.modelYear, profile.powertrain.label, profile.market.label,
                profile.engine, profile.transmission)
        }
    }
}

data class DiagnosticCommunicationSnapshot(
    val protocolIdentified: Boolean,
    val standardDataObserved: Boolean,
    val udsRespondingEcuCount: Int?
)

/**
 * 진단 세션 한 건. 파일로 저장하고 다시 읽는 단위다.
 *
 * @param deviceAddressMasked 마스킹된 Bluetooth 주소. 전체 MAC은 저장하지 않는다.
 */
data class DiagnosticSession(
    val id: String,
    val startedAt: Instant,
    val deviceName: String?,
    val deviceAddressMasked: String?,
    val adapterInfo: String?,
    val adapterVoltage: String?,
    /** `ATDP` 로 확인한 연결 프로토콜 이름. */
    val protocol: String? = null,
    /** 프로토콜 탐색 과정. 어떤 프로토콜을 시도해 어떤 결과였는지. */
    val protocolAttempts: String? = null,
    /** 진단 대상 차량 이름. 설정에서 바꿀 수 있다. */
    val vehicle: String = "Nissan Leaf 2019",
    /** 진단 당시 차량 정보. 이후 프로필 변경·삭제와 무관하게 기록 분류를 유지한다. */
    val vehicleSnapshot: DiagnosticVehicleSnapshot? = null,
    /** 실제 관찰 사실만 저장하며 미관찰을 미지원·정상으로 해석하지 않는다. */
    val communicationSnapshot: DiagnosticCommunicationSnapshot? = null,
    /** 차대번호. 저장 시 마스킹된 값이 들어온다. */
    val vin: String? = null,
    /** `0101` — 경고등 점등 여부와 ECU가 보고한 DTC 개수. */
    val monitorStatus: MonitorStatus? = null,
    /** 지원 PID 목록(원시 비트맵 문자열). */
    val supportedPidsHex: String? = null,
    /** 실시간 값(Mode 01). */
    val liveValues: List<PidValue> = emptyList(),
    /**
     * 삭제 **전에** 읽어 둔 프리즈 프레임.
     * Mode 04 를 실행하면 차량에서는 사라지므로, 이 기록이 유일한 사본이 된다.
     */
    val freezeFrame: FreezeFrame? = null,
    /** CAN 헤더 표시(ATH1) 상태에서 수집했는지. */
    val headersOn: Boolean = false,
    /** ECU별 UDS 오류코드 조회 결과. 표준 OBD 로는 읽히지 않는 코드가 여기 담긴다. */
    val udsResults: List<UdsDtcReadResult> = emptyList(),
    val commands: List<CommandLog> = emptyList(),
    val dtcBeforeClear: List<DtcCode> = emptyList(),
    val clearAttempted: Boolean = false,
    val clearResponse: String? = null,
    val dtcAfterClear: List<DtcCode> = emptyList(),
    val clearVerificationComplete: Boolean = false,
    /** 모의 데이터(테스트 모드)로 만들어진 세션이면 true. */
    val simulated: Boolean = false,
    val appVersion: String? = null,
    val androidVersion: String? = null,
    val batteryOnly: Boolean = false,
    val udsClearAttempted: Boolean = false,
    val udsClearResult: com.eunho.leafobd.obd.UdsClearResult? = null
) {
    val totalDtcCount: Int
        get() = dtcBeforeClear.size

    /** 삭제 후에도 남아 있는 코드. 활성 고장일 가능성이 있다. */
    val remainingCodes: List<DtcCode>
        get() {
            if (!clearAttempted) return emptyList()
            val before = dtcBeforeClear.map { com.eunho.leafobd.obd.ClearVerification.identity(it) }.toSet()
            return dtcAfterClear.filter { com.eunho.leafobd.obd.ClearVerification.identity(it) in before }
        }

    /** 삭제로 사라진 코드. */
    val clearedCodes: List<DtcCode>
        get() {
            if (!clearAttempted || !clearVerificationComplete) return emptyList()
            val after = dtcAfterClear.map { com.eunho.leafobd.obd.ClearVerification.identity(it) }.toSet()
            return dtcBeforeClear.filter { com.eunho.leafobd.obd.ClearVerification.identity(it) !in after }
        }

    /** 삭제 후 새로 나타난 코드. */
    val newCodes: List<DtcCode>
        get() {
            if (!clearAttempted) return emptyList()
            val before = dtcBeforeClear.map { com.eunho.leafobd.obd.ClearVerification.identity(it) }.toSet()
            return dtcAfterClear.filter { com.eunho.leafobd.obd.ClearVerification.identity(it) !in before }
        }
}
