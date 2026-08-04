package com.eunho.leafobd.bluetooth

/** 어댑터 연결 상태. UI는 이 상태만 보고 화면을 그린다. */
sealed interface BluetoothConnectionState {

    data object Idle : BluetoothConnectionState

    /** 런타임 권한이 없어 아무 API도 호출할 수 없는 상태. */
    data object PermissionRequired : BluetoothConnectionState

    /** 기기의 Bluetooth가 꺼져 있는 상태. */
    data object BluetoothOff : BluetoothConnectionState

    /** 이 기기가 Bluetooth Classic을 지원하지 않는 상태. */
    data object Unsupported : BluetoothConnectionState

    data object Connecting : BluetoothConnectionState

    data class Connected(val deviceName: String) : BluetoothConnectionState

    /** [message] 는 항상 사용자에게 그대로 보여줄 수 있는 한국어 문장이다. */
    data class Error(val message: String) : BluetoothConnectionState

    data object Disconnected : BluetoothConnectionState
}

/** 화면 표시용 라벨. */
val BluetoothConnectionState.label: String
    get() = when (this) {
        BluetoothConnectionState.Idle -> "대기 중"
        BluetoothConnectionState.PermissionRequired -> "권한 필요"
        BluetoothConnectionState.BluetoothOff -> "Bluetooth 꺼짐"
        BluetoothConnectionState.Unsupported -> "지원하지 않는 기기"
        BluetoothConnectionState.Connecting -> "연결 중…"
        is BluetoothConnectionState.Connected -> "연결됨 (${deviceName})"
        is BluetoothConnectionState.Error -> "오류"
        BluetoothConnectionState.Disconnected -> "연결 해제됨"
    }

val BluetoothConnectionState.isConnected: Boolean
    get() = this is BluetoothConnectionState.Connected
