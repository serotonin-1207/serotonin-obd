package com.eunho.leafobd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** 연결 시도 결과. 실패 메시지는 항상 사용자에게 보여줄 수 있는 한국어다. */
sealed interface ConnectResult {
    data class Success(
        val transport: BluetoothElm327Transport,
        val deviceName: String
    ) : ConnectResult

    data class Failure(val message: String) : ConnectResult
}

/**
 * Bluetooth Classic(RFCOMM/SPP) 연결 관리자.
 *
 * 규칙
 *  - 권한을 확인하기 전에는 `bondedDevices`, `name`, `address`, 소켓 생성 API를 호출하지 않는다.
 *  - 모든 연결·입출력은 `Dispatchers.IO` 에서 수행한다.
 *  - 페어링은 앱에서 하지 않는다. 시스템 Bluetooth 설정에서 먼저 페어링한 장치만 사용한다.
 *  - 연결 실패 시 비공식 리플렉션 fallback 소켓을 쓰지 않는다. 공식 API만 사용한다.
 */
class BluetoothClassicManager(private val context: Context) {

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** 이 기기가 Bluetooth Classic 을 지원하는지. */
    val isSupported: Boolean
        get() = adapter != null

    /** Bluetooth 가 켜져 있는지. 권한 없이도 확인할 수 있다. */
    val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun hasPermission(): Boolean = BluetoothPermissions.hasAll(context)

    /** 현재 상태를 [BluetoothConnectionState] 로 판정한다(연결 시도 전 단계). */
    fun currentPrerequisiteState(): BluetoothConnectionState = when {
        !isSupported -> BluetoothConnectionState.Unsupported
        !hasPermission() -> BluetoothConnectionState.PermissionRequired
        !isEnabled -> BluetoothConnectionState.BluetoothOff
        else -> BluetoothConnectionState.Idle
    }

    /**
     * 페어링된 장치 목록.
     *
     * 권한이 없거나 Bluetooth 가 꺼져 있으면 빈 목록을 돌려준다(예외를 던지지 않는다).
     * OBD 어댑터로 보이는 장치를 앞쪽에 정렬한다.
     */
    @SuppressLint("MissingPermission") // 바로 위에서 hasPermission() 으로 확인한다.
    fun pairedDevices(): List<ObdBluetoothDevice> {
        if (!hasPermission() || !isEnabled) return emptyList()

        return runCatching {
            adapter?.bondedDevices.orEmpty().map { device ->
                ObdBluetoothDevice(
                    name = device.name ?: "이름 없는 장치",
                    address = device.address
                )
            }
        }.getOrDefault(emptyList())
            .sortedWith(
                compareByDescending<ObdBluetoothDevice> { it.looksLikeObdAdapter }
                    .thenBy { it.name }
            )
    }

    /**
     * RFCOMM 소켓으로 연결한다.
     *
     * @return 성공하면 열린 [BluetoothElm327Transport], 실패하면 한국어 사유.
     */
    @SuppressLint("MissingPermission") // 아래에서 hasPermission() 으로 먼저 확인한다.
    suspend fun connect(device: ObdBluetoothDevice): ConnectResult = withContext(Dispatchers.IO) {
        if (!isSupported) {
            return@withContext ConnectResult.Failure("이 기기는 Bluetooth Classic 을 지원하지 않습니다.")
        }
        if (!hasPermission()) {
            return@withContext ConnectResult.Failure(
                "Bluetooth 권한이 없습니다. 설정에서 '근처 기기' 권한을 허용해 주십시오."
            )
        }
        if (!isEnabled) {
            return@withContext ConnectResult.Failure("Bluetooth 가 꺼져 있습니다. 먼저 Bluetooth 를 켜 주십시오.")
        }

        val adapter = adapter
            ?: return@withContext ConnectResult.Failure("Bluetooth 어댑터를 사용할 수 없습니다.")

        val remote = runCatching { adapter.getRemoteDevice(device.address) }.getOrNull()
            ?: return@withContext ConnectResult.Failure(
                "장치 주소가 올바르지 않습니다. 시스템 Bluetooth 설정에서 다시 페어링해 주십시오."
            )

        // 검색이 돌고 있으면 RFCOMM 연결이 매우 느려지거나 실패한다.
        runCatching { if (adapter.isDiscovering) adapter.cancelDiscovery() }

        // 1차: 표준 보안 RFCOMM 소켓
        val secure = attempt(remote, device, insecure = false)
        if (secure is ConnectResult.Success) return@withContext secure

        // 2차: insecure RFCOMM 소켓
        //
        // ELM327 클론 어댑터 중에는 페어링 암호화를 요구하는 보안 소켓으로는
        // 연결되지 않고 insecure 소켓으로만 붙는 제품이 있다.
        // 이것은 리플렉션 우회가 아니라 Android 공식 공개 API 다.
        // 연결 대상은 사용자가 직접 시스템 설정에서 페어링한 장치뿐이다.
        val insecure = attempt(remote, device, insecure = true)
        if (insecure is ConnectResult.Success) return@withContext insecure

        // 두 방식 모두 실패하면 1차 시도의 사유를 보여 준다(원인에 더 가깝다).
        secure
    }

    @SuppressLint("MissingPermission") // 호출부에서 hasPermission() 으로 확인했다.
    private suspend fun attempt(
        remote: android.bluetooth.BluetoothDevice,
        device: ObdBluetoothDevice,
        insecure: Boolean
    ): ConnectResult {
        var socket: BluetoothSocket? = null
        return try {
            socket = if (insecure) {
                remote.createInsecureRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
            } else {
                remote.createRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
            }

            // socket.connect() 는 블로킹이므로 타임아웃을 별도로 건다.
            // 시간이 지나면 소켓을 닫아 블로킹을 강제로 끝낸다.
            val connected = withTimeoutOrNull(SppConstants.CONNECT_TIMEOUT_MS) {
                socket.connect()
                true
            }

            if (connected != true) {
                runCatching { socket.close() }
                return ConnectResult.Failure(
                    "연결 시간이 초과되었습니다(${SppConstants.CONNECT_TIMEOUT_MS / 1000}초).\n" +
                        "어댑터가 OBD 단자에 제대로 꽂혀 있는지, 다른 앱이 어댑터를 사용 중이 아닌지 확인해 주십시오."
                )
            }

            val transport = BluetoothElm327Transport(socket, device.name)
            transport.open()
            ConnectResult.Success(transport, device.name)
        } catch (e: SecurityException) {
            runCatching { socket?.close() }
            ConnectResult.Failure("Bluetooth 권한이 거부되었습니다. 설정에서 권한을 허용해 주십시오.")
        } catch (e: IOException) {
            runCatching { socket?.close() }
            ConnectResult.Failure(describeIoFailure(e))
        } catch (e: Exception) {
            runCatching { socket?.close() }
            ConnectResult.Failure("연결 중 알 수 없는 오류가 발생했습니다: ${e.message ?: e::class.java.simpleName}")
        }
    }

    /**
     * 대표적인 RFCOMM 실패 사유를 한국어로 설명한다.
     *
     * 어댑터·기기마다 메시지가 달라 문자열을 그대로 보여주면 도움이 되지 않는다.
     */
    private fun describeIoFailure(e: IOException): String {
        val raw = e.message.orEmpty()
        val hint = when {
            raw.contains("socket might closed", ignoreCase = true) ||
                raw.contains("read failed", ignoreCase = true) ->
                "어댑터가 응답하지 않았습니다. 어댑터를 뺐다가 다시 꽂고, 차량 전원을 켠 뒤 다시 시도해 주십시오."

            raw.contains("Connection refused", ignoreCase = true) ->
                "어댑터가 연결을 거부했습니다. 다른 앱(LeafSpy 등)이 어댑터를 사용 중인지 확인해 주십시오."

            raw.contains("Device or resource busy", ignoreCase = true) ->
                "어댑터가 다른 연결에 사용 중입니다. 다른 앱을 종료한 뒤 다시 시도해 주십시오."

            raw.contains("Service discovery failed", ignoreCase = true) ->
                "어댑터에서 SPP 서비스를 찾지 못했습니다. 시스템 Bluetooth 설정에서 페어링을 해제하고 다시 페어링해 주십시오."

            else ->
                "어댑터에 연결하지 못했습니다. 페어링 상태와 어댑터 전원(LED)을 확인해 주십시오."
        }
        return if (raw.isBlank()) hint else "$hint\n(상세: $raw)"
    }
}
