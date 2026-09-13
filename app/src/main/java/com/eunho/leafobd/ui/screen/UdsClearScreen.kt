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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.obd.UdsDtcCode
import com.eunho.leafobd.ui.component.ExpandableRaw
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.ui.theme.StatusFail
import com.eunho.leafobd.ui.theme.StatusOk
import com.eunho.leafobd.viewmodel.MainUiState
import com.eunho.leafobd.viewmodel.UdsClearConfirmation

/**
 * ECU 오류코드 삭제 화면 (UDS 14).
 *
 * 표준 OBD 삭제 화면과 같은 원칙: 읽고 저장한 뒤에만, 이중 확인 후, 1회만.
 */
@Composable
fun UdsClearScreen(
    state: MainUiState,
    onToggleCheck: (Int, Boolean) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onClear: () -> Unit,
    onToggleTarget: (String) -> Unit = {},
    onExtendedSessionChange: (Boolean) -> Unit = {}
) {
    val eligibility = state.udsClearEligibility
    val all = state.ecuScan?.respondingAddresses?.takeIf { it.isNotEmpty() }
        ?: state.settings.knownEcuAddresses
    val targets = if (state.udsClearTargets.isEmpty()) all
    else all.filter { it in state.udsClearTargets }
    val codesByEcu = state.udsResult?.results.orEmpty().associate { it.ecu to it.codes.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.simulated) SimulatedBadge()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "삭제는 수리가 아닙니다",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    UdsClearConfirmation.WARNING,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        SectionCard("서비스센터 안내로 삭제하는 경우") {
            Text(UdsClearConfirmation.SERVICE_CENTER_NOTICE, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.hasHighVoltageCodes) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "고전압 계통 코드가 포함되어 있습니다",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        UdsClearConfirmation.HIGH_VOLTAGE_WARNING,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        SectionCard("삭제 대상 ECU") {
            StatusRow("대상", "${targets.size} / ${all.size}곳")
            StatusRow("현재 코드", "${state.udsResult?.allCodes?.size ?: 0}건")
            Text(
                "아무것도 고르지 않으면 전체가 대상입니다. " +
                    "특정 ECU 만 다시 시도하려면 그 ECU 만 선택하십시오.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            all.forEach { address ->
                val selected = address in state.udsClearTargets
                val n = codesByEcu[address] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selected || state.udsClearTargets.isEmpty(),
                        onCheckedChange = { onToggleTarget(address) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$address" + if (n > 0) "  — 코드 ${n}건" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (n > 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        SectionCard("거부하는 ECU 대응") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.udsUseExtendedSession,
                    onCheckedChange = onExtendedSessionChange
                )
                Spacer(Modifier.width(4.dp))
                Text("확장 진단 세션 후 재시도", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "일부 ECU 는 기본 상태에서 삭제를 거부합니다. " +
                    "거부한 ECU 에만 확장 진단 세션(10 03)을 요청한 뒤 한 번 더 시도합니다.\n" +
                    "ISO 14229 표준 요청이며, 값을 쓰거나 잠금을 푸는 명령이 아닙니다. " +
                    "세션은 잠시 뒤 자동으로 원래대로 돌아갑니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("보내는 명령") {
            Text(
                "보내는 명령은 14 FF FF FF (ISO 14229 ClearDiagnosticInformation) 하나뿐입니다. " +
                    "접촉기 조작이나 충전 강제 시작 같은 명령은 이 앱에 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("사전 조건") {
            ConditionRow2("어댑터 연결", eligibility.connected)
            ConditionRow2("응답 ECU 확보", eligibility.ecuFound)
            ConditionRow2("오류코드 읽기 완료", eligibility.codesRead)
            ConditionRow2("결과 파일 저장", eligibility.logSaved)
            ConditionRow2("안전 확인 선택", eligibility.safetyChecked)
            ConditionRow2("확인 문구 입력", eligibility.confirmationTyped)
            if (eligibility.alreadyCleared) {
                Text(
                    "이번 세션에서 이미 삭제했습니다. 반복 삭제는 허용하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusFail
                )
            }
        }

        SectionCard("안전 확인") {
            UdsClearConfirmation.CHECKLIST.forEachIndexed { index, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.udsSafetyChecks.getOrElse(index) { false },
                        onCheckedChange = { onToggleCheck(index, it) },
                        enabled = !state.udsClearAttempted
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionCard("확인 문구 입력") {
            Text("아래 문구를 그대로 입력해야 삭제 버튼이 활성화됩니다.", style = MaterialTheme.typography.bodyMedium)
            Text(
                UdsClearConfirmation.PHRASE,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = state.udsConfirmationInput,
                onValueChange = onConfirmationChange,
                singleLine = true,
                enabled = !state.udsClearAttempted,
                label = { Text("확인 문구") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!eligibility.eligible && eligibility.unmetReasons.isNotEmpty()) {
            SectionCard("아직 삭제할 수 없는 이유") {
                eligibility.unmetReasons.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Button(
            onClick = onClear,
            enabled = eligibility.eligible && !state.udsClearRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.udsClearRunning) "삭제 및 재조회 중…" else "ECU 오류코드 삭제")
        }

        if (state.udsClearRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "삭제 후 3초 기다렸다가 자동으로 다시 읽어 전후를 비교합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.udsClearResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.hasRecurrence) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (result.hasRecurrence) "코드가 다시 나타났습니다" else "삭제 완료",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard("ECU별 응답") {
                result.outcomes.forEach { outcome ->
                    StatusRow(
                        "ECU ${outcome.ecu}",
                        if (outcome.accepted) "수락" else "거부",
                        valueColor = if (outcome.accepted) StatusOk else StatusFail
                    )
                    if (!outcome.accepted) {
                        Text(
                            outcome.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpandableRaw(raw = outcome.raw)
                }
            }

            CodeGroup2("사라진 코드", result.cleared)
            CodeGroup2("여전히 현재 고장 상태", result.stillFailing, highlight = true)
            CodeGroup2("남았지만 현재 고장은 아님", result.remaining.filterNot { it.currentlyFailing })
            CodeGroup2("새로 나타난 코드", result.appeared, highlight = true)

            if (result.statusChanged.isNotEmpty()) {
                SectionCard("상태가 바뀐 코드") {
                    Text(
                        "코드가 사라지지 않았어도 상태 비트가 달라졌다면 의미 있는 변화입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    result.statusChanged.forEach { (code, was, now) ->
                        StatusRow(code.fullCode, "%02X → %02X".format(was, now))
                        Text(
                            "  ${code.statusLabels.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (result.stillFailing.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "ECU 가 지금도 고장을 감지하고 있습니다",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "다음 코드는 지운 직후 다시 세워졌습니다: " +
                                result.stillFailing.joinToString(", ") { it.fullCode } + "\n\n" +
                                "원인이 그대로 남아 있다는 뜻입니다. 반복해서 지워도 결과는 같습니다. " +
                                "차량이 충전이나 주행을 막고 있다면 이 코드가 이유일 가능성이 큽니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                SectionCard("삭제 후 절차") {
                    Text(
                        "1. 차량 전원을 완전히 끕니다.\n" +
                            "2. 스마트키를 차량에서 멀리 둡니다.\n" +
                            "3. 수 분 후 차량을 다시 켭니다.\n" +
                            "4. 오류코드를 다시 읽습니다.\n" +
                            "5. 충전을 시도하기 전에 코드가 재발했는지 확인합니다.\n\n" +
                            "충전 중에는 차량 곁을 떠나지 마시고, 타는 냄새·연기·비정상적인 열이 " +
                            "느껴지면 즉시 충전을 중단하고 차량에서 떨어지십시오.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ConditionRow2(label: String, met: Boolean) {
    StatusRow(
        label = label,
        value = if (met) "충족" else "미충족",
        valueColor = if (met) StatusOk else StatusFail
    )
}

@Composable
private fun CodeGroup2(title: String, codes: List<UdsDtcCode>, highlight: Boolean = false) {
    SectionCard(title) {
        if (codes.isEmpty()) {
            Text("없음", style = MaterialTheme.typography.bodyMedium)
        } else {
            codes.forEach { code ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        code.fullCode,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (highlight) StatusFail else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ECU ${code.ecu ?: "-"} · ${code.systemLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
