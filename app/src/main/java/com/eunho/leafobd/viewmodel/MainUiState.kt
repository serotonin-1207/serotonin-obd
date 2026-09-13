package com.eunho.leafobd.viewmodel

import com.eunho.leafobd.bluetooth.BluetoothConnectionState
import com.eunho.leafobd.bluetooth.ObdBluetoothDevice
import com.eunho.leafobd.data.AppSettings
import com.eunho.leafobd.data.UpdateInfo
import com.eunho.leafobd.elm327.FakeScenario
import com.eunho.leafobd.elm327.ProtocolProbeResult
import com.eunho.leafobd.log.SavedSession
import com.eunho.leafobd.obd.CanMonitorResult
import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.obd.EcuScanProgress
import com.eunho.leafobd.obd.EcuScanResult
import com.eunho.leafobd.obd.FreezeFrame
import com.eunho.leafobd.obd.MonitorStatus
import com.eunho.leafobd.obd.ObdMode
import com.eunho.leafobd.obd.PidValue
import com.eunho.leafobd.obd.SupportedPids
import com.eunho.leafobd.obd.DidScanResult
import com.eunho.leafobd.obd.UdsClearResult
import com.eunho.leafobd.obd.UdsDiagnosticsResult

/** 진단 단계 하나의 진행 상태. */
enum class StepStatus { PENDING, RUNNING, SUCCESS, WARNING, FAILED }

/**
 * 진단 화면에 표시할 단계.
 *
 * @param detail 성공/실패 요약 (한국어)
 * @param raw 펼쳐 볼 수 있는 원시 응답
 */
data class DiagnosisStep(
    val order: Int,
    val label: String,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String? = null,
    val raw: String? = null
)

/** 모드별 DTC 조회 결과 요약. */
data class ModeResult(
    val mode: ObdMode,
    val codes: List<DtcCode> = emptyList(),
    val statusLabel: String = "미실행",
    val message: String? = null,
    val raw: String = ""
)

/** 삭제 전후 비교 결과. */
data class ClearComparison(
    val response: String,
    val accepted: Boolean,
    val before: List<DtcCode>,
    val after: List<DtcCode>,
    val cleared: List<DtcCode>,
    val remaining: List<DtcCode>,
    val appeared: List<DtcCode>,
    val verificationComplete: Boolean = false
) {
    /** 재발했거나 새로 나타난 코드가 있으면 활성 고장 가능성이 있다. */
    val hasRecurrence: Boolean get() = remaining.isNotEmpty() || appeared.isNotEmpty()
}

/** 진단 단계 번호. 순서를 바꾸면 [MainViewModel] 의 호출부도 함께 고쳐야 한다. */
object Step {
    const val CONNECT = 1
    const val INIT = 2
    const val ADAPTER_INFO = 3
    const val VOLTAGE = 4
    const val VEHICLE_INFO = 5
    const val FREEZE_FRAME = 6
    const val STORED_DTC = 7
    const val PENDING_DTC = 8
    const val PERMANENT_DTC = 9
    const val ECU_SCAN = 10
    const val UDS_DTC = 11
    const val SAVE_LOG = 12
}

/** 진단 화면의 기본 단계 목록 (지시서 12.3 + 확장 조회). */
fun defaultDiagnosisSteps(): List<DiagnosisStep> = listOf(
    DiagnosisStep(Step.CONNECT, "Bluetooth 연결"),
    DiagnosisStep(Step.INIT, "ELM327 초기화"),
    DiagnosisStep(Step.ADAPTER_INFO, "어댑터 정보 확인 (ATI)"),
    DiagnosisStep(Step.VOLTAGE, "전압 확인 (ATRV)"),
    DiagnosisStep(Step.VEHICLE_INFO, "차량 정보 확인 (0101 · 지원 PID · VIN)"),
    DiagnosisStep(Step.FREEZE_FRAME, "프리즈 프레임 저장 (Mode 02)"),
    DiagnosisStep(Step.STORED_DTC, "저장 DTC 읽기 (Mode 03)"),
    DiagnosisStep(Step.PENDING_DTC, "보류 DTC 읽기 (Mode 07)"),
    DiagnosisStep(Step.PERMANENT_DTC, "영구 DTC 읽기 (Mode 0A)"),
    // 표준 OBD 로 아무것도 나오지 않는 차량(전기차 등)을 위한 단계.
    DiagnosisStep(Step.ECU_SCAN, "ECU 응답 스캔"),
    DiagnosisStep(Step.UDS_DTC, "ECU 오류코드 읽기 (UDS)"),
    DiagnosisStep(Step.SAVE_LOG, "로그 저장")
)

/** 화면 전체 상태. UI는 이 값만 보고 그린다. */
data class MainUiState(
    val vehicleProfiles: List<com.eunho.leafobd.data.VehicleProfile> = emptyList(),
    val batterySnapshot: com.eunho.leafobd.data.BatterySnapshot? = null,
    val batteryRunning: Boolean = false,
    val batteryMessage: String = "",
    val batteryRaw: String = "",
    val batteryProfile: com.eunho.leafobd.ev.EvProfile = com.eunho.leafobd.ev.EvProfile.LEAF_ZE1,
    val batteryRecordProfile: com.eunho.leafobd.ev.EvProfile? = null,
    val batteryRecords: List<com.eunho.leafobd.data.BatteryRecord> = emptyList(),
    val batteryRecordId: String? = null,
    val batterySaving: Boolean = false,
    val batteryHistoryError: String = "",
    val batteryProgress: Float = 0f,
    val batteryStage: String = "",
    val batteryStopRequested: Boolean = false,
    val bluetoothSupported: Boolean = true,
    val permissionGranted: Boolean = false,
    val permissionRequested: Boolean = false,
    val bluetoothEnabled: Boolean = false,

    val pairedDevices: List<ObdBluetoothDevice> = emptyList(),
    val selectedDevice: ObdBluetoothDevice? = null,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Idle,

    val elmInitialized: Boolean = false,
    val adapterInfo: String? = null,
    val adapterVoltage: String? = null,
    val voltageHint: String? = null,
    val protocol: String? = null,

    val diagnosisRunning: Boolean = false,
    val steps: List<DiagnosisStep> = defaultDiagnosisSteps(),
    val modeResults: List<ModeResult> = emptyList(),
    val dtcs: List<DtcCode> = emptyList(),
    val rawLog: String = "",
    val lastDiagnosisAt: String? = null,

    val sessionSaved: Boolean = false,
    val savedSessionName: String? = null,
    val savedSessions: List<SavedSession> = emptyList(),

    val safetyChecks: List<Boolean> = List(ClearConfirmation.CHECKLIST.size) { false },
    val confirmationInput: String = "",
    val clearAttempted: Boolean = false,
    val clearRunning: Boolean = false,
    val clearComparison: ClearComparison? = null,

    /** 프로토콜 탐색 결과. 어느 프로토콜로 통신되었는지, 무엇을 시도했는지 담고 있다. */
    val protocolProbe: ProtocolProbeResult? = null,

    /** CAN 버스 듣기(읽기 전용) 결과. */
    val canMonitor: CanMonitorResult? = null,
    val canMonitorRunning: Boolean = false,

    /** ECU 응답 스캔 결과. */
    val ecuScan: EcuScanResult? = null,
    val ecuScanRunning: Boolean = false,
    val ecuScanProgress: EcuScanProgress? = null,

    /** ECU별 오류코드 조회 결과 (UDS). */
    val udsResult: UdsDiagnosticsResult? = null,
    val udsRunning: Boolean = false,
    /** (현재, 전체, 주소) */
    val udsProgress: Triple<Int, Int, String>? = null,

    /** DID 스캔 결과. */
    val didScan: DidScanResult? = null,
    val didScanRunning: Boolean = false,
    val didProgress: Triple<Int, Int, String>? = null,

    /** ECU 오류코드 삭제 (UDS). */
    val udsClearResult: UdsClearResult? = null,
    val udsClearRunning: Boolean = false,
    val udsClearAttempted: Boolean = false,
    val udsSafetyChecks: List<Boolean> = List(UdsClearConfirmation.CHECKLIST.size) { false },
    val udsConfirmationInput: String = "",
    /** 삭제 대상으로 고른 ECU. 비어 있으면 전체. */
    val udsClearTargets: Set<String> = emptySet(),
    /** 거부한 ECU 에 확장 진단 세션(10 03)을 먼저 요청할지. */
    val udsUseExtendedSession: Boolean = false,

    val monitorStatus: MonitorStatus? = null,
    val supportedPids: SupportedPids? = null,
    val liveValues: List<PidValue> = emptyList(),
    val freezeFrame: FreezeFrame? = null,
    /** 화면 표시용 전체 VIN. 파일에는 마스킹된 값만 저장한다. */
    val vin: String? = null,

    val settings: AppSettings = AppSettings(),

    /** 새 버전이 있으면 그 정보. 없거나 확인 전이면 null. */
    val updateAvailable: UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val knowledgePackVersion: String = "확인 중",
    val knowledgePackRevision: Int = 0,
    val knowledgePackSource: String = "앱 내장 데이터",
    val knowledgePackUpdating: Boolean = false,
    val unknownCodeQueue: com.eunho.leafobd.log.UnknownCodeQueueResult? = null,
    val unknownCodeQueueLoading: Boolean = false,

    val userMessage: String? = null
) {

    val testMode: Boolean get() = settings.testMode
    val fakeScenario: FakeScenario get() = settings.fakeScenario
    val headersOn: Boolean get() = settings.headersOn
    val vehicleName: String get() = settings.vehicleName
    val selectedVehicleProfile: com.eunho.leafobd.data.VehicleProfile?
        get() = vehicleProfiles.firstOrNull { it.id == settings.selectedVehicleProfileId }

    val simulated: Boolean get() = testMode

    val clearEligibility: ClearEligibility
        get() = ClearEligibility(
            connected = connectionState is BluetoothConnectionState.Connected || testMode,
            initialized = elmInitialized,
            dtcRead = modeResults.isNotEmpty(),
            logSaved = sessionSaved,
            safetyChecked = safetyChecks.all { it },
            confirmationTyped = ClearConfirmation.matches(confirmationInput),
            alreadyCleared = clearAttempted
        )

    val clearEligible: Boolean get() = clearEligibility.eligible

    /** ECU 오류코드 삭제(UDS) 사전조건. */
    val udsClearEligibility: UdsClearEligibility
        get() = UdsClearEligibility(
            connected = connectionState is BluetoothConnectionState.Connected || testMode,
            // 이번 스캔이 비어도 지난번에 찾아 둔 주소가 있으면 대상이 있는 것이다.
            ecuFound = ecuScan?.found == true || settings.knownEcuAddresses.isNotEmpty(),
            codesRead = udsResult != null,
            logSaved = sessionSaved,
            safetyChecked = udsSafetyChecks.all { it },
            confirmationTyped = UdsClearConfirmation.matches(udsConfirmationInput),
            alreadyCleared = udsClearAttempted
        )

    /** 읽어 온 코드 중에 고전압 계통으로 보이는 것이 있는지. */
    val hasHighVoltageCodes: Boolean
        get() = udsResult?.allCodes.orEmpty().any { code ->
            // P3xxx 대역은 공개 자료에서 리튬이온 배터리 계통으로 언급된다.
            code.code.startsWith("P3") || code.code.startsWith("P0A")
        }

    /** 삭제 화면에서 강조해야 할 코드(재발/신규). */
    val recurringCodes: List<DtcCode>
        get() = clearComparison?.let { it.remaining + it.appeared }.orEmpty()

}
