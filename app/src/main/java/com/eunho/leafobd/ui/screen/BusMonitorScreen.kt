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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.bluetooth.isConnected
import com.eunho.leafobd.ui.component.ExpandableRaw
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.viewmodel.MainUiState

/**
 * CAN 버스 듣기 화면.
 *
 * 표준 OBD가 전부 `NO DATA` 일 때 "버스에 통신이 흐르고 있는가"를 확인한다.
 * 차량에 어떤 요청도 보내지 않고 **듣기만** 한다.
 */
@Composable
fun BusMonitorScreen(
    state: MainUiState,
    onRun: () -> Unit
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
                "어댑터를 수신 전용 모드(ATMA)로 두고 정해진 시간 동안 버스에 흐르는 " +
                    "CAN 프레임을 듣기만 합니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "• 차량에 어떤 요청도 보내지 않습니다\n" +
                    "• 어떤 값도 바꾸지 않습니다\n" +
                    "• 수신한 CAN ID와 프레임 수만 집계합니다",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "표준 OBD 조회가 전부 데이터 없음으로 나올 때, 차량이 깨어 있는지 " +
                    "아니면 통신 자체가 없는지 구분하는 데 씁니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("현재 상태") {
            StatusRow("어댑터 연결", if (state.connectionState.isConnected) "연결됨" else "연결 안 됨")
            StatusRow("프로토콜", state.protocol ?: "미확인")
        }

        Button(
            onClick = onRun,
            enabled = !state.canMonitorRunning &&
                (state.connectionState.isConnected || state.simulated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.canMonitorRunning) "듣는 중… (10초)" else "10초 동안 버스 듣기")
        }

        if (state.canMonitorRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.canMonitor?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.hasTraffic) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (result.hasTraffic) "통신 있음" else "통신 없음",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(result.interpretation, style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard("집계") {
                StatusRow("수신 프레임", "${result.totalFrames}건")
                StatusRow("CAN ID 종류", "${result.frames.size}종")
                StatusRow("듣기 시간", "${result.durationMs / 1000}초")

                result.frames.take(MAX_SHOWN).forEach { frame ->
                    StatusRow("ID ${frame.id}", "${frame.count}건")
                }
                if (result.frames.size > MAX_SHOWN) {
                    Text(
                        "그 외 ${result.frames.size - MAX_SHOWN}종은 원시 데이터에서 확인하십시오.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExpandableRaw(label = "원시 수신 데이터 보기", raw = result.raw)
            }
        }

        SectionCard("2018년 이후 Leaf 참고") {
            Text(
                "2018년 이후 Leaf(ZE1)는 OBD-II 포트가 CAN 게이트웨이로 분리되어 있어 " +
                    "브로드캐스트 트래픽이 포트까지 오지 않는 것으로 알려져 있습니다.\n\n" +
                    "따라서 이 차량에서 프레임이 0건으로 나오더라도 " +
                    "\"차량이 꺼져 있다\"는 결론으로 바로 이어지지 않습니다. " +
                    "게이트웨이가 막고 있는 것일 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private const val MAX_SHOWN = 20
