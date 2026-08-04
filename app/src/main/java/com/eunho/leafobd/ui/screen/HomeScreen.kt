package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.bluetooth.label
import com.eunho.leafobd.ui.component.SafetyWarningCard
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.ui.navigation.Routes
import com.eunho.leafobd.ui.theme.StatusFail
import com.eunho.leafobd.ui.theme.StatusOk
import com.eunho.leafobd.viewmodel.MainUiState

@Composable
fun HomeScreen(
    state: MainUiState,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onDismissUpdate: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.simulated) SimulatedBadge()

        // 새 버전 안내. 다운로드는 사용자가 직접 눌러 브라우저로 넘어간다.
        state.updateAvailable?.let { update ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "새 버전이 있습니다 — ${update.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (update.notes.isNotBlank()) {
                        Text(
                            update.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpenUrl(update.downloadUrl) }) {
                            Text("다운로드")
                        }
                        OutlinedButton(onClick = onDismissUpdate) {
                            Text("나중에")
                        }
                    }
                }
            }
        }

        SectionCard("차량 및 어댑터") {
            StatusRow("차량", state.vehicleName)
            StatusRow("어댑터", "Vgate iCar Pro BT3.0")
            StatusRow("선택된 장치", state.selectedDevice?.name ?: "선택 안 됨")
            if (state.headersOn) StatusRow("CAN 헤더 표시", "켬 (ATH1)")
        }

        SectionCard("현재 상태") {
            StatusRow(
                "Bluetooth 지원",
                if (state.bluetoothSupported) "지원" else "미지원",
                valueColor = if (state.bluetoothSupported) StatusOk else StatusFail
            )
            StatusRow(
                "Bluetooth 권한",
                if (state.permissionGranted) "허용됨" else "필요함",
                valueColor = if (state.permissionGranted) StatusOk else StatusFail
            )
            StatusRow(
                "Bluetooth 전원",
                if (state.bluetoothEnabled) "켜짐" else "꺼짐",
                valueColor = if (state.bluetoothEnabled) StatusOk else StatusFail
            )
            StatusRow("어댑터 연결", state.connectionState.label)
            StatusRow(
                "ELM327 초기화",
                if (state.elmInitialized) "완료" else "미완료",
                valueColor = if (state.elmInitialized) StatusOk else MaterialTheme.colorScheme.onSurface
            )
            StatusRow("어댑터 정보", state.adapterInfo ?: "-")
            StatusRow("어댑터 전압", state.adapterVoltage ?: "-")
            StatusRow("최근 진단", state.lastDiagnosisAt ?: "없음")

            if (!state.permissionGranted) {
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Bluetooth 권한 허용하기")
                }
            }
            if (state.permissionGranted && !state.bluetoothEnabled) {
                OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Bluetooth 설정 열기")
                }
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("상태 새로 고침")
            }
        }

        SafetyWarningCard()

        Button(
            onClick = { onNavigate(Routes.DEVICES) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("어댑터 연결") }

        Button(
            onClick = { onNavigate(Routes.DIAGNOSIS) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("진단 시작") }

        OutlinedButton(
            onClick = { onNavigate(Routes.LOGS) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("진단 기록") }

        OutlinedButton(
            onClick = { onNavigate(Routes.ECU_SCAN) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ECU 응답 스캔 (읽기 전용)") }

        OutlinedButton(
            onClick = { onNavigate(Routes.UDS_DTC) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ECU 오류코드 읽기") }

        OutlinedButton(
            onClick = { onNavigate(Routes.UDS_CLEAR) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ECU 오류코드 삭제") }

        OutlinedButton(
            onClick = { onNavigate(Routes.BUS_MONITOR) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("CAN 버스 확인 (읽기 전용)") }

        OutlinedButton(
            onClick = { onNavigate(Routes.HELP) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("사용 절차 보기") }

        OutlinedButton(
            onClick = { onNavigate(Routes.SETTINGS) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("설정") }

        Text(
            text = "이 앱은 인터넷을 사용하지 않으며, 진단 데이터를 외부로 전송하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
