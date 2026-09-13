package com.eunho.leafobd.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eunho.leafobd.BuildConfig
import com.eunho.leafobd.bluetooth.BluetoothClassicManager
import com.eunho.leafobd.bluetooth.BluetoothConnectionState
import com.eunho.leafobd.bluetooth.ConnectResult
import com.eunho.leafobd.bluetooth.ObdBluetoothDevice
import com.eunho.leafobd.data.AppSettings
import com.eunho.leafobd.data.DiagnosticKnowledge
import com.eunho.leafobd.data.KnowledgePackUpdateResult
import com.eunho.leafobd.data.KnowledgePackUpdater
import com.eunho.leafobd.data.SettingsRepository
import com.eunho.leafobd.data.UpdateChecker
import com.eunho.leafobd.data.UpdateResult
import com.eunho.leafobd.data.VehicleProfile
import com.eunho.leafobd.data.VehicleProfileRepository
import com.eunho.leafobd.data.VehicleProfiles
import com.eunho.leafobd.elm327.Elm327Client
import com.eunho.leafobd.elm327.Elm327Command
import com.eunho.leafobd.elm327.Elm327InitResult
import com.eunho.leafobd.elm327.Elm327Initializer
import com.eunho.leafobd.elm327.ProbeOutcome
import com.eunho.leafobd.elm327.Elm327Transport
import com.eunho.leafobd.elm327.FakeElm327Transport
import com.eunho.leafobd.elm327.FakeScenario
import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.log.DiagnosticLogRepository
import com.eunho.leafobd.log.DiagnosticSession
import com.eunho.leafobd.log.DiagnosticCommunicationSnapshot
import com.eunho.leafobd.log.DiagnosticVehicleSnapshot
import com.eunho.leafobd.log.SavedSession
import com.eunho.leafobd.log.SessionFormatter
import com.eunho.leafobd.log.SavedCodeHistory
import com.eunho.leafobd.log.SavedWorkshopReport
import com.eunho.leafobd.log.UnknownCodeInput
import com.eunho.leafobd.log.UnknownCodeQueue
import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.obd.CanMonitorParser
import com.eunho.leafobd.obd.CanMonitorResult
import com.eunho.leafobd.obd.DidScanResult
import com.eunho.leafobd.obd.DidScanner
import com.eunho.leafobd.obd.StandardDid
import com.eunho.leafobd.obd.UdsClear
import com.eunho.leafobd.obd.UdsClearResult
import com.eunho.leafobd.obd.EcuAddresses
import com.eunho.leafobd.obd.EcuScanProgress
import com.eunho.leafobd.obd.EcuScanResult
import com.eunho.leafobd.obd.EcuScanner
import com.eunho.leafobd.obd.UdsDiagnostics
import com.eunho.leafobd.obd.UdsDiagnosticsResult
import com.eunho.leafobd.obd.ObdMode
import com.eunho.leafobd.obd.ObdProtocol
import com.eunho.leafobd.obd.ObdResponseStatus
import com.eunho.leafobd.obd.ObdService
import com.eunho.leafobd.obd.PidValue
import com.eunho.leafobd.util.MacMasking
import com.eunho.leafobd.util.VinMasking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/**
 * 화면 상태를 보관하고 진단 흐름을 실행한다.
 *
 * 규칙
 *  - UI는 [BluetoothClassicManager] 나 소켓을 직접 다루지 않는다.
 *  - 긴 작업은 모두 코루틴에서 실행하고 [operationMutex] 로 중복 실행을 막는다.
 *  - 예외가 화면까지 올라가지 않도록 여기서 한국어 메시지로 바꾼다.
 *  - 화면 회전이나 뒤로 가기로 Composable이 사라져도 ViewModel 상태는 유지된다.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetooth = BluetoothClassicManager(application)
    private val logRepository = DiagnosticLogRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val vehicleProfileRepository = VehicleProfileRepository(application)
    private val recordVehicleGroups = com.eunho.leafobd.log.RecordVehicleGroups(application)
    private val knowledgePackUpdater = KnowledgePackUpdater(application)

    private val _uiState = MutableStateFlow(MainUiState(
        settings = settingsRepository.load(),
        vehicleProfiles = vehicleProfileRepository.load(),
        knowledgePackVersion = DiagnosticKnowledge.VERSION,
        knowledgePackRevision = DiagnosticKnowledge.activeRevision,
        knowledgePackSource = DiagnosticKnowledge.sourceLabel
    ))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** 어댑터 명령은 한 번에 한 흐름만 실행한다. */
    private val operationMutex = Mutex()

    private var transport: Elm327Transport? = null
    private var client: Elm327Client? = null

    /** 이번 세션에서 주고받은 모든 명령 로그. 파일 저장의 근거가 된다. */
    private val commandLogs = mutableListOf<CommandLog>()
    private val batteryStop = java.util.concurrent.atomic.AtomicBoolean(false)
    private val batteryHistory = com.eunho.leafobd.data.BatteryHistory(java.io.File(application.noBackupFilesDir, "battery-history"))
    private val batteryPreferences = application.getSharedPreferences("battery-preferences", android.content.Context.MODE_PRIVATE)
    private val batteryHistoryMutex = Mutex()

    init {
        val profile = runCatching { com.eunho.leafobd.ev.EvProfile.valueOf(batteryPreferences.getString("profile", "LEAF_ZE1")!!) }
            .getOrDefault(com.eunho.leafobd.ev.EvProfile.LEAF_ZE1)
        _uiState.update { it.copy(batteryProfile = profile) }
        viewModelScope.launch { refreshBatteryHistory() }
        refreshPrerequisites()
        refreshSavedSessions()
        if (_uiState.value.settings.checkForUpdates) checkForUpdates(silent = true)
    }

    // ------------------------------------------------------------------
    // 업데이트 확인
    // ------------------------------------------------------------------

    /**
     * 새 버전이 있는지 확인한다. 버전 번호만 읽으며 진단 데이터는 전송하지 않는다.
     *
     * @param silent 실패해도 사용자에게 메시지를 띄우지 않는다(앱 시작 시 자동 확인용).
     */
    fun checkForUpdates(silent: Boolean = false) {
        _uiState.update { it.copy(updateChecking = true) }
        viewModelScope.launch {
            when (val result = UpdateChecker.check(BuildConfig.VERSION_CODE)) {
                is UpdateResult.Available ->
                    _uiState.update { it.copy(updateAvailable = result.info, updateChecking = false) }

                is UpdateResult.UpToDate -> {
                    _uiState.update { it.copy(updateAvailable = null, updateChecking = false) }
                    if (!silent) showMessage("최신 버전을 사용 중입니다.")
                }

                is UpdateResult.Failed -> {
                    _uiState.update { it.copy(updateChecking = false) }
                    if (!silent) showMessage(result.message)
                }
            }
        }
    }

    fun setCheckForUpdates(enabled: Boolean) {
        updateSettings { it.copy(checkForUpdates = enabled) }
        if (enabled) checkForUpdates(silent = true)
        else _uiState.update { it.copy(updateAvailable = null) }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateAvailable = null) }
    }

    /** 사용자가 누른 경우에만 서명된 공개 오류코드 데이터 팩을 확인하고 설치한다. */
    fun updateKnowledgePack() {
        if (!_uiState.value.settings.checkForUpdates) {
            showMessage("업데이트 확인을 켠 뒤 오류코드 데이터를 업데이트할 수 있습니다.")
            return
        }
        if (_uiState.value.knowledgePackUpdating) return
        _uiState.update { it.copy(knowledgePackUpdating = true) }
        viewModelScope.launch {
            when (val result = knowledgePackUpdater.update(DiagnosticKnowledge.activeRevision, BuildConfig.VERSION_CODE)) {
                is KnowledgePackUpdateResult.Updated -> {
                    _uiState.update {
                        it.copy(
                            knowledgePackUpdating = false,
                            knowledgePackVersion = result.version,
                            knowledgePackRevision = result.revision,
                            knowledgePackSource = DiagnosticKnowledge.sourceLabel
                        )
                    }
                    showMessage("오류코드 데이터 ${result.entryCount}건을 업데이트했습니다.")
                }
                KnowledgePackUpdateResult.UpToDate -> {
                    _uiState.update { it.copy(knowledgePackUpdating = false) }
                    showMessage("오류코드 데이터가 최신입니다.")
                }
                is KnowledgePackUpdateResult.AppUpdateRequired -> {
                    _uiState.update { it.copy(knowledgePackUpdating = false) }
                    showMessage("이 데이터 팩은 더 최신 앱이 필요합니다. 앱을 먼저 업데이트하세요.")
                }
                is KnowledgePackUpdateResult.Failed -> {
                    _uiState.update { it.copy(knowledgePackUpdating = false) }
                    showMessage(result.message)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 권한 / 사전 상태
    // ------------------------------------------------------------------

    /** 권한·Bluetooth 상태를 다시 확인한다. 화면 진입 때마다 호출한다. */
    fun refreshPrerequisites() {
        val granted = bluetooth.hasPermission()
        _uiState.update {
            it.copy(
                bluetoothSupported = bluetooth.isSupported,
                permissionGranted = granted,
                bluetoothEnabled = bluetooth.isEnabled,
                // 권한이 있어야만 장치 목록 API를 호출할 수 있다.
                pairedDevices = if (granted) bluetooth.pairedDevices() else emptyList()
            )
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted, permissionRequested = true) }
        if (granted) {
            refreshPrerequisites()
        } else {
            showMessage("권한이 거부되었습니다. 설정에서 '근처 기기' 권한을 허용해야 어댑터에 연결할 수 있습니다.")
        }
    }

    fun selectDevice(device: ObdBluetoothDevice) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    // ------------------------------------------------------------------
    // 테스트 모드
    // ------------------------------------------------------------------

    fun setTestMode(enabled: Boolean) {
        disconnectInternal()
        updateSettings { it.copy(testMode = enabled) }
        _uiState.update {
            it.copy(
                connectionState = BluetoothConnectionState.Idle,
                elmInitialized = false,
                userMessage = if (enabled) {
                    "테스트 모드가 켜졌습니다. 표시되는 모든 값은 모의 데이터이며 실제 차량 결과가 아닙니다."
                } else {
                    "테스트 모드를 껐습니다."
                }
            )
        }
    }

    fun setFakeScenario(scenario: FakeScenario) = updateSettings { it.copy(fakeScenario = scenario) }

    // ------------------------------------------------------------------
    // 설정
    // ------------------------------------------------------------------

    fun setVehicleName(name: String) {
        val vehicle = name.ifBlank { AppSettings.DEFAULT_VEHICLE }
        if (vehicle == _uiState.value.vehicleName) return
        disconnectInternal()
        updateSettings { it.copy(vehicleName = vehicle, knownEcuAddresses = emptyList()) }
        _uiState.update { it.copy(ecuScan = null, udsResult = null, sessionSaved = false,
            modeResults = emptyList(), dtcs = emptyList(), liveValues = emptyList(), freezeFrame = null,
            vin = null, elmInitialized = false, clearComparison = null, udsClearResult = null,
            connectionState = BluetoothConnectionState.Idle) }
    }

    fun saveVehicleProfile(profile: VehicleProfile) {
        if (_uiState.value.diagnosisRunning || _uiState.value.batteryRunning) return
        runCatching {
            val profiles = VehicleProfiles.put(_uiState.value.vehicleProfiles, profile)
            vehicleProfileRepository.save(profiles)
            _uiState.update { it.copy(vehicleProfiles = profiles) }
        }.onSuccess {
            selectVehicleProfile(profile.id)
            showMessage("차량 프로필을 저장하고 진단 차량으로 선택했습니다.")
        }
            .onFailure { showMessage(it.message ?: "차량 프로필을 저장하지 못했습니다.") }
    }

    fun selectVehicleProfile(id: String?) {
        if (_uiState.value.diagnosisRunning || _uiState.value.batteryRunning) return
        val profile = id?.let { selected -> _uiState.value.vehicleProfiles.firstOrNull { it.id == selected } }
        if (id != null && profile == null) return
        disconnectInternal()
        updateSettings { it.copy(selectedVehicleProfileId = profile?.id, vehicleName = profile?.alias ?: AppSettings.DEFAULT_VEHICLE, knownEcuAddresses = emptyList()) }
        _uiState.update { state -> state.copy(ecuScan = null, udsResult = null, sessionSaved = false, modeResults = emptyList(), dtcs = emptyList(),
            liveValues = emptyList(), freezeFrame = null, vin = null, elmInitialized = false, clearComparison = null, udsClearResult = null,
            connectionState = BluetoothConnectionState.Idle,
            batteryProfile = profile?.evProfile ?: state.batteryProfile) }
        showMessage(profile?.let { "진단 차량을 ${it.alias}(으)로 선택했습니다." } ?: "진단 차량 선택을 해제했습니다.")
    }

    fun deleteVehicleProfile(id: String) {
        if (_uiState.value.diagnosisRunning || _uiState.value.batteryRunning) return
        val profiles = _uiState.value.vehicleProfiles.filterNot { it.id == id }
        if (profiles.size == _uiState.value.vehicleProfiles.size) return
        runCatching { vehicleProfileRepository.save(profiles) }
            .onSuccess {
                _uiState.update { it.copy(vehicleProfiles = profiles) }
                if (_uiState.value.settings.selectedVehicleProfileId == id) selectVehicleProfile(null)
                showMessage("차량 프로필을 삭제했습니다. 기존 진단 기록은 유지됩니다.")
            }.onFailure { showMessage("차량 프로필을 삭제하지 못했습니다.") }
    }

    // ------------------------------------------------------------------
    // CAN 버스 듣기 (읽기 전용)
    // ------------------------------------------------------------------

    /**
     * `ATMA` 로 일정 시간 버스를 **듣기만** 한다.
     *
     * 차량에 어떤 요청도 보내지 않는다. 표준 OBD가 전부 `NO DATA` 일 때
     * "버스에 통신이 흐르고 있는가"를 확인해 원인을 좁히기 위한 것이다.
     */
    fun runCanMonitor(durationMs: Long = CAN_MONITOR_MS) = launchExclusive {
        val activeClient = client ?: run {
            showMessage("먼저 어댑터에 연결해 주십시오.")
            return@launchExclusive
        }

        _uiState.update { it.copy(canMonitorRunning = true, canMonitor = null) }

        try {
            // 헤더를 켜야 CAN ID가 보인다. 긴 프레임도 허용한다.
            val prep = listOf(
                Elm327Command.HEADERS_ON,
                Elm327Command("ATAL", "긴 메시지 허용", optional = true)
            )
            prep.forEach { commandLogs.add(activeClient.send(it)) }

            val log = activeClient.monitor(durationMs = durationMs)
            commandLogs.add(log)

            val result =
                if (log.success) CanMonitorParser.parse(log.rawResponse, durationMs)
                else CanMonitorResult(errorMessage = log.errorMessage, durationMs = durationMs)

            _uiState.update { it.copy(canMonitor = result) }
            appendRawLog()

            // 헤더 설정을 원래대로 되돌린다.
            if (!_uiState.value.headersOn) {
                commandLogs.add(activeClient.send(Elm327Command.HEADERS_OFF))
            }
        } catch (e: Exception) {
            showMessage("버스 듣기 중 오류가 발생했습니다: ${e.message ?: e::class.java.simpleName}")
        } finally {
            _uiState.update { it.copy(canMonitorRunning = false) }
        }
    }

    // ------------------------------------------------------------------
    // ECU 응답 스캔 (읽기 전용)
    // ------------------------------------------------------------------

    /**
     * 개별 ECU 주소를 지정해 표준 UDS 읽기 요청을 보내고 응답하는 곳을 찾는다.
     *
     * 지금까지는 방송 주소(7DF)로만 요청했기 때문에, 게이트웨이가 있는 차량에서는
     * 응답을 못 받았을 수 있다. 이 스캔은 그 가정을 실제로 확인한다.
     *
     * @param full true 면 진단 주소 구간 전체(0x700~0x7EF), false 면 표준 8개만
     */
    fun runEcuScan(full: Boolean = false) = launchExclusive {
        val activeClient = client ?: run {
            showMessage("먼저 어댑터에 연결해 주십시오.")
            return@launchExclusive
        }

        val addresses = if (full) EcuAddresses.FULL else EcuAddresses.STANDARD
        _uiState.update {
            it.copy(
                ecuScanRunning = true,
                ecuScan = null,
                ecuScanProgress = EcuScanProgress(0, addresses.size, "", 0)
            )
        }

        try {
            val (logs, result) = EcuScanner(activeClient, headersOn = _uiState.value.headersOn)
                .scan(addresses) { progress ->
                    _uiState.update { it.copy(ecuScanProgress = progress) }
                }

            commandLogs.addAll(logs)
            _uiState.update { it.copy(ecuScan = result) }
            appendRawLog()
            saveSession()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    ecuScan = EcuScanResult(
                        errorMessage = "스캔 중 오류가 발생했습니다: " +
                            (e.message ?: e::class.java.simpleName)
                    )
                )
            }
        } finally {
            _uiState.update { it.copy(ecuScanRunning = false, ecuScanProgress = null) }
        }
    }

    /**
     * 스캔에서 응답한 ECU 주소들의 오류코드를 실제로 읽는다.
     *
     * 흐름 제어를 직접 보내 다중 프레임 응답을 끝까지 받는다.
     * 보내는 요청은 `19 02`(오류코드 읽기)와 선택적으로 `22 F1 90`(차대번호)뿐이다.
     */
    fun runUdsDiagnostics(addresses: List<String>? = null) = launchExclusive {
        val activeClient = client ?: run {
            showMessage("먼저 어댑터에 연결해 주십시오.")
            return@launchExclusive
        }

        // 이번 스캔 결과가 없으면 지난번에 찾아 둔 주소를 쓴다.
        // 차량 모듈이 절전에 들어가 스캔이 비어도 조회는 시도할 수 있어야 한다.
        val targets = addresses
            ?: _uiState.value.ecuScan?.respondingAddresses?.takeIf { it.isNotEmpty() }
            ?: _uiState.value.settings.knownEcuAddresses

        if (targets.isEmpty()) {
            showMessage("먼저 진단을 실행해 응답하는 ECU 를 찾아 주십시오.")
            return@launchExclusive
        }

        _uiState.update {
            it.copy(udsRunning = true, udsResult = null, udsProgress = Triple(0, targets.size, ""))
        }

        try {
            val (logs, result) = UdsDiagnostics(activeClient).readAll(
                addresses = targets,
                readVin = _uiState.value.settings.readVin
            ) { current, total, address ->
                _uiState.update { it.copy(udsProgress = Triple(current, total, address)) }
            }

            commandLogs.addAll(logs)
            _uiState.update { it.copy(udsResult = result, vin = result.vin ?: it.vin) }
            appendRawLog()
            saveSession()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    udsResult = UdsDiagnosticsResult(
                        errorMessage = "조회 중 오류가 발생했습니다: " +
                            (e.message ?: e::class.java.simpleName)
                    )
                )
            }
        } finally {
            _uiState.update { it.copy(udsRunning = false, udsProgress = null) }
        }
    }

    // ------------------------------------------------------------------
    // DID 스캔 (읽기 전용)
    // ------------------------------------------------------------------

    /**
     * `22`(ReadDataByIdentifier)로 읽히는 데이터 항목을 찾는다.
     *
     * 배터리 상태처럼 오류코드보다 구체적인 값을 확보하기 위한 것이다.
     * 읽기 전용이며 어떤 값도 바꾸지 않는다.
     *
     * @param wide true 면 제조사 구간까지 넓게 훑는다 (시간이 오래 걸린다)
     */
    fun runDidScan(wide: Boolean = false) = launchExclusive {
        val activeClient = client ?: run {
            showMessage("먼저 어댑터에 연결해 주십시오.")
            return@launchExclusive
        }
        val targets = _uiState.value.ecuScan?.respondingAddresses.orEmpty()
        if (targets.isEmpty()) {
            showMessage("먼저 진단을 실행해 응답하는 ECU 를 찾아 주십시오.")
            return@launchExclusive
        }

        val dids =
            if (wide) StandardDid.IDENTIFICATION + StandardDid.MANUFACTURER
            else StandardDid.IDENTIFICATION

        _uiState.update { it.copy(didScanRunning = true, didScan = null) }

        try {
            val (logs, result) = DidScanner(activeClient).scan(targets, dids) { c, t, label ->
                _uiState.update { it.copy(didProgress = Triple(c, t, label)) }
            }
            commandLogs.addAll(logs)
            _uiState.update { it.copy(didScan = result) }
            appendRawLog()
            saveSession()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    didScan = DidScanResult(
                        errorMessage = "스캔 중 오류: ${e.message ?: e::class.java.simpleName}"
                    )
                )
            }
        } finally {
            _uiState.update { it.copy(didScanRunning = false, didProgress = null) }
        }
    }

    // ------------------------------------------------------------------
    // ECU 오류코드 삭제 (UDS 14)
    // ------------------------------------------------------------------

    fun setUdsSafetyCheck(index: Int, checked: Boolean) {
        _uiState.update { state ->
            state.copy(
                udsSafetyChecks = state.udsSafetyChecks.toMutableList().also {
                    if (index in it.indices) it[index] = checked
                }
            )
        }
    }

    fun setUdsConfirmationInput(text: String) {
        _uiState.update { it.copy(udsConfirmationInput = text) }
    }

    /** 삭제 대상 ECU 를 켜고 끈다. 아무것도 안 고르면 전체가 대상이다. */
    fun toggleUdsClearTarget(address: String) {
        _uiState.update { state ->
            val next = state.udsClearTargets.toMutableSet()
            if (!next.add(address)) next.remove(address)
            state.copy(udsClearTargets = next)
        }
    }

    fun setUdsExtendedSession(enabled: Boolean) {
        _uiState.update { it.copy(udsUseExtendedSession = enabled) }
    }

    /**
     * ECU 오류코드를 **1회만** 삭제하고 자동으로 재조회한다.
     *
     * 삭제는 고장 감지를 끄지 않는다. 원인이 남아 있으면 곧바로 다시 검출된다.
     * 그 사실을 확인할 수 있도록 삭제 후 반드시 다시 읽어 전후를 비교한다.
     */
    fun runUdsClear() = launchExclusive {
        val eligibility = _uiState.value.udsClearEligibility
        if (!eligibility.eligible) {
            showMessage("삭제 조건이 충족되지 않았습니다.\n" + eligibility.unmetReasons.joinToString("\n"))
            return@launchExclusive
        }

        val activeClient = client ?: run {
            showMessage("어댑터 연결이 끊어졌습니다. 다시 연결한 뒤 진단부터 실행해 주십시오.")
            return@launchExclusive
        }

        val all = _uiState.value.ecuScan?.respondingAddresses?.takeIf { it.isNotEmpty() }
            ?: _uiState.value.settings.knownEcuAddresses
        val chosen = _uiState.value.udsClearTargets
        val targets = if (chosen.isEmpty()) all else all.filter { it in chosen }
        val before = _uiState.value.udsResult?.allCodes.orEmpty()

        if (targets.isEmpty()) {
            showMessage("삭제할 ECU 가 없습니다.")
            return@launchExclusive
        }
        val startedAt = System.currentTimeMillis()

        _uiState.update { it.copy(udsClearRunning = true, udsClearResult = null, udsClearAttempted = true) }

        try {
            // 1. 삭제 (반복하지 않는다)
            val (clearLogs, outcomes) = UdsClear(activeClient).clear(
                addresses = targets,
                extendedSession = _uiState.value.udsUseExtendedSession
            ) { c, t, address ->
                _uiState.update { it.copy(udsProgress = Triple(c, t, address)) }
            }
            commandLogs.addAll(clearLogs)

            // 2. ECU 가 상태를 정리할 시간을 준다.
            delay(RE_READ_DELAY_MS)

            // 3. 재조회 — 정말 지워졌는지, 곧바로 다시 나타나는지 확인한다.
            val (readLogs, reread) = UdsDiagnostics(activeClient).readAll(targets)
            commandLogs.addAll(readLogs)

            val result = UdsClearResult(
                outcomes = outcomes,
                before = before,
                after = reread.allCodes,
                verificationComplete = targets.size == all.size && reread.results.size == targets.size &&
                    reread.results.all { it.complete } && before.all { code -> code.ecu in reread.results.map { it.ecu } },
                durationMs = System.currentTimeMillis() - startedAt
            )

            _uiState.update {
                it.copy(
                    udsClearResult = result,
                    // 재조회 성공 여부와 무관하게 같은 세션에서는 다시 삭제하지 않는다.
                    udsClearAttempted = true,
                    udsConfirmationInput = "",
                    udsResult = reread,
                    udsProgress = null
                )
            }
            appendRawLog()
            saveSession()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    udsClearResult = UdsClearResult(
                        errorMessage = "삭제 중 오류: ${e.message ?: e::class.java.simpleName}"
                    )
                )
            }
        } finally {
            _uiState.update { it.copy(udsClearRunning = false, udsProgress = null) }
        }
    }

    fun setProtocol(protocol: ObdProtocol) {
        updateSettings { it.copy(protocol = protocol) }
        showMessage(
            if (protocol == ObdProtocol.AUTO) "프로토콜을 자동 선택으로 되돌렸습니다."
            else "프로토콜을 ${protocol.label} 로 고정했습니다. 다음 진단부터 적용됩니다."
        )
    }

    fun setAutoProtocolSweep(enabled: Boolean) = updateSettings { it.copy(autoProtocolSweep = enabled) }

    fun setHeadersOn(enabled: Boolean) {
        updateSettings { it.copy(headersOn = enabled) }
        showMessage(
            if (enabled) "CAN 헤더를 표시합니다. 다음 진단부터 어느 ECU가 응답했는지 함께 기록됩니다."
            else "CAN 헤더 표시를 껐습니다."
        )
    }

    fun setReadVin(enabled: Boolean) = updateSettings { it.copy(readVin = enabled) }

    fun setSaveVinMasked(enabled: Boolean) = updateSettings { it.copy(saveVinMasked = enabled) }

    fun setReadFreezeFrame(enabled: Boolean) = updateSettings { it.copy(readFreezeFrame = enabled) }

    fun setReadLiveValues(enabled: Boolean) = updateSettings { it.copy(readLiveValues = enabled) }

    /** 설정을 바꾸고 즉시 기기에 저장한다. */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _uiState.update { state ->
            val updated = transform(state.settings)
            settingsRepository.save(updated)
            state.copy(settings = updated)
        }
    }

    // ------------------------------------------------------------------
    // 연결
    // ------------------------------------------------------------------

    fun connect() = connectToDevice(null)

    /** 장치 목록의 한 버튼으로 선택과 연결을 함께 처리한다. */
    fun connectDevice(device: ObdBluetoothDevice) = connectToDevice(device)

    private fun connectToDevice(requestedDevice: ObdBluetoothDevice?) = launchExclusive {
        val state = _uiState.value

        if (state.testMode) {
            disconnectInternal()
            val fake = FakeElm327Transport(state.fakeScenario)
            fake.open()
            transport = fake
            client = Elm327Client(fake)
            _uiState.update {
                it.copy(connectionState = BluetoothConnectionState.Connected(fake.description))
            }
            return@launchExclusive
        }

        val prerequisite = bluetooth.currentPrerequisiteState()
        if (prerequisite != BluetoothConnectionState.Idle) {
            _uiState.update { it.copy(connectionState = prerequisite) }
            return@launchExclusive
        }

        val device = requestedDevice ?: state.selectedDevice ?: run {
            showMessage("먼저 연결할 어댑터를 선택해 주십시오.")
            return@launchExclusive
        }

        disconnectInternal()
        _uiState.update { it.copy(selectedDevice = device, connectionState = BluetoothConnectionState.Connecting) }

        when (val result = bluetooth.connect(device)) {
            is ConnectResult.Success -> {
                transport = result.transport
                client = Elm327Client(result.transport)
                _uiState.update {
                    it.copy(connectionState = BluetoothConnectionState.Connected(result.deviceName))
                }
            }

            is ConnectResult.Failure -> {
                _uiState.update {
                    it.copy(connectionState = BluetoothConnectionState.Error(result.message))
                }
            }
        }
    }

    fun disconnect() = launchExclusive {
        disconnectInternal()
        _uiState.update {
            it.copy(
                connectionState = BluetoothConnectionState.Disconnected,
                elmInitialized = false
            )
        }
    }

    private fun disconnectInternal() {
        runCatching { client?.close() }
        runCatching { transport?.close() }
        client = null
        transport = null
    }

    // ------------------------------------------------------------------
    // 진단
    // ------------------------------------------------------------------

    /**
     * 진단 전체 흐름:
     * 연결 → 초기화 → ATI/ATRV → 차량 정보(0101·지원 PID·VIN)
     * → **프리즈 프레임** → Mode 03/07/0A → 로그 저장.
     *
     * 프리즈 프레임을 DTC 읽기보다 먼저 읽는 이유:
     * Mode 04 로 삭제하면 함께 사라지는 값이라, 어떤 경우에도 먼저 확보해 두어야 한다.
     *
     * 어떤 단계에서 실패해도 지금까지의 결과와 원시 로그는 저장한다.
     */
    fun runDiagnosis() = launchExclusive {
        if (_uiState.value.selectedVehicleProfile == null) {
            showMessage("진단 전에 차량 프로필을 선택하세요.")
            return@launchExclusive
        }
        commandLogs.clear()
        _uiState.update {
            it.copy(
                diagnosisRunning = true,
                steps = defaultDiagnosisSteps(),
                modeResults = emptyList(),
                dtcs = emptyList(),
                rawLog = "",
                sessionSaved = false,
                savedSessionName = null,
                clearAttempted = false,
                udsClearAttempted = false,
                clearComparison = null,
                safetyChecks = List(ClearConfirmation.CHECKLIST.size) { false },
                confirmationInput = "",
                elmInitialized = false,
                protocolProbe = null,
                monitorStatus = null,
                supportedPids = null,
                liveValues = emptyList(),
                freezeFrame = null,
                vin = null,
                userMessage = null
            )
        }

        val settings = _uiState.value.settings

        try {
            // 1단계 — 연결
            updateStep(Step.CONNECT, StepStatus.RUNNING)
            if (client == null) {
                connectInline()
            }
            val activeClient = client
            if (activeClient == null) {
                updateStep(Step.CONNECT, StepStatus.FAILED, currentConnectionMessage())
                return@launchExclusive
            }
            updateStep(Step.CONNECT, StepStatus.SUCCESS, activeClient.transportDescription)

            // 2~4단계 — ELM327 초기화, ATI, ATRV
            updateStep(Step.INIT, StepStatus.RUNNING)
            val init = Elm327Initializer(
                client = activeClient,
                headersOn = settings.headersOn,
                protocol = settings.protocol,
                sweepOnFailure = settings.autoProtocolSweep
            ).initialize()
            commandLogs.addAll(init.logs)

            if (!init.success) {
                updateStep(Step.INIT, StepStatus.FAILED, init.errorMessage)
                appendRawLog()
                saveSession()
                return@launchExclusive
            }

            val probe = init.probe
            val initStatus = when {
                probe == null || probe.outcome == ProbeOutcome.RESPONDED -> StepStatus.SUCCESS
                probe.outcome == ProbeOutcome.CONNECTED_NO_DATA -> StepStatus.WARNING
                else -> StepStatus.FAILED
            }
            updateStep(
                Step.INIT,
                initStatus,
                buildInitDetail(init, settings.headersOn),
                probe?.attempted?.joinToString("\n") { "${it.first.setCommand} (${it.first.label}) -> ${it.second.label}" }
            )
            _uiState.update { it.copy(protocolProbe = probe) }
            updateStep(
                Step.ADAPTER_INFO,
                if (init.adapterInfo != null) StepStatus.SUCCESS else StepStatus.WARNING,
                init.adapterInfo ?: "어댑터 정보를 읽지 못했습니다.",
                init.logs.firstOrNull { it.command == "ATI" }?.rawResponse
            )
            updateStep(
                Step.VOLTAGE,
                if (init.voltage?.volts != null) StepStatus.SUCCESS else StepStatus.WARNING,
                init.voltage?.display ?: "전압을 읽지 못했습니다.",
                init.logs.firstOrNull { it.command == "ATRV" }?.rawResponse
            )

            _uiState.update {
                it.copy(
                    elmInitialized = true,
                    adapterInfo = init.adapterInfo,
                    adapterVoltage = init.voltage?.display,
                    voltageHint = init.voltage?.hint,
                    protocol = init.protocol
                )
            }

            val service = ObdService(activeClient, headersOn = settings.headersOn)

            // 5단계 — 차량 정보 (경고등 상태, 지원 PID, 실시간 값, VIN)
            runVehicleInfoStep(service, settings)

            // 6단계 — 프리즈 프레임 (반드시 삭제 전에)
            runFreezeFrameStep(service, settings)

            // 7~9단계 — 표준 DTC 읽기
            val results = mutableListOf<ModeResult>()
            val allCodes = mutableListOf<DtcCode>()

            ObdMode.entries.forEachIndexed { index, mode ->
                val step = Step.STORED_DTC + index
                updateStep(step, StepStatus.RUNNING)

                val outcome = service.readDtcs(mode)
                commandLogs.add(outcome.log)

                val status = when (outcome.result.status) {
                    ObdResponseStatus.OK -> StepStatus.SUCCESS
                    ObdResponseStatus.NO_DATA, ObdResponseStatus.UNSUPPORTED -> StepStatus.WARNING
                    else -> StepStatus.FAILED
                }
                val detail = when (outcome.result.status) {
                    ObdResponseStatus.OK ->
                        if (outcome.result.codes.isEmpty()) "코드 없음"
                        else "${outcome.result.codes.size}건: " +
                            outcome.result.codes.joinToString(", ") { it.code }

                    else -> outcome.result.message ?: outcome.result.status.label
                }

                updateStep(step, status, detail, outcome.log.rawResponse)
                results.add(
                    ModeResult(
                        mode = mode,
                        codes = outcome.result.codes,
                        statusLabel = outcome.result.status.label,
                        message = outcome.result.message,
                        raw = outcome.log.rawResponse
                    )
                )
                allCodes.addAll(outcome.result.codes)
            }

            _uiState.update {
                it.copy(
                    modeResults = results,
                    dtcs = allCodes,
                    lastDiagnosisAt = SessionFormatter.formatInstant(Instant.now())
                )
            }
            appendRawLog()

            // 10~11단계 — 표준 OBD 로 아무것도 못 얻었으면 ECU 주소를 직접 지정해 조회한다.
            //
            // 전기차는 표준 OBD PID 를 구현하지 않는 경우가 많다.
            // 방송 주소(7DF)로는 응답이 없어도 개별 ECU 주소로는 응답한다.
            if (settings.autoEcuScan && allCodes.isEmpty()) {
                runEcuScanStep(service = null, client = activeClient)
            } else {
                updateStep(Step.ECU_SCAN, StepStatus.SUCCESS, "표준 OBD 로 코드를 얻어 건너뜁니다.")
                updateStep(Step.UDS_DTC, StepStatus.SUCCESS, "표준 OBD 로 코드를 얻어 건너뜁니다.")
            }

            // 12단계 — 로그 저장
            updateStep(Step.SAVE_LOG, StepStatus.RUNNING)
            val saved = saveSession()
            if (saved != null) {
                updateStep(Step.SAVE_LOG, StepStatus.SUCCESS, "저장 완료: ${saved.baseName}")
            } else {
                updateStep(Step.SAVE_LOG, StepStatus.FAILED, "로그 저장에 실패했습니다.")
            }
        } catch (e: Exception) {
            showMessage("진단 중 오류가 발생했습니다: ${e.message ?: e::class.java.simpleName}")
        } finally {
            _uiState.update { it.copy(diagnosisRunning = false) }
        }
    }

    /** 초기화 단계에 표시할 요약. 프로토콜 탐색 결과를 함께 알려 준다. */
    private fun buildInitDetail(init: Elm327InitResult, headersOn: Boolean): String {
        val header = if (headersOn) " (CAN 헤더 표시)" else ""
        val probe = init.probe ?: return "초기화 완료$header"

        return when (probe.outcome) {
            ProbeOutcome.RESPONDED -> {
                val used = probe.protocol?.label ?: "자동"
                if (probe.foundBySweep) {
                    "자동 선택 실패 → $used 로 통신 성공$header\n" +
                        "설정에서 이 프로토콜로 고정하면 다음부터 더 빠릅니다."
                } else {
                    "초기화 완료 · $used$header"
                }
            }

            ProbeOutcome.CONNECTED_NO_DATA ->
                "버스에는 연결되었으나 차량이 데이터를 주지 않습니다. " +
                    "시동(READY) 상태를 확인해 주십시오.$header"

            ProbeOutcome.FAILED ->
                "차량과 통신하지 못했습니다. 시도한 프로토콜: " +
                    probe.attempted.joinToString(", ") { it.first.label } + "\n" +
                    "시동(READY) 상태, 어댑터 결합, OBD 단자를 확인해 주십시오.$header"
        }
    }

    /**
     * 5단계 — 경고등 상태(`0101`), 지원 PID, 실시간 값, VIN.
     *
     * 이 단계가 실패해도 진단 전체를 중단하지 않는다.
     * 전기차에는 없는 항목이 많아 실패가 정상일 수 있기 때문이다.
     */
    private suspend fun runVehicleInfoStep(service: ObdService, settings: AppSettings) {
        updateStep(Step.VEHICLE_INFO, StepStatus.RUNNING)
        val summary = StringBuilder()
        val raw = StringBuilder()

        // 0101 — 경고등과 ECU가 보고하는 DTC 개수
        val (statusLog, monitorStatus) = service.readMonitorStatus()
        commandLogs.add(statusLog)
        raw.append("0101 -> ${statusLog.rawResponse}")
        if (monitorStatus != null) {
            summary.append("경고등 ${monitorStatus.milLabel} · ECU 보고 코드 ${monitorStatus.dtcCount}건")
        } else {
            summary.append("경고등 상태 확인 불가")
        }

        // 지원 PID 목록
        val (pidLogs, supported) = service.readSupportedPids()
        commandLogs.addAll(pidLogs)
        if (supported.ids.isNotEmpty()) {
            summary.append(" · 지원 PID ${supported.ids.size}개")
        }

        // 해석 가능한 실시간 값
        var liveValues = emptyList<PidValue>()
        if (settings.readLiveValues && supported.ids.isNotEmpty()) {
            val (liveLogs, values) = service.readLiveValues(supported)
            commandLogs.addAll(liveLogs)
            liveValues = values
            if (values.isNotEmpty()) summary.append(" · 실시간 값 ${values.size}개")
        }

        // VIN (기본 꺼짐 — 식별정보이므로 사용자가 켜야 읽는다)
        var vin: String? = null
        if (settings.readVin) {
            val (vinLog, value) = service.readVin()
            commandLogs.add(vinLog)
            raw.append("\n0902 -> ${vinLog.rawResponse}")
            vin = value
            summary.append(if (value != null) " · VIN 확인" else " · VIN 확인 불가")
        }

        _uiState.update {
            it.copy(
                monitorStatus = monitorStatus,
                supportedPids = supported,
                liveValues = liveValues,
                vin = vin
            )
        }

        updateStep(
            Step.VEHICLE_INFO,
            if (monitorStatus != null || supported.ids.isNotEmpty()) StepStatus.SUCCESS
            else StepStatus.WARNING,
            summary.toString(),
            raw.toString()
        )
    }

    /**
     * 6단계 — 프리즈 프레임.
     *
     * **Mode 04 로 삭제하면 차량에서 사라지는 값이므로 반드시 삭제 전에 읽는다.**
     * 해석하지 못한 PID도 원시 응답을 그대로 남긴다.
     */
    private suspend fun runFreezeFrameStep(service: ObdService, settings: AppSettings) {
        if (!settings.readFreezeFrame) {
            updateStep(Step.FREEZE_FRAME, StepStatus.WARNING, "설정에서 꺼져 있어 건너뜁니다.")
            return
        }

        updateStep(Step.FREEZE_FRAME, StepStatus.RUNNING)
        val (logs, frame) = service.readFreezeFrame()
        commandLogs.addAll(logs)
        _uiState.update { it.copy(freezeFrame = frame) }

        // 통신이 실패했는데 원시 응답만 남은 경우를 "성공"으로 표시하면 안 된다.
        // 삭제 전 확보 여부를 판단하는 근거이므로 실패는 실패로 보여야 한다.
        val readSucceeded = logs.any { it.success }

        val status = when {
            !readSucceeded -> StepStatus.FAILED
            frame.hasData -> StepStatus.SUCCESS
            else -> StepStatus.WARNING
        }

        val detail = when {
            !readSucceeded ->
                logs.firstOrNull { !it.success }?.errorMessage
                    ?: "프리즈 프레임을 읽지 못했습니다."
            frame.triggerDtc != null ->
                "원인 DTC ${frame.triggerDtc} · 값 ${frame.values.size}개 저장됨"
            frame.values.isNotEmpty() -> "값 ${frame.values.size}개 저장됨 (원인 DTC 확인 불가)"
            else -> "저장된 프리즈 프레임이 없습니다. (전기차에서는 흔한 결과입니다)"
        }

        updateStep(
            Step.FREEZE_FRAME,
            status,
            detail,
            frame.rawResponses.entries.joinToString("\n") { "${it.key} -> ${it.value}" }
        )
    }

    /**
     * 10~11단계 — ECU 주소 스캔과 UDS 오류코드 읽기.
     *
     * 표준 OBD 가 아무것도 돌려주지 않을 때만 실행된다.
     * 보내는 요청은 ISO 14229 표준 읽기뿐이며 차량 상태를 바꾸지 않는다.
     */
    private suspend fun runEcuScanStep(service: ObdService?, client: Elm327Client) {
        val scanner = EcuScanner(client, headersOn = _uiState.value.headersOn)
        val known = _uiState.value.settings.knownEcuAddresses

        // 1) 지난번에 찾아 둔 주소가 있으면 그것부터 확인한다.
        //
        // 전체 구간 스캔은 4분쯤 걸리는데, 그 사이 차량 모듈이 절전에 들어가
        // 정작 조회할 때는 응답이 없어지는 일이 있다. 아는 주소로 바로 가면 20초면 된다.
        var scan: EcuScanResult? = null
        var authoritative = false
        if (known.isNotEmpty()) {
            updateStep(Step.ECU_SCAN, StepStatus.RUNNING, "지난번 찾은 ECU ${known.size}곳 확인 중…")
            val (knownLogs, knownScan) = scanner.scan(known) { progress ->
                _uiState.update { it.copy(ecuScanProgress = progress) }
            }
            commandLogs.addAll(knownLogs)

            // 절반 이상이 응답해야 목록이 아직 유효하다고 본다.
            // 크게 줄었으면 목록이 틀렸거나 차량이 절전 중이므로 다시 찾는다.
            if (knownScan.respondingAddresses.size >= (known.size + 1) / 2) {
                scan = knownScan
            }
        }

        // 2) 없으면 표준 주소 8개
        if (scan == null) {
            updateStep(Step.ECU_SCAN, StepStatus.RUNNING, "표준 주소 확인 중…")
            val (logs, standardScan) = scanner.scan(EcuAddresses.STANDARD) { progress ->
                _uiState.update { it.copy(ecuScanProgress = progress) }
            }
            commandLogs.addAll(logs)
            if (standardScan.found) {
                scan = standardScan
                authoritative = true
            }
        }

        // 3) 그래도 없으면 진단 주소 구간 전체
        if (scan == null) {
            updateStep(Step.ECU_SCAN, StepStatus.RUNNING, "전체 구간 확인 중… (수 분 걸립니다)")
            val (fullLogs, fullScan) = scanner.scan(EcuAddresses.FULL) { progress ->
                _uiState.update { it.copy(ecuScanProgress = progress) }
            }
            commandLogs.addAll(fullLogs)
            scan = fullScan
            authoritative = true
        }

        _uiState.update { it.copy(ecuScan = scan, ecuScanProgress = null) }

        // 새로 훑어 찾은 결과만 목록으로 저장한다.
        // 기존 목록을 확인만 한 경우에는 덮어쓰지 않는다.
        // (차량이 절전 중이면 일부만 응답하는데, 그걸로 목록을 줄이면 안 된다)
        if (authoritative && scan.found) {
            updateSettings { it.copy(knownEcuAddresses = scan.respondingAddresses) }
        }

        if (!scan.found) {
            updateStep(
                Step.ECU_SCAN,
                StepStatus.FAILED,
                scan.summary + "\n차량 전원을 켜고(브레이크 없이 전원 버튼 2회) 다시 시도해 주십시오."
            )
            updateStep(Step.UDS_DTC, StepStatus.FAILED, "응답하는 ECU 가 없어 건너뜁니다.")
            return
        }

        val found = scan.respondingAddresses.size
        // 지난번보다 크게 줄었으면 차량이 절전에 들어갔을 가능성이 크다.
        val shrank = known.isNotEmpty() && found < known.size / 2

        updateStep(
            Step.ECU_SCAN,
            if (shrank) StepStatus.WARNING else StepStatus.SUCCESS,
            buildString {
                append("응답 ECU ${found}곳: ${scan.respondingAddresses.joinToString(", ")}")
                if (shrank) {
                    append("\n지난번 ${known.size}곳보다 크게 줄었습니다. ")
                    append("차량 모듈이 절전에 들어갔을 수 있습니다. ")
                    append("전원을 켜고 다시 시도하면 더 많이 잡힙니다.")
                }
            }
        )

        // ECU별 오류코드 읽기
        updateStep(Step.UDS_DTC, StepStatus.RUNNING)
        val (udsLogs, uds) = UdsDiagnostics(client).readAll(
            addresses = scan.respondingAddresses,
            readVin = _uiState.value.settings.readVin
        ) { current, total, address ->
            _uiState.update { it.copy(udsProgress = Triple(current, total, address)) }
        }
        commandLogs.addAll(udsLogs)
        _uiState.update {
            it.copy(udsResult = uds, udsProgress = null, vin = uds.vin ?: it.vin)
        }

        val incomplete = uds.results.count { it.needsRetry }
        updateStep(
            Step.UDS_DTC,
            when {
                uds.allCodes.isEmpty() -> StepStatus.WARNING
                incomplete > 0 -> StepStatus.WARNING
                else -> StepStatus.SUCCESS
            },
            buildString {
                append(uds.summary)
                if (uds.activeCodes.isNotEmpty()) {
                    append("\n현재 고장 또는 확정: ")
                    append(uds.activeCodes.joinToString(", ") { it.fullCode })
                }
                if (incomplete > 0) append("\n(${incomplete}개 ECU 는 응답이 완전하지 않습니다)")
            }
        )
    }

    /** [runDiagnosis] 안에서 쓰는 연결. 이미 Mutex 안이므로 [connect] 를 호출하지 않는다. */
    private suspend fun connectInline() {
        val state = _uiState.value

        if (state.testMode) {
            val fake = FakeElm327Transport(state.fakeScenario)
            fake.open()
            transport = fake
            client = Elm327Client(fake)
            _uiState.update {
                it.copy(connectionState = BluetoothConnectionState.Connected(fake.description))
            }
            return
        }

        val prerequisite = bluetooth.currentPrerequisiteState()
        if (prerequisite != BluetoothConnectionState.Idle) {
            _uiState.update { it.copy(connectionState = prerequisite) }
            return
        }

        val device = state.selectedDevice ?: return
        _uiState.update { it.copy(connectionState = BluetoothConnectionState.Connecting) }

        when (val result = bluetooth.connect(device)) {
            is ConnectResult.Success -> {
                transport = result.transport
                client = Elm327Client(result.transport)
                _uiState.update {
                    it.copy(connectionState = BluetoothConnectionState.Connected(result.deviceName))
                }
            }

            is ConnectResult.Failure -> {
                _uiState.update {
                    it.copy(connectionState = BluetoothConnectionState.Error(result.message))
                }
            }
        }
    }

    private fun currentConnectionMessage(): String =
        when (val state = _uiState.value.connectionState) {
            is BluetoothConnectionState.Error -> state.message
            BluetoothConnectionState.PermissionRequired -> "Bluetooth 권한이 필요합니다."
            BluetoothConnectionState.BluetoothOff -> "Bluetooth 가 꺼져 있습니다."
            BluetoothConnectionState.Unsupported -> "이 기기는 Bluetooth Classic 을 지원하지 않습니다."
            else -> "어댑터를 선택하고 연결해 주십시오."
        }

    // ------------------------------------------------------------------
    // 삭제 (Mode 04)
    // ------------------------------------------------------------------

    fun setSafetyCheck(index: Int, checked: Boolean) {
        _uiState.update { state ->
            state.copy(
                safetyChecks = state.safetyChecks.toMutableList().also {
                    if (index in it.indices) it[index] = checked
                }
            )
        }
    }

    fun setConfirmationInput(text: String) {
        _uiState.update { it.copy(confirmationInput = text) }
    }

    /**
     * 표준 DTC 삭제를 **1회만** 실행하고 자동으로 재조회한다.
     *
     * 사전조건을 다시 한번 검사한다. UI 버튼이 비활성이어도 여기서 한 번 더 막는다.
     */
    fun clearDtcs() = launchExclusive {
        val eligibility = _uiState.value.clearEligibility
        if (!eligibility.eligible) {
            showMessage("삭제 조건이 충족되지 않았습니다.\n" + eligibility.unmetReasons.joinToString("\n"))
            return@launchExclusive
        }

        val activeClient = client ?: run {
            showMessage("어댑터 연결이 끊어졌습니다. 다시 연결한 뒤 진단부터 실행해 주십시오.")
            return@launchExclusive
        }

        _uiState.update { it.copy(clearRunning = true, clearAttempted = true) }

        try {
            val service = ObdService(activeClient, headersOn = _uiState.value.headersOn)
            val before = _uiState.value.dtcs

            // 1. 삭제 실행 (반복하지 않는다)
            val clearOutcome = service.clearDtcs()
            commandLogs.add(clearOutcome.log)

            // 2. 3초 대기 — ECU가 삭제를 처리할 시간을 준다.
            delay(RE_READ_DELAY_MS)

            // 3. 재조회
            val after = mutableListOf<DtcCode>()
            val results = mutableListOf<ModeResult>()
            val verificationReads = mutableListOf<com.eunho.leafobd.obd.DtcReadOutcome>()
            ObdMode.entries.forEach { mode ->
                val outcome = service.readDtcs(mode)
                verificationReads.add(outcome)
                commandLogs.add(outcome.log)
                after.addAll(outcome.result.codes)
                results.add(
                    ModeResult(
                        mode = mode,
                        codes = outcome.result.codes,
                        statusLabel = outcome.result.status.label,
                        message = outcome.result.message,
                        raw = outcome.log.rawResponse
                    )
                )
            }

            // 4. 전후 비교
            val verified = com.eunho.leafobd.obd.ClearVerification.complete(verificationReads, before)
            val beforeCodes = before.map { com.eunho.leafobd.obd.ClearVerification.identity(it) }.toSet()
            val afterCodes = after.map { com.eunho.leafobd.obd.ClearVerification.identity(it) }.toSet()
            val comparison = ClearComparison(
                response = clearOutcome.log.rawResponse.trim().ifEmpty { clearOutcome.message },
                accepted = clearOutcome.accepted,
                before = before,
                after = after,
                cleared = if (verified) before.filter { com.eunho.leafobd.obd.ClearVerification.identity(it) !in afterCodes } else emptyList(),
                remaining = after.filter { com.eunho.leafobd.obd.ClearVerification.identity(it) in beforeCodes },
                appeared = after.filter { com.eunho.leafobd.obd.ClearVerification.identity(it) !in beforeCodes },
                verificationComplete = verified
            )

            _uiState.update {
                it.copy(
                    clearAttempted = true,
                    clearComparison = comparison,
                    modeResults = results,
                    userMessage = clearOutcome.message
                )
            }
            appendRawLog()

            // 5. 결과 저장 (삭제 전후가 모두 담긴 세션으로 다시 저장한다)
            saveSession()
        } catch (e: Exception) {
            showMessage("삭제 중 오류가 발생했습니다: ${e.message ?: e::class.java.simpleName}")
        } finally {
            _uiState.update { it.copy(clearRunning = false) }
        }
    }

    // ------------------------------------------------------------------
    // 로그
    // ------------------------------------------------------------------

    private fun appendRawLog() {
        val text = commandLogs.joinToString("\n") { log ->
            val raw = log.rawResponse.replace("\r", "\\r").replace("\n", "\\n")
            ">> ${log.command}\n<< $raw" + (log.errorMessage?.let { "\n!! $it" } ?: "")
        }
        _uiState.update { it.copy(rawLog = text) }
    }

    private suspend fun saveSession(): SavedSession? {
        val session = buildSession()
        return logRepository.save(session).fold(
            onSuccess = { saved ->
                _uiState.value.selectedVehicleProfile?.let { profile ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { recordVehicleGroups.assign(listOf(saved), profile.alias) }
                    }
                }
                _uiState.update {
                    it.copy(sessionSaved = true, savedSessionName = saved.baseName)
                }
                refreshSavedSessionsNow()
                saved
            },
            onFailure = { e ->
                showMessage(e.message ?: "로그 저장에 실패했습니다.")
                null
            }
        )
    }

    fun buildSession(): DiagnosticSession {
        val state = _uiState.value
        return DiagnosticSession(
            id = UUID.randomUUID().toString(),
            startedAt = Instant.now(),
            deviceName = state.selectedDevice?.name ?: client?.transportDescription,
            deviceAddressMasked = MacMasking.mask(state.selectedDevice?.address),
            adapterInfo = state.adapterInfo,
            adapterVoltage = state.adapterVoltage,
            protocol = state.protocol,
            protocolAttempts = state.protocolProbe?.attempted
                ?.joinToString(", ") { "${it.first.setCommand}(${it.first.label})=${it.second.label}" },
            vehicle = state.settings.vehicleName,
            vehicleSnapshot = state.selectedVehicleProfile?.let(DiagnosticVehicleSnapshot::from),
            communicationSnapshot = DiagnosticCommunicationSnapshot(
                protocolIdentified = !state.protocol.isNullOrBlank(),
                standardDataObserved = state.monitorStatus != null || state.supportedPids != null ||
                    state.freezeFrame?.hasData == true || state.dtcs.isNotEmpty(),
                udsRespondingEcuCount = state.ecuScan?.respondingAddresses?.size
            ),
            // VIN 은 식별정보다. 설정에서 끄지 않는 한 마스킹해서 저장한다.
            vin = if (state.settings.saveVinMasked) VinMasking.mask(state.vin) else state.vin,
            monitorStatus = state.monitorStatus,
            supportedPidsHex = state.supportedPids?.rawHex,
            liveValues = state.liveValues,
            freezeFrame = state.freezeFrame,
            headersOn = state.headersOn,
            udsResults = state.udsResult?.results.orEmpty(),
            udsClearAttempted = state.udsClearAttempted,
            udsClearResult = state.udsClearResult,
            commands = commandLogs.toList(),
            dtcBeforeClear = state.clearComparison?.before ?: state.dtcs,
            clearAttempted = state.clearAttempted,
            clearResponse = state.clearComparison?.response,
            dtcAfterClear = state.clearComparison?.after.orEmpty(),
            clearVerificationComplete = state.clearComparison?.verificationComplete == true,
            simulated = state.testMode,
            appVersion = BuildConfig.VERSION_NAME,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    fun clipboardSummary(): String = SessionFormatter.toClipboardSummary(buildSession())

    fun refreshSavedSessions() = viewModelScope.launch { refreshSavedSessionsNow() }

    private suspend fun refreshSavedSessionsNow() {
        val sessions = logRepository.list()
        _uiState.update { it.copy(savedSessions = sessions) }
    }

    suspend fun readSavedSession(saved: SavedSession): String = logRepository.readText(saved)

    fun deleteSavedSession(saved: SavedSession) = viewModelScope.launch {
        if (logRepository.delete(saved)) {
            refreshSavedSessionsNow()
            showMessage("기록을 삭제했습니다: ${saved.baseName}")
        } else {
            showMessage("기록을 삭제하지 못했습니다.")
        }
    }

    // ------------------------------------------------------------------
    // 공통
    // ------------------------------------------------------------------

    private fun updateStep(
        order: Int,
        status: StepStatus,
        detail: String? = null,
        raw: String? = null
    ) {
        _uiState.update { state ->
            state.copy(
                steps = state.steps.map { step ->
                    if (step.order == order) {
                        step.copy(
                            status = status,
                            detail = detail ?: step.detail,
                            raw = raw ?: step.raw
                        )
                    } else {
                        step
                    }
                }
            )
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(userMessage = message) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** 차량별로 분류된 실제 진단 기록에서 아직 해설 원장에 없는 코드만 찾는다. */
    fun refreshUnknownCodeQueue() {
        if (_uiState.value.unknownCodeQueueLoading) return
        _uiState.update { it.copy(unknownCodeQueueLoading = true) }
        viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val sessions = logRepository.list()
                    val labels = recordVehicleGroups.labels(sessions)
                    val inputs = sessions.map { saved ->
                        val record = runCatching {
                            require(saved.jsonFile.length() in 1..SavedWorkshopReport.MAX_BYTES.toLong())
                            SavedCodeHistory.parse(saved.jsonFile.readText(Charsets.UTF_8))
                        }.getOrNull()
                        UnknownCodeInput(labels[saved.baseName], record)
                    }
                    UnknownCodeQueue.analyze(inputs, DiagnosticKnowledge.entries.map { it.code }.toSet())
                }
                _uiState.update { it.copy(unknownCodeQueue = result, unknownCodeQueueLoading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(unknownCodeQueueLoading = false) }
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(unknownCodeQueueLoading = false) }
                showMessage("미해설 코드 대기함을 만들지 못했습니다. 저장 기록을 확인하세요.")
            }
        }
    }

    fun setBatterySnapshot(snapshot: com.eunho.leafobd.data.BatterySnapshot?) {
        if (_uiState.value.batteryRunning || _uiState.value.batterySaving) return
        _uiState.update { it.copy(batterySnapshot = snapshot, batteryMessage = "", batteryRaw = "", batteryRecordId = null, batteryRecordProfile = null) }
    }

    fun selectBatteryProfile(profile: com.eunho.leafobd.ev.EvProfile) {
        if (_uiState.value.batteryRunning || _uiState.value.batterySaving) return
        batteryPreferences.edit().putString("profile", profile.name).apply()
        _uiState.update { it.copy(batteryProfile = profile, batterySnapshot = null, batteryRecordId = null,
            batteryRecordProfile = null, batteryMessage = "", batteryRaw = "") }
    }

    fun stopBatteryRead() {
        if (!_uiState.value.batteryRunning) return
        batteryStop.set(true)
        _uiState.update { it.copy(batteryStopRequested = true) }
    }

    private suspend fun refreshBatteryHistory() {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            batteryHistoryMutex.withLock { batteryHistory.load() }
        }
        _uiState.update { it.copy(batteryRecords = result.first,
            batteryHistoryError = if (result.second > 0) "읽을 수 없는 기록 ${result.second}건 · 다른 기록은 정상 표시" else "") }
    }

    fun openBatteryRecord(id: String) {
        val state = _uiState.value
        if (state.batteryRunning || state.batterySaving) return
        val record = state.batteryRecords.firstOrNull { it.id == id } ?: return
        if (record.profile != null && record.profile != state.batteryProfile) return
        _uiState.update { it.copy(batterySnapshot = record.snapshot, batteryRecordProfile = record.profile,
            batteryRecordId = id, batteryRaw = record.raw, batteryMessage = "저장 기록을 열었습니다.\n${record.message}") }
    }

    fun deleteBatteryRecord(id: String) {
        if (_uiState.value.batteryRunning || _uiState.value.batterySaving || _uiState.value.batteryRecords.none { it.id == id }) return
        _uiState.update { it.copy(batterySaving = true) }
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { batteryHistoryMutex.withLock { batteryHistory.delete(id) } }
                _uiState.update { if (it.batteryRecordId == id) it.copy(batteryRecordId = null, batterySnapshot = null,
                    batteryRecordProfile = null, batteryRaw = "", batteryMessage = "") else it }
                refreshBatteryHistory()
            } catch (_: Exception) { showMessage("기록을 삭제하지 못했습니다. 다시 시도해 주세요.") }
            finally { _uiState.update { it.copy(batterySaving = false) } }
        }
    }

    private suspend fun persistBattery(record: com.eunho.leafobd.data.BatteryRecord): Boolean {
        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { batteryHistoryMutex.withLock { batteryHistory.save(record) } }
        }
        if (saved.isSuccess) {
            _uiState.update { it.copy(batteryRecordId = record.id) }
            refreshBatteryHistory()
        } else showMessage("측정값 저장 실패 · 화면에서 다시 저장할 수 있습니다: ${saved.exceptionOrNull()?.message.orEmpty()}")
        return saved.isSuccess
    }

    fun saveBatterySnapshot() {
        val state = _uiState.value
        val snapshot = state.batterySnapshot ?: return
        if (state.batteryRunning || state.batterySaving || state.batteryRecordId != null || snapshot.demo) return
        _uiState.update { it.copy(batterySaving = true) }
        viewModelScope.launch {
            try {
                persistBattery(com.eunho.leafobd.data.BatteryRecord(profile = state.batteryRecordProfile,
                    snapshot = snapshot, message = state.batteryMessage, raw = state.batteryRaw, vehicleProfileId = state.selectedVehicleProfile?.id))
            } finally { _uiState.update { it.copy(batterySaving = false) } }
        }
    }

    fun readEvBattery(profile: com.eunho.leafobd.ev.EvProfile, enabled: Boolean) = launchExclusive {
        if (!enabled || _uiState.value.batterySaving || profile != _uiState.value.batteryProfile) return@launchExclusive
        val vehicleProfile = _uiState.value.selectedVehicleProfile
        if (vehicleProfile == null) {
            showMessage("배터리 조회 전에 차량 프로필을 선택하세요.")
            return@launchExclusive
        }
        if (vehicleProfile.evProfile != profile) {
            showMessage("선택한 차량 프로필과 배터리 조회 차종이 다릅니다.")
            return@launchExclusive
        }
        val active = client
        if (active == null || !active.isOpen || active.simulated || _uiState.value.testMode) {
            showMessage("어댑터 선택 화면에서 실제 어댑터에 먼저 연결하세요. 가상 모드에서는 차량 조회를 실행하지 않습니다.")
            return@launchExclusive
        }
        val started = Instant.now()
        batteryStop.set(false)
        _uiState.update { it.copy(batteryRunning = true, batterySnapshot = null, batteryMessage = "조회 중 · 완료 후 연결을 해제합니다.", batteryRaw = "",
            batteryStopRequested = false, batteryProgress = 0f, batteryStage = "조회 준비", batteryRecordId = null, batteryRecordProfile = profile) }
        try {
            val result = com.eunho.leafobd.ev.EvBatteryService(active).read(profile, batteryStop::get) { stage, done, total ->
                _uiState.update { it.copy(batteryStage = stage, batteryProgress = done.toFloat() / total) }
            }
            val raw = result.logs.joinToString("\n") { "${it.timestamp} > ${it.command}\n${it.rawResponse}" }
            _uiState.update { it.copy(batterySnapshot = result.snapshot, batteryMessage = result.messages.joinToString("\n"), batteryRaw = raw) }
            persistBattery(com.eunho.leafobd.data.BatteryRecord(profile = profile, snapshot = result.snapshot,
                message = result.messages.joinToString("\n"), raw = raw, vehicleProfileId = vehicleProfile.id))
            val saved = logRepository.save(DiagnosticSession(
                id = UUID.randomUUID().toString(), startedAt = started,
                deviceName = _uiState.value.selectedDevice?.name,
                deviceAddressMasked = MacMasking.mask(_uiState.value.selectedDevice?.address),
                adapterInfo = null, adapterVoltage = null, vehicle = profile.label + " · 배터리 읽기 시험 · DTC 미조회",
                vehicleSnapshot = DiagnosticVehicleSnapshot.from(vehicleProfile), headersOn = true,
                commands = result.logs, appVersion = BuildConfig.VERSION_NAME, batteryOnly = true
            ))
            if (saved.isFailure) showMessage("배터리 원시 기록 저장에 실패했습니다.")
            else saved.getOrNull()?.let { stored -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { recordVehicleGroups.assign(listOf(stored), vehicleProfile.alias) }
            } }
            refreshSavedSessionsNow()
        } finally {
            disconnectInternal()
            _uiState.update { it.copy(batteryRunning = false, elmInitialized = false, connectionState = BluetoothConnectionState.Disconnected) }
        }
    }

    /**
     * 어댑터를 쓰는 작업은 동시에 하나만 실행한다.
     * 이미 실행 중이면 새 요청을 조용히 무시하고 안내만 남긴다.
     */
    private fun launchExclusive(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!operationMutex.tryLock()) {
                showMessage("이미 다른 작업이 진행 중입니다. 잠시 기다려 주십시오.")
                return@launch
            }
            try {
                block()
            } catch (e: Exception) {
                showMessage("작업 중 오류가 발생했습니다: ${e.message ?: e::class.java.simpleName}")
            } finally {
                operationMutex.unlock()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectInternal()
    }

    private companion object {
        /** 삭제 후 재조회까지 기다리는 시간(지시서 2.3). */
        const val RE_READ_DELAY_MS = 3_000L

        /** CAN 버스를 듣는 기본 시간. */
        const val CAN_MONITOR_MS = 10_000L
    }
}
