package com.eunho.leafobd.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 버전별 Bluetooth 런타임 권한 처리.
 *
 * - Android 12(API 31) 이상: `BLUETOOTH_CONNECT` 가 런타임 권한이다.
 *   페어링된 장치 목록 조회, 장치 이름/주소 읽기, 소켓 생성에 모두 필요하다.
 * - Android 11 이하: 설치 시 권한(`BLUETOOTH`, `BLUETOOTH_ADMIN`)이라 런타임 요청이 필요 없다.
 *
 * 이 앱은 **검색(discovery)을 하지 않으므로** `BLUETOOTH_SCAN` 을 요청하지 않는다.
 * 위치 권한(`ACCESS_FINE_LOCATION`)도 요청하지 않는다.
 */
object BluetoothPermissions {

    /** 런타임에 요청해야 하는 권한 목록. Android 11 이하에서는 빈 배열이다. */
    val required: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    /**
     * 필요한 권한이 모두 허용되었는지 확인한다.
     *
     * 중요: 이 함수가 true 를 돌려주기 전에는
     * `BluetoothAdapter.bondedDevices`, `BluetoothDevice.name`,
     * `createRfcommSocketToServiceRecord` 등을 호출하면 안 된다.
     * Android 12 이상에서 `SecurityException` 이 발생한다.
     */
    fun hasAll(context: Context): Boolean = required.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** 권한 거부 시 화면에 보여줄 설명. */
    const val RATIONALE: String =
        "OBD2 어댑터와 연결하려면 '근처 기기' 권한이 필요합니다.\n" +
            "이 앱은 위치정보를 사용하지 않으며, 페어링된 어댑터와의 통신에만 권한을 씁니다."
}
