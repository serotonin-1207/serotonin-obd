package com.eunho.leafobd.ui.screen

import android.content.Context
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
import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.obd.FreezeFrame
import com.eunho.leafobd.ui.component.ExpandableRaw
import com.eunho.leafobd.ui.component.SafetyWarningCard
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.ui.component.StepDot
import com.eunho.leafobd.ui.component.stepStatusLabel
import com.eunho.leafobd.ui.theme.StatusFail
import com.eunho.leafobd.ui.theme.StatusWarn
import com.eunho.leafobd.viewmodel.DiagnosisStep
import com.eunho.leafobd.viewmodel.MainUiState
import com.eunho.leafobd.viewmodel.ModeResult

@Composable
fun DiagnosisScreen(
    state: MainUiState,
    onRunDiagnosis: () -> Unit,
    onCopySummary: () -> String,
    onMessage: (String) -> Unit,
    onGoToClear: () -> Unit,
    onGoToLogs: () -> Unit,
    onGoToBusMonitor: () -> Unit,
    onGoToEcuScan: () -> Unit
) {
    val context: Context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.simulated) SimulatedBadge()

        Button(
            onClick = onRunDiagnosis,
            enabled = !state.diagnosisRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.diagnosisRunning) "진단 진행 중…" else "진단 실행")
        }

        if (state.diagnosisRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        SectionCard("진행 단계") {
            state.steps.forEach { step -> StepRow(step) }
        }

        if (state.adapterInfo != null || state.adapterVoltage != null) {
            SectionCard("어댑터") {
                StatusRow("ATI (식별정보)", state.adapterInfo ?: "-")
                StatusRow("ATRV (전압)", state.adapterVoltage ?: "-")
                StatusRow("연결 프로토콜", state.protocol ?: "-")
                state.voltageHint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = StatusWarn)
                }
                Text(
                    "어댑터가 보고하는 식별 문자열만으로 정품 여부를 판단할 수 없습니다. " +
                        "전압은 참고값이며 멀티미터 측정을 대체하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.monitorStatus != null || state.supportedPids != null || state.vin != null) {
            SectionCard("차량 정보") {
                StatusRow("차량", state.vehicleName)
                state.vin?.let { StatusRow("차대번호(VIN)", it) }
                state.monitorStatus?.let { status ->
                    StatusRow(
                        "경고등(MIL)",
                        status.milLabel,
                        valueColor = if (status.milOn) StatusFail else MaterialTheme.colorScheme.onSurface
                    )
                    StatusRow("ECU 보고 코드 수", "${status.dtcCount}건")
                    status.ecu?.let { StatusRow("응답 ECU", it) }
                    ExpandableRaw(label = "0101 원시 응답 보기", raw = status.rawHex)
                }
                state.supportedPids?.let { StatusRow("지원 PID", "${it.ids.size}개") }
                if (state.vin != null) {
                    Text(
                        "차대번호는 화면에만 전체가 표시됩니다. 파일 저장 시 마스킹 여부는 설정에서 바꿀 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.liveValues.isNotEmpty()) {
            SectionCard("실시간 값 (Mode 01)") {
                state.liveValues.forEach { value ->
                    StatusRow(value.label, value.display)
                }
                Text(
                    "차량이 지원한다고 보고한 표준 PID만 표시합니다. " +
                        "전기차에는 엔진 관련 항목이 아예 없을 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.freezeFrame?.let { frame -> FreezeFrameCard(frame) }

        state.modeResults.forEach { result -> ModeResultCard(result) }

        if (state.modeResults.isNotEmpty()) {
            SectionCard("로그") {
                StatusRow("저장 상태", if (state.sessionSaved) "저장 완료" else "저장되지 않음")
                StatusRow("파일 이름", state.savedSessionName ?: "-")
                Text(
                    "저장 위치: 앱 내부 저장소 (진단 기록 화면에서 TXT·JSON 내보내기 가능)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        LogExporter.copyToClipboard(context, "Serotonin OBD 진단 결과", onCopySummary())
                            .onSuccess { onMessage("진단 결과 요약을 클립보드에 복사했습니다.") }
                            .onFailure { onMessage(it.message ?: "복사에 실패했습니다.") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("결과 요약 복사") }

                ExpandableRaw(label = "전체 원시 로그 보기", raw = state.rawLog)
            }
        }

        if (state.sessionSaved) {
            OutlinedButton(onClick = onGoToClear, modifier = Modifier.fillMaxWidth()) {
                Text("오류코드 삭제 화면으로")
            }
        }

        OutlinedButton(onClick = onGoToLogs, modifier = Modifier.fillMaxWidth()) {
            Text("진단 기록 보기")
        }

        // 표준 조회가 전부 데이터 없음이면 버스 자체를 확인해 볼 수 있게 안내한다.
        if (state.modeResults.isNotEmpty() && state.dtcs.isEmpty()) {
            SectionCard("데이터가 하나도 나오지 않았다면") {
                Text(
                    "차량이 표준 OBD 요청에 응답하지 않는 상태입니다. " +
                        "CAN 버스에 통신이 흐르고 있는지 직접 들어 보면 원인을 좁힐 수 있습니다.\n" +
                        "듣기만 하며 차량에 아무것도 보내지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onGoToEcuScan, modifier = Modifier.fillMaxWidth()) {
                    Text("ECU 응답 스캔 (읽기 전용)")
                }
                OutlinedButton(onClick = onGoToBusMonitor, modifier = Modifier.fillMaxWidth()) {
                    Text("CAN 버스 확인 (읽기 전용)")
                }
            }
        }

        SafetyWarningCard()
    }
}

@Composable
private fun StepRow(step: DiagnosisStep) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepDot(step.status)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${step.order}. ${step.label}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stepStatusLabel(step.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        step.detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp)
            )
        }
        step.raw?.let { ExpandableRaw(raw = it, modifier = Modifier.padding(start = 14.dp)) }
    }
}

@Composable
private fun FreezeFrameCard(frame: FreezeFrame) {
    SectionCard("프리즈 프레임 (Mode 02)") {
        Text(
            "고장이 확정되던 순간의 스냅샷입니다. " +
                "오류코드를 삭제하면 차량에서는 함께 사라지므로, 이 기록이 유일한 사본이 됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = StatusWarn
        )

        if (!frame.hasData) {
            Text(
                "읽어 온 값이 없습니다. 차량에 저장된 프리즈 프레임이 없거나 통신이 실패한 경우입니다. " +
                    "전기차에서는 값이 없는 것이 흔한 결과이며 오류가 아닙니다. " +
                    "아래 원시 응답으로 어느 쪽인지 확인하십시오.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            StatusRow("원인 DTC", frame.triggerDtc ?: "확인 불가")
            frame.values.forEach { value -> StatusRow(value.label, value.display) }
        }

        if (frame.rawResponses.isNotEmpty()) {
            ExpandableRaw(
                label = "프리즈 프레임 원시 응답 보기",
                raw = frame.rawResponses.entries.joinToString("\n") { "${it.key} -> ${it.value}" }
            )
        }
    }
}

@Composable
private fun ModeResultCard(result: ModeResult) {
    SectionCard("${result.mode.label} — ${result.mode.command}") {
        StatusRow("응답 상태", result.statusLabel)
        result.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result.codes.isEmpty()) {
            Text("검출된 코드가 없습니다.", style = MaterialTheme.typography.bodyMedium)
        } else {
            result.codes.forEach { DtcRow(it) }
        }
        ExpandableRaw(raw = result.raw)
    }
}

@Composable
fun DtcRow(code: DtcCode, highlight: Boolean = false) {
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
                    text = code.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) StatusFail else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "[${code.status.label}] ${code.systemLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "원본 바이트: ${code.rawBytes} · 출처: ${code.source.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            code.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (code.needsAttention) {
                Text(
                    text = "섀시·바디·통신 계통 코드입니다. 임의로 무시하지 말고 전문 점검을 받으십시오.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarn
                )
            }
        }
    }
}
