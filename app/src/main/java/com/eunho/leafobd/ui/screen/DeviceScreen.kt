package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.bluetooth.BluetoothConnectionState
import com.eunho.leafobd.bluetooth.ObdBluetoothDevice
import com.eunho.leafobd.bluetooth.label
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.viewmodel.MainUiState

@Composable
fun DeviceScreen(
    state: MainUiState,
    onConnect: () -> Unit,
    onConnectDevice: (ObdBluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.simulated) SimulatedBadge()

        SectionCard("연결 상태") {
            StatusRow("상태", state.connectionState.label)
            (state.connectionState as? BluetoothConnectionState.Error)?.let { error ->
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        when {
            state.simulated -> {
                SectionCard("테스트 모드") {
                    Text(
                        "테스트 모드가 켜져 있어 실제 Bluetooth 장치를 사용하지 않습니다.\n" +
                            "시나리오: ${state.fakeScenario.label}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                        Text("모의 어댑터 연결")
                    }
                }
            }

            !state.bluetoothSupported -> {
                SectionCard("사용할 수 없음") {
                    Text("이 기기는 Bluetooth Classic 을 지원하지 않아 OBD2 어댑터에 연결할 수 없습니다.")
                }
            }

            !state.permissionGranted -> {
                SectionCard("권한 필요") {
                    Text(
                        "OBD2 어댑터와 연결하려면 '근처 기기' 권한이 필요합니다.\n" +
                            "이 앱은 위치정보를 사용하지 않으며, 페어링된 어댑터와의 통신에만 권한을 씁니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("권한 요청")
                    }
                    OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("앱 설정 열기 (권한을 거부했다면)")
                    }
                }
            }

            !state.bluetoothEnabled -> {
                SectionCard("Bluetooth 꺼짐") {
                    Text("Bluetooth 가 꺼져 있습니다. 먼저 Bluetooth 를 켜 주십시오.")
                    OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Bluetooth 설정 열기")
                    }
                }
            }

            else -> {
                SectionCard("페어링된 장치") {
                    Text("연결할 장치의 버튼을 누르면 바로 연결을 시작합니다. 별도의 확인 단계는 없습니다.")
                    if (state.pairedDevices.isEmpty()) {
                        Text(
                            "페어링된 장치가 없습니다.\n" +
                                "시스템 Bluetooth 설정에서 어댑터를 먼저 페어링해 주십시오.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        state.pairedDevices.forEach { device ->
                            DeviceRow(
                                device = device,
                                selected = state.selectedDevice?.address == device.address,
                                connecting = state.connectionState is BluetoothConnectionState.Connecting,
                                connected = state.connectionState is BluetoothConnectionState.Connected,
                                onConnect = { onConnectDevice(device) }
                            )
                        }
                    }
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("목록 새로 고침")
                    }
                }
            }
        }

        if (state.connectionState is BluetoothConnectionState.Connected) {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text("연결 해제")
            }
        }

        SectionCard("새 장치 페어링 방법") {
            Text(
                "1. 차량을 안전한 상태(P, 주차 브레이크)로 둔다.\n" +
                    "2. 어댑터를 운전석 하단 OBD2 포트에 꽂는다.\n" +
                    "3. 안드로이드 설정 → 연결 → Bluetooth 를 연다.\n" +
                    "4. V-LINK, OBDII, Vgate, iCar Pro 등으로 표시되는 장치를 선택한다.\n" +
                    "5. PIN을 요구하면 판매자 설명을 먼저 확인한다. (앱은 PIN을 단정하지 않는다)\n" +
                    "6. 페어링이 끝나면 이 화면으로 돌아와 목록을 새로 고친다.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
                Text("시스템 Bluetooth 설정 열기")
            }
        }

        Text(
            text = "장치 주소는 마지막 2바이트만 표시하고 저장합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceRow(
    device: ObdBluetoothDevice,
    selected: Boolean,
    connecting: Boolean,
    connected: Boolean,
    onConnect: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = device.name + if (device.looksLikeObdAdapter) "  (OBD 어댑터로 추정)" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = device.maskedAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onConnect, enabled = !connecting && !(selected && connected), modifier = Modifier.fillMaxWidth()) {
                Text(when {
                    selected && connecting -> "연결 중…"
                    selected && connected -> "현재 연결됨"
                    else -> "이 장치 연결"
                })
            }
        }
    }
}
