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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.log.LogExporter
import com.eunho.leafobd.obd.UdsDtcCode
import com.eunho.leafobd.obd.UdsDtcReadResult
import com.eunho.leafobd.ui.component.ExpandableRaw
import com.eunho.leafobd.ui.component.SafetyWarningCard
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.ui.theme.StatusFail
import com.eunho.leafobd.ui.theme.StatusWarn
import com.eunho.leafobd.viewmodel.MainUiState

/**
 * ECU별 오류코드 화면 (UDS).
 *
 * [EcuScanScreen] 에서 찾은 주소들에 `19 02` 표준 요청을 보내 실제 코드를 읽는다.
 */
@Composable
fun UdsDtcScreen(
    state: MainUiState,
    onRead: () -> Unit,
    onGoToScan: () -> Unit,
    onGoToClear: () -> Unit,
    onGoToDidScan: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenCode: (String) -> Unit
) {
    val context = LocalContext.current
    val addresses = state.ecuScan?.respondingAddresses?.takeIf { it.isNotEmpty() }
        ?: state.settings.knownEcuAddresses

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.simulated) SimulatedBadge()

        SectionCard("조회 대상") {
            StatusRow("응답한 ECU", "${addresses.size}곳")
            if (addresses.isNotEmpty()) {
                Text(
                    addresses.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "먼저 진단을 실행해 응답하는 ECU 를 찾아 주십시오.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onGoToScan, modifier = Modifier.fillMaxWidth()) {
                    Text("ECU 응답 스캔으로")
                }
            }
            if (state.ecuScan?.found != true && state.settings.knownEcuAddresses.isNotEmpty()) {
                Text(
                    "지난번 진단에서 찾아 둔 주소를 씁니다. " +
                        "응답이 없으면 차량 전원을 켜고(브레이크 없이 전원 버튼 2회) 다시 시도하십시오.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarn
                )
            }
            Text(
                "보내는 요청은 19 02(오류코드 읽기)와 22 F1 90(차대번호)뿐입니다. " +
                    "삭제·쓰기·보안 접근은 하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onRead,
            enabled = !state.udsRunning && addresses.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.udsRunning) "읽는 중…" else "오류코드 읽기")
        }

        state.udsProgress?.let { (current, total, address) ->
            SectionCard("진행 중") {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else current.toFloat() / total },
                    modifier = Modifier.fillMaxWidth()
                )
                StatusRow("진행", "$current / $total")
                StatusRow("현재 ECU", address)
            }
        }

        state.udsResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.allCodes.isEmpty()) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (result.allCodes.isEmpty()) "조회 결과 확인" else "오류코드 ${result.allCodes.size}건",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                    result.vin?.let { StatusRow("차대번호", it) }
                    StatusRow("걸린 시간", "${result.durationMs / 1000}초")
                }
            }

            if (result.activeCodes.isNotEmpty()) {
                SectionCard("확정 또는 현재 고장 상태인 코드") {
                    Text(
                        "아래 코드는 지금 고장 상태이거나 확정된 것입니다. 우선 확인하십시오.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusFail
                    )
                    result.activeCodes.forEach { UdsCodeRow(it, onOpenCode, highlight = true) }
                }
            }

            result.withCodes.forEach { ecuResult -> EcuResultCard(ecuResult, onOpenCode) }

            if (result.results.isNotEmpty()) {
                SectionCard("코드가 없는 ECU") {
                    val clean = result.results.filter { !it.hasCodes && it.complete }
                    if (clean.isEmpty()) {
                        Text("없음", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            clean.joinToString(", ") { it.ecu },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val text = buildReport(result.results, result.vin)
                        LogExporter.copyToClipboard(context, "Serotonin OBD ECU 오류코드", text)
                            .onSuccess { onMessage("ECU 오류코드를 클립보드에 복사했습니다.") }
                            .onFailure { onMessage(it.message ?: "복사에 실패했습니다.") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("결과 복사 (정비소 전달용)") }

                OutlinedButton(onClick = onGoToDidScan, modifier = Modifier.fillMaxWidth()) {
                    Text("ECU 데이터 스캔 (더 자세한 정보 찾기)")
                }

                OutlinedButton(onClick = onGoToClear, modifier = Modifier.fillMaxWidth()) {
                    Text("ECU 오류코드 삭제 화면으로")
                }
            }
        }

        SectionCard("코드 표기 읽는 법") {
            Text(
                "P33ED-00 형태로 표시됩니다.\n" +
                    "• 앞 5자리 — 표준 오류코드 (P=파워트레인, C=섀시, B=바디, U=통신)\n" +
                    "• 뒤 2자리 — 고장 유형(FTB). 같은 코드라도 원인을 세분합니다\n\n" +
                    "정비소에 전달할 때는 뒤 2자리까지 함께 알려 주십시오.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SafetyWarningCard()
    }
}

@Composable
private fun EcuResultCard(result: UdsDtcReadResult, onOpenCode: (String) -> Unit) {
    SectionCard("ECU ${result.ecu}") {
        StatusRow("코드", "${result.codes.size}건")
        result.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.truncated) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        result.codes.forEach { UdsCodeRow(it, onOpenCode) }
        ExpandableRaw(raw = result.raw)
    }
}

@Composable
private fun UdsCodeRow(code: UdsDtcCode, onOpenCode: (String) -> Unit, highlight: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    code.fullCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) StatusFail else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    code.systemLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { onOpenCode(code.fullCode) }) { Text("${code.fullCode} 해설·확인 순서") }
            if (code.statusLabels.isNotEmpty()) {
                Text(
                    code.statusLabels.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "ECU ${code.ecu ?: "-"} · 원본 ${code.rawBytes}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildReport(results: List<UdsDtcReadResult>, vin: String?): String = buildString {
    appendLine("[Serotonin OBD ECU 오류코드]")
    appendLine()
    vin?.let { appendLine("차대번호: $it") }
    appendLine()
    results.filter { it.hasCodes }.forEach { result ->
        appendLine("ECU ${result.ecu}")
        result.codes.forEach { code ->
            appendLine("  - ${code.fullCode}  [${code.systemLabel}]  ${code.statusLabels.joinToString(", ")}")
            appendLine("    원본 ${code.rawBytes}")
        }
        appendLine()
    }
    val clean = results.filter { !it.hasCodes && it.complete }.map { it.ecu }
    if (clean.isNotEmpty()) {
        appendLine("코드 없음: ${clean.joinToString(", ")}")
    }
    appendLine()
    appendLine("※ UDS 19 02 표준 요청으로 읽은 값입니다.")
    appendLine("※ 이 앱은 개인 진단 보조 도구이며 전문 진단을 대체하지 않습니다.")
}
