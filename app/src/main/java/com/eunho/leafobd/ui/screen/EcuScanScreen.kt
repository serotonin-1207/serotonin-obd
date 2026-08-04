package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.bluetooth.isConnected
import com.eunho.leafobd.obd.EcuResponseKind
import com.eunho.leafobd.obd.EcuScanHit
import com.eunho.leafobd.ui.component.ExpandableRaw
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.ui.theme.StatusOk
import com.eunho.leafobd.ui.theme.StatusWarn
import com.eunho.leafobd.viewmodel.MainUiState

/**
 * ECU 응답 스캔 화면.
 *
 * 방송 주소(7DF)가 아니라 개별 ECU 주소를 직접 지정해 표준 UDS 읽기 요청을 보낸다.
 * 게이트웨이가 있는 차량에서 "정말 아무도 응답하지 않는가"를 확인하는 마지막 표준 수단이다.
 */
@Composable
fun EcuScanScreen(
    state: MainUiState,
    onScan: (Boolean) -> Unit,
    onGoToDtc: () -> Unit
) {
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
                "지금까지 앱은 요청을 방송 주소(7DF)로만 보냈습니다. " +
                    "\"거기 누구든 대답해\" 방식인데, 게이트웨이가 있는 차량은 이걸 통과시키지 않을 수 있습니다.\n\n" +
                    "이 스캔은 ECU 주소를 하나씩 직접 지정해서 부릅니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "보내는 요청 (모두 ISO 14229 국제 표준, 읽기 전용)\n" +
                    "• 3E 00 — 존재 확인\n" +
                    "• 22 F1 90 — 차대번호 읽기\n" +
                    "• 19 02 — 오류코드 읽기",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "진단 세션 변경, 보안 접근, 데이터 쓰기, 루틴 실행, ECU 리셋은 하지 않습니다. " +
                    "읽기만 합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("현재 상태") {
            StatusRow("어댑터 연결", if (state.connectionState.isConnected) "연결됨" else "연결 안 됨")
            StatusRow("프로토콜", state.protocol ?: "미확인")
        }

        val enabled = !state.ecuScanRunning &&
            (state.connectionState.isConnected || state.simulated)

        Button(
            onClick = { onScan(false) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("빠른 스캔 — 표준 주소 8개 (약 10초)") }

        OutlinedButton(
            onClick = { onScan(true) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("전체 스캔 — 진단 주소 240개 (수 분)") }

        state.ecuScanProgress?.let { progress ->
            SectionCard("진행 중") {
                LinearProgressIndicator(
                    progress = {
                        if (progress.total == 0) 0f
                        else progress.current.toFloat() / progress.total
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                StatusRow("확인한 주소", "${progress.current} / ${progress.total}")
                StatusRow("현재 주소", progress.address)
                StatusRow("응답 발견", "${progress.hits}건")
            }
        }

        state.ecuScan?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.found) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (result.found) "응답한 ECU를 찾았습니다" else "응답한 ECU가 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                    StatusRow("확인한 주소", "${result.scannedAddresses}개")
                    StatusRow("걸린 시간", "${result.durationMs / 1000}초")
                }
            }

            if (result.found) {
                Button(onClick = onGoToDtc, modifier = Modifier.fillMaxWidth()) {
                    Text("이 ECU들의 오류코드 읽기")
                }
            }

            if (result.hits.isNotEmpty()) {
                SectionCard("응답 내역") {
                    result.hits.forEach { hit -> HitRow(hit) }
                }
            }
        }

        SectionCard("결과를 어떻게 읽나") {
            Text(
                "• 정상 응답 — 그 주소로 진단이 가능합니다\n" +
                    "• 거부 응답(7F) — 서비스는 거절했지만 ECU가 거기 있다는 증거입니다. " +
                    "무응답과 완전히 다릅니다\n" +
                    "• 무응답 — 그 주소에 아무것도 없거나 게이트웨이가 막고 있습니다",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun HitRow(hit: EcuScanHit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "주소 ${hit.requestAddress}" + (hit.respondingId?.let { " → 응답 $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${hit.request.label} · ${hit.kind.label}",
                style = MaterialTheme.typography.bodySmall,
                color = when (hit.kind) {
                    EcuResponseKind.POSITIVE -> StatusOk
                    EcuResponseKind.NEGATIVE -> StatusWarn
                    EcuResponseKind.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            hit.negativeReason?.let {
                Text(
                    "사유: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExpandableRaw(raw = hit.raw)
        }
    }
}
