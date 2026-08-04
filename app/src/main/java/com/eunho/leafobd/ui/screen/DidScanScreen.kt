package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.log.LogExporter
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.viewmodel.MainUiState

/**
 * DID 스캔 화면.
 *
 * `22`(ReadDataByIdentifier)로 읽히는 데이터 항목을 찾는다.
 * 오류코드보다 구체적인 값(부품번호, 소프트웨어 버전, 제조사 데이터)을 확보해
 * 수리 업체에 전달하기 위한 것이다.
 */
@Composable
fun DidScanScreen(
    state: MainUiState,
    onScan: (Boolean) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val targets = state.ecuScan?.respondingAddresses.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.simulated) SimulatedBadge()

        SectionCard("이 기능이 하는 일") {
            Text(
                "ECU 가 어떤 데이터를 내어 주는지 항목 번호를 하나씩 물어봅니다.\n" +
                    "부품번호, 소프트웨어 버전, 제조사 고유 데이터 등이 나올 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "ISO 14229 의 22(ReadDataByIdentifier)는 읽기 전용 서비스입니다. " +
                    "어떤 값도 바꾸지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusRow("대상 ECU", "${targets.size}곳")
        }

        val enabled = !state.didScanRunning && targets.isNotEmpty()

        Button(onClick = { onScan(false) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("표준 항목 스캔 (F180~F1FF)")
        }
        OutlinedButton(onClick = { onScan(true) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("넓게 스캔 (제조사 구간 포함 · 오래 걸림)")
        }

        state.didProgress?.let { (current, total, label) ->
            SectionCard("진행 중") {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else current.toFloat() / total },
                    modifier = Modifier.fillMaxWidth()
                )
                StatusRow("진행", "$current / $total")
                StatusRow("현재", label)
            }
        }

        state.didScan?.let { result ->
            SectionCard("결과") {
                Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                StatusRow("확인한 항목", "${result.scannedCount}개")
                StatusRow("읽힌 항목", "${result.values.size}개")
                StatusRow("걸린 시간", "${result.durationMs / 1000}초")
            }

            result.byEcu.forEach { (ecu, values) ->
                SectionCard("ECU $ecu — ${values.size}개") {
                    values.forEach { value ->
                        Text(
                            value.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Default
                        )
                        value.asText?.let {
                            Text(
                                "  $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "  ${value.didHex}: ${value.rawHex}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (result.found) {
                OutlinedButton(
                    onClick = {
                        val text = buildString {
                            appendLine("[Serotonin OBD DID 스캔 결과]")
                            appendLine()
                            result.byEcu.forEach { (ecu, values) ->
                                appendLine("ECU $ecu")
                                values.forEach { v ->
                                    appendLine("  ${v.didHex} ${v.label}: ${v.rawHex}")
                                    v.asText?.let { appendLine("    -> $it") }
                                }
                                appendLine()
                            }
                        }
                        LogExporter.copyToClipboard(context, "Serotonin OBD DID", text)
                            .onSuccess { onMessage("DID 결과를 클립보드에 복사했습니다.") }
                            .onFailure { onMessage(it.message ?: "복사에 실패했습니다.") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("결과 복사 (정비소 전달용)") }
            }
        }

        SectionCard("참고") {
            Text(
                "읽히는 항목이 적어도 정상입니다. 제조사가 표준 항목을 많이 구현하지 않는 경우가 흔합니다.\n\n" +
                    "배터리 셀 전압 같은 값은 보통 제조사 고유 방식으로만 제공되며, " +
                    "이 앱은 검증되지 않은 제조사 전용 명령을 추측해서 보내지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
