package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.bluetooth.label
import com.eunho.leafobd.ui.component.SafetyWarningCard
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.navigation.Routes
import com.eunho.leafobd.viewmodel.MainUiState

@Composable
fun HomeScreen(state: MainUiState, onNavigate: (String) -> Unit, onRefresh: () -> Unit,
    onRequestPermission: () -> Unit, onOpenBluetoothSettings: () -> Unit,
    onOpenUrl: (String) -> Unit = {}, onDismissUpdate: () -> Unit = {}) {
    var professional by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.simulated) SimulatedBadge()
        Text("내 차를 이해하는 진단", style = MaterialTheme.typography.headlineMedium)
        Text("코드의 의미를 찾고, 측정값을 확인하고, 정비 기록으로 이어갑니다.", style = MaterialTheme.typography.bodyLarge)
        FilterChip(selected = professional, onClick = { professional = !professional }, label = { Text(if (professional) "정비사 보기" else "일반 사용자 보기 · 눌러서 전환") })
        state.updateAvailable?.let { update -> SectionCard("새 버전 ${update.versionName}") {
            Text(update.notes)
            TextButton(onClick = { onOpenUrl(update.downloadUrl) }) { Text("다운로드") }
            TextButton(onClick = onDismissUpdate) { Text("나중에") }
        } }
        SectionCard(state.vehicleName) {
            Text(state.selectedVehicleProfile?.description ?: "진단 전에 차량 프로필을 선택하세요.")
            Text(state.connectionState.label)
            Text("장치: ${state.selectedDevice?.name ?: "선택 전"}")
            Text("최근 진단: ${state.lastDiagnosisAt ?: "아직 없음"}")
            Button(onClick = { onNavigate(Routes.DEVICES) }, modifier = Modifier.fillMaxWidth()) { Text("어댑터 연결·변경") }
            Button(onClick = { onNavigate(Routes.VEHICLES) }, modifier = Modifier.fillMaxWidth()) { Text(if (state.selectedVehicleProfile == null) "진단 차량 선택" else "진단 차량 변경") }
            TextButton(onClick = { onNavigate(Routes.SETTINGS) }) { Text("진단 설정") }
        }
        HomeAction("01", "오류코드 알아보기", "연결 없이 검색 · 공개 근거와 적용 범위", { onNavigate(Routes.KNOWLEDGE) })
        HomeAction("02", "차량 진단", "저장·보류·영구 코드와 발생 당시 기록", { onNavigate(Routes.DIAGNOSIS) })
        HomeAction("03", "배터리 그래픽", "니로·리프 읽기 시험 · 측정 파일의 셀 전압·온도", { onNavigate(Routes.BATTERY) })
        HomeAction("04", "진단 기록", "정비 전후 기록 확인·공유", { onNavigate(Routes.LOGS) })
        HomeAction("05", "미해설 코드 대기함", "차량별 실제 기록에서 검증할 코드 정리", { onNavigate(Routes.UNKNOWN_CODES) })
        HomeAction("06", "내 차 지원 범위", "기능별 지원·부분 지원·실차 검증 상태", { onNavigate(Routes.SUPPORT) })
        SectionCard("지원 범위를 먼저 확인하세요") {
            Text("표준 OBD 기능도 차량마다 지원 항목이 다릅니다. 현대·기아 국내 차량과 전기차 배터리 직접 조회는 차종별 실차 검증이 필요합니다.")
            Text("응답 없음 ≠ 고장 없음 · 삭제 ≠ 수리", color = MaterialTheme.colorScheme.primary)
        }
        if (professional) SectionCard("정비사 도구") {
            Text("제조사별 명령과 응답은 적용 차량을 확인한 뒤 사용하세요.")
            listOf("ECU 오류코드 읽기" to Routes.UDS_DTC, "ECU 응답 스캔" to Routes.ECU_SCAN,
                "ECU 데이터 스캔" to Routes.DID_SCAN, "CAN 버스 확인" to Routes.BUS_MONITOR,
                "표준 코드 삭제" to Routes.CLEAR, "ECU 코드 삭제" to Routes.UDS_CLEAR).forEach { (title, route) ->
                OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(title) }
            }
        }
        if (!state.permissionGranted) Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Bluetooth 권한 허용") }
        if (state.permissionGranted && !state.bluetoothEnabled) OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) { Text("Bluetooth 켜기") }
        TextButton(onClick = onRefresh) { Text("연결 상태 새로 고침") }
        SafetyWarningCard()
        OutlinedButton(onClick = { onNavigate(Routes.HELP) }, modifier = Modifier.fillMaxWidth()) { Text("사용 절차") }
        Text("검색과 기록은 기기 안에서 처리합니다. 업데이트 확인과 사용자가 연 외부 문서에는 인터넷이 필요합니다.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeAction(number: String, title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
