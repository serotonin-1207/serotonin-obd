package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.BuildConfig
import com.eunho.leafobd.data.AppInfo
import com.eunho.leafobd.elm327.FakeScenario
import com.eunho.leafobd.nissan.NissanLeafService
import com.eunho.leafobd.obd.ObdProtocol
import com.eunho.leafobd.ui.theme.StatusWarn
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.viewmodel.MainUiState

@Composable
fun SettingsScreen(
    state: MainUiState,
    onTestModeChange: (Boolean) -> Unit,
    onScenarioChange: (FakeScenario) -> Unit,
    onManageVehicles: () -> Unit,
    onProtocolChange: (ObdProtocol) -> Unit,
    onAutoSweepChange: (Boolean) -> Unit,
    onHeadersChange: (Boolean) -> Unit,
    onReadVinChange: (Boolean) -> Unit,
    onSaveVinMaskedChange: (Boolean) -> Unit,
    onReadFreezeFrameChange: (Boolean) -> Unit,
    onReadLiveValuesChange: (Boolean) -> Unit,
    onCheckUpdatesChange: (Boolean) -> Unit,
    onCheckUpdateNow: () -> Unit,
    onUpdateKnowledgePack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenHelp: () -> Unit,
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

        SectionCard("진단 대상 차량") {
            Text(state.selectedVehicleProfile?.let { "${it.alias}\n${it.description}" } ?: "선택된 차량이 없습니다.")
            OutlinedButton(onClick = onManageVehicles, modifier = Modifier.fillMaxWidth()) { Text("차량 프로필 관리") }
            Text(
                "이 앱은 표준 OBD-II 명령만 사용하므로 OBD-II 규격을 따르는 다른 차량에서도 " +
                    "오류코드를 읽을 수 있습니다. 다만 ABS·에어백·TPMS 같은 제조사 전용 계통은 " +
                    "어느 차량에서도 표준 명령으로 읽을 수 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("연결 프로토콜") {
            Text(
                "차량과 통신할 방식입니다. 보통 자동 선택으로 충분하지만, " +
                    "어댑터가 자동 탐색에 실패해 UNABLE TO CONNECT 만 나오면 직접 지정해 보십시오.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.protocolProbe?.let { probe ->
                StatusRow(
                    "지난 진단 결과",
                    probe.protocol?.let { "${it.label} · ${probe.outcome.label}" } ?: "통신 실패"
                )
                if (probe.foundBySweep && probe.protocol != null) {
                    Text(
                        "자동 선택은 실패했지만 ${probe.protocol.label} 로 통신됐습니다. " +
                            "아래에서 이 프로토콜로 고정하면 진단이 훨씬 빨라집니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarn
                    )
                }
            }

            SettingSwitch(
                checked = state.settings.autoProtocolSweep,
                onCheckedChange = onAutoSweepChange,
                title = "실패 시 순차 시도",
                description = "선택한 프로토콜로 통신되지 않으면 CAN 계열부터 차례로 시도합니다. " +
                    "시간이 조금 더 걸리지만 원인을 좁힐 수 있습니다."
            )

            ObdProtocol.SELECTABLE.forEach { protocol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.settings.protocol == protocol,
                            onClick = { onProtocolChange(protocol) }
                        ),
                    verticalAlignment = Alignment.Top
                ) {
                    RadioButton(
                        selected = state.settings.protocol == protocol,
                        onClick = { onProtocolChange(protocol) }
                    )
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "${protocol.code}. ${protocol.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (state.settings.protocol == protocol) FontWeight.Bold
                            else FontWeight.Normal
                        )
                        Text(
                            protocol.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SectionCard("조회 항목") {
            SettingSwitch(
                checked = state.settings.readFreezeFrame,
                onCheckedChange = onReadFreezeFrameChange,
                title = "프리즈 프레임 읽기 (Mode 02)",
                description = "고장이 확정되던 순간의 스냅샷입니다. " +
                    "오류코드를 삭제하면 차량에서 함께 사라지므로, 켜 두는 것을 권장합니다."
            )
            SettingSwitch(
                checked = state.settings.readLiveValues,
                onCheckedChange = onReadLiveValuesChange,
                title = "실시간 값 읽기 (Mode 01)",
                description = "차량이 지원한다고 보고한 PID만 조회합니다. 진단 시간이 조금 길어집니다."
            )
            SettingSwitch(
                checked = state.settings.headersOn,
                onCheckedChange = onHeadersChange,
                title = "CAN 헤더 표시 (ATH1)",
                description = "어느 ECU가 응답했는지 함께 기록합니다. " +
                    "다중 프레임 응답을 앱이 직접 재조립하므로, 결과가 이상하면 꺼서 다시 시도해 보십시오."
            )
        }

        SectionCard("차대번호(VIN)") {
            SettingSwitch(
                checked = state.settings.readVin,
                onCheckedChange = onReadVinChange,
                title = "VIN 읽기 (Mode 09)",
                description = "통신이 정상인지 확인하는 데 유용합니다. " +
                    "VIN 은 차량을 특정할 수 있는 식별정보이므로 기본값은 꺼짐입니다."
            )
            if (state.settings.readVin) {
                SettingSwitch(
                    checked = state.settings.saveVinMasked,
                    onCheckedChange = onSaveVinMaskedChange,
                    title = "파일 저장 시 VIN 마스킹",
                    description = "앞 3자리와 뒤 4자리만 남기고 가립니다. " +
                        "로그를 남에게 보낼 계획이라면 반드시 켜 두십시오."
                )
            }
            state.vin?.let {
                StatusRow("이번 진단에서 읽은 VIN", it)
                Text(
                    "화면에만 전체를 표시합니다." +
                        if (state.settings.saveVinMasked) " 파일에는 마스킹된 값이 저장됩니다." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard("테스트 모드") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.testMode, onCheckedChange = onTestModeChange)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (state.testMode) "켜짐 — 모의 데이터 사용 중" else "꺼짐 — 실제 어댑터 사용",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                "실제 어댑터 없이 화면 흐름, 파서, 로그 저장, 삭제 조건을 확인할 수 있습니다.\n" +
                    "테스트 모드에서 표시되는 코드는 모두 가짜이며 실제 차량 상태와 무관합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.testMode) {
                Text("시나리오", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                FakeScenario.entries.forEach { scenario ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.fakeScenario == scenario,
                                onClick = { onScenarioChange(scenario) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.fakeScenario == scenario,
                            onClick = { onScenarioChange(scenario) }
                        )
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(scenario.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                scenario.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        SectionCard("Nissan Leaf 전용 진단") {
            StatusRow("상태", if (NissanLeafService.ENABLED) "사용 가능" else "비활성")
            Text(
                NissanLeafService.DISABLED_REASON,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SectionCard("개인정보 및 네트워크") {
            StatusRow("인터넷 사용", "앱·오류코드 데이터 업데이트만")
            StatusRow("위치 권한", "사용하지 않음")
            StatusRow("광고·분석 SDK", "없음")
            StatusRow("Bluetooth 주소 저장", "마지막 2바이트만")
            Text(
                "차량 진단과 검색은 오프라인으로 동작합니다. 진단 데이터는 기기 안에만 저장되며, " +
                    "업데이트 요청에 차량 정보나 진단 기록을 포함하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                Text("앱 권한 설정 열기")
            }
        }

        SectionCard("업데이트") {
            SettingSwitch(
                checked = state.settings.checkForUpdates,
                onCheckedChange = onCheckUpdatesChange,
                title = "새 버전 확인",
                description = "앱을 열 때 새 버전이 있는지 확인해 알려 줍니다. " +
                    "오류코드 데이터 갱신은 아래 버튼을 눌렀을 때만 실행됩니다. 진단 데이터는 전송하지 않습니다. " +
                    "끄면 인터넷을 전혀 사용하지 않습니다."
            )
            state.updateAvailable?.let { update ->
                StatusRow("새 버전", update.versionName)
                OutlinedButton(
                    onClick = { onOpenUrl(update.downloadUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("새 버전 다운로드") }
            }
            OutlinedButton(
                onClick = onCheckUpdateNow,
                enabled = state.settings.checkForUpdates && !state.updateChecking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.updateChecking) "확인 중…" else "지금 확인")
            }
            StatusRow("오류코드 데이터", "${state.knowledgePackVersion} · r${state.knowledgePackRevision}")
            StatusRow("현재 출처", state.knowledgePackSource)
            OutlinedButton(
                onClick = onUpdateKnowledgePack,
                enabled = state.settings.checkForUpdates && !state.knowledgePackUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.knowledgePackUpdating) "데이터 확인 중…" else "오류코드 데이터 업데이트")
            }
            Text(
                "GitHub의 공개 데이터만 내려받습니다. 서명·해시·출처 검사를 모두 통과해야 적용되며 실패하면 현재 데이터를 유지합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("앱 정보") {
            StatusRow("앱 이름", AppInfo.APP_NAME)
            StatusRow("버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            StatusRow("패키지", BuildConfig.APPLICATION_ID)
            StatusRow("제작자", AppInfo.AUTHOR)
            StatusRow("문의", AppInfo.AUTHOR_EMAIL)
            StatusRow("대상 차량", state.settings.vehicleName)
            OutlinedButton(
                onClick = { onOpenUrl(AppInfo.REPO_URL) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("소스 코드 (GitHub)") }
            OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) {
                Text("사용 절차 보기")
            }
        }

        Text(
            "이 앱은 개인 진단 보조 도구이며 정비소의 전문 진단을 대체하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 제목 + 설명이 있는 스위치 한 줄. */
@Composable
private fun SettingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
