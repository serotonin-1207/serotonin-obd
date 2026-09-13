package com.eunho.leafobd.data

import android.content.Context
import com.eunho.leafobd.elm327.FakeScenario
import com.eunho.leafobd.obd.ObdProtocol

/**
 * 사용자가 바꿀 수 있는 설정.
 *
 * @param vehicleName 로그와 화면에 표시할 차량 이름. 다른 차량에도 이 앱을 쓸 수 있게 한다.
 * @param headersOn `ATH1` 로 CAN 헤더를 표시할지. 어느 ECU가 응답했는지 알 수 있다.
 * @param readVin `0902` 로 차대번호를 읽을지. 기본값은 끔(식별정보이므로 사용자가 켜야 한다).
 * @param saveVinMasked 파일에 VIN 을 저장할 때 마스킹할지. 기본값은 켬.
 * @param readFreezeFrame 삭제 전에 프리즈 프레임을 읽어 둘지. 기본값은 켬.
 * @param readLiveValues 지원 PID 실시간 값을 읽을지. 시간이 더 걸린다.
 * @param protocol 사용할 OBD 프로토콜. 기본값은 자동 선택.
 * @param autoProtocolSweep 자동 선택이 실패했을 때 후보 프로토콜을 순서대로 시도할지.
 */
data class AppSettings(
    val vehicleName: String = DEFAULT_VEHICLE,
    val selectedVehicleProfileId: String? = null,
    val headersOn: Boolean = false,
    val protocol: ObdProtocol = ObdProtocol.AUTO,
    val autoProtocolSweep: Boolean = true,
    val readVin: Boolean = false,
    val saveVinMasked: Boolean = true,
    val readFreezeFrame: Boolean = true,
    val readLiveValues: Boolean = true,
    /**
     * 표준 OBD 로 아무 코드도 못 얻으면 ECU 주소를 직접 지정해 조회할지.
     * 전기차처럼 표준 PID 를 구현하지 않는 차량에 필요하다.
     */
    val autoEcuScan: Boolean = true,
    /**
     * 지난번 스캔에서 응답한 ECU 주소.
     *
     * 전체 구간(240개) 스캔은 4분쯤 걸리는데, 그 사이에 차량 모듈이 절전에 들어가
     * 정작 조회할 때는 응답이 없는 일이 생긴다.
     * 한 번 찾아 둔 주소를 기억해 두고 바로 조회하면 20초 안에 끝난다.
     */
    val knownEcuAddresses: List<String> = emptyList(),
    val testMode: Boolean = false,
    val fakeScenario: FakeScenario = FakeScenario.RECURRING,
    /** 앱 버전 확인과 수동 데이터 팩 갱신을 허용할지. 끄면 인터넷을 전혀 쓰지 않는다. */
    val checkForUpdates: Boolean = true
) {
    companion object {
        const val DEFAULT_VEHICLE: String = "차량 미설정"
    }
}

/**
 * 설정을 기기에 저장한다.
 *
 * `SharedPreferences` 만 사용한다. 외부 라이브러리를 추가하지 않고,
 * 저장 내용은 모두 앱 전용 영역에 남으며 밖으로 나가지 않는다.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        vehicleName = prefs.getString(KEY_VEHICLE, AppSettings.DEFAULT_VEHICLE)
            ?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_VEHICLE,
        selectedVehicleProfileId = prefs.getString(KEY_SELECTED_VEHICLE_PROFILE, null),
        headersOn = prefs.getBoolean(KEY_HEADERS, false),
        protocol = ObdProtocol.ofCode(prefs.getString(KEY_PROTOCOL, null)) ?: ObdProtocol.AUTO,
        autoProtocolSweep = prefs.getBoolean(KEY_PROTOCOL_SWEEP, true),
        readVin = prefs.getBoolean(KEY_READ_VIN, false),
        saveVinMasked = prefs.getBoolean(KEY_MASK_VIN, true),
        readFreezeFrame = prefs.getBoolean(KEY_FREEZE_FRAME, true),
        readLiveValues = prefs.getBoolean(KEY_LIVE_VALUES, true),
        autoEcuScan = prefs.getBoolean(KEY_AUTO_ECU_SCAN, true),
        knownEcuAddresses = prefs.getString(KEY_KNOWN_ECUS, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty(),
        testMode = prefs.getBoolean(KEY_TEST_MODE, false),
        fakeScenario = runCatching {
            FakeScenario.valueOf(prefs.getString(KEY_SCENARIO, null) ?: FakeScenario.RECURRING.name)
        }.getOrDefault(FakeScenario.RECURRING),
        checkForUpdates = prefs.getBoolean(KEY_CHECK_UPDATES, true)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_VEHICLE, settings.vehicleName)
            .putString(KEY_SELECTED_VEHICLE_PROFILE, settings.selectedVehicleProfileId)
            .putBoolean(KEY_HEADERS, settings.headersOn)
            .putString(KEY_PROTOCOL, settings.protocol.code)
            .putBoolean(KEY_PROTOCOL_SWEEP, settings.autoProtocolSweep)
            .putBoolean(KEY_READ_VIN, settings.readVin)
            .putBoolean(KEY_MASK_VIN, settings.saveVinMasked)
            .putBoolean(KEY_FREEZE_FRAME, settings.readFreezeFrame)
            .putBoolean(KEY_LIVE_VALUES, settings.readLiveValues)
            .putBoolean(KEY_AUTO_ECU_SCAN, settings.autoEcuScan)
            .putString(KEY_KNOWN_ECUS, settings.knownEcuAddresses.joinToString(","))
            .putBoolean(KEY_TEST_MODE, settings.testMode)
            .putString(KEY_SCENARIO, settings.fakeScenario.name)
            .putBoolean(KEY_CHECK_UPDATES, settings.checkForUpdates)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "leafobd_settings"
        const val KEY_VEHICLE = "vehicle_name"
        const val KEY_SELECTED_VEHICLE_PROFILE = "selected_vehicle_profile"
        const val KEY_HEADERS = "headers_on"
        const val KEY_PROTOCOL = "obd_protocol"
        const val KEY_PROTOCOL_SWEEP = "obd_protocol_sweep"
        const val KEY_READ_VIN = "read_vin"
        const val KEY_MASK_VIN = "mask_vin"
        const val KEY_FREEZE_FRAME = "read_freeze_frame"
        const val KEY_LIVE_VALUES = "read_live_values"
        const val KEY_AUTO_ECU_SCAN = "auto_ecu_scan"
        const val KEY_KNOWN_ECUS = "known_ecu_addresses"
        const val KEY_TEST_MODE = "test_mode"
        const val KEY_SCENARIO = "fake_scenario"
        const val KEY_CHECK_UPDATES = "check_for_updates"
    }
}
