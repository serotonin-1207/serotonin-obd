package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.log.LogExporter
import com.eunho.leafobd.log.UnknownCodeQueue
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.viewmodel.MainUiState

@Composable
fun UnknownCodeQueueScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onOpenCode: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val result = state.unknownCodeQueue
    var vehicleFilter by remember(result) { mutableStateOf<String?>(null) }
    var familyFilter by remember(result) { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onRefresh() }
    val candidates = result?.candidates.orEmpty()
        .filter { vehicleFilter == null || it.vehicleLabel == vehicleFilter }
        .filter { familyFilter == null || it.coverageId == familyFilter }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("실제 기록에서 검증할 코드를 찾습니다", style = MaterialTheme.typography.headlineSmall)
        Text("차량별로 분류된 저장 기록만 사용합니다. 미해설 코드는 고장 원인이나 위험도가 확인됐다는 뜻이 아닙니다.")
        SectionCard("분석 범위") {
            StatusRow("등록 해설", "${com.eunho.leafobd.data.DiagnosticKnowledge.entries.size}건")
            StatusRow("분석 상태", if (state.unknownCodeQueueLoading) "기록 확인 중…" else "완료")
            result?.let {
                StatusRow("분석한 기록", "${it.scannedRecords}건")
                StatusRow("미해설 후보", "${it.candidates.size}건")
                StatusRow("미분류 제외", "${it.unclassifiedRecords}건")
                StatusRow("계열 미저장 기존 기록", "${it.uncataloguedRecords}건")
                StatusRow("손상·지원불가 제외", "${it.skippedRecords}건")
                Text("같은 코드라도 차량과 ECU가 다르면 별도 후보로 유지합니다. 같은 세션의 중복 수신은 반복 진단 횟수로 세지 않습니다.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onRefresh, enabled = !state.unknownCodeQueueLoading, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.unknownCodeQueueLoading) "분석 중…" else "저장 기록 다시 분석")
            }
        }

        if (result != null) {
            if (result.familySummaries.isNotEmpty()) SectionCard("모델 계열별 후보") {
                FilterChip(selected = familyFilter == null, onClick = { familyFilter = null }, label = { Text("전체 계열") })
                result.familySummaries.forEach { family ->
                    FilterChip(selected = familyFilter == family.coverageId, onClick = { familyFilter = family.coverageId },
                        label = { Text("${family.coverageLabel} · 후보 ${family.candidateCount}") })
                    Text("차량 ${family.vehicleCount}대 · 진단 기록 ${family.recordCount}건 · 코드 수신 세션 합계 ${family.codeSessionCount}건", style = MaterialTheme.typography.bodySmall)
                    Text("프로토콜 식별 ${family.protocolObservedCount}/${family.protocolAssessedCount} · 표준 데이터 관찰 ${family.standardDataObservedCount}/${family.standardDataAssessedCount} · 제조사 ECU 응답 관찰 ${family.udsResponseObservedCount}/${family.udsResponseAssessedCount}", style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionCard("차량 필터") {
                FilterChip(selected = vehicleFilter == null, onClick = { vehicleFilter = null }, label = { Text("전체 ${result.candidates.size}") })
                result.candidates.map { it.vehicleLabel }.distinct().sorted().forEach { label ->
                    val count = result.candidates.count { it.vehicleLabel == label }
                    FilterChip(selected = vehicleFilter == label, onClick = { vehicleFilter = label }, label = { Text("$label $count") })
                }
            }
            SectionCard("검증용 목록 내보내기") {
                Text("차량 별칭·VIN·Bluetooth 주소·원시 응답과 정확한 시각을 제외한 텍스트만 복사합니다.")
                OutlinedButton(onClick = {
                    LogExporter.copyToClipboard(context, "미해설 오류코드 검증 후보", UnknownCodeQueue.export(result))
                        .onSuccess { onMessage("개인정보를 제외한 검증 후보를 복사했습니다.") }
                        .onFailure { onMessage("검증 후보를 복사하지 못했습니다.") }
                }, enabled = result.candidates.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("검증 후보 복사") }
            }
        }

        if (!state.unknownCodeQueueLoading && result != null && candidates.isEmpty()) {
            SectionCard("표시할 미해설 코드가 없습니다") {
                Text(if (result.unclassifiedRecords > 0) "미분류 기록이 있습니다. 진단 기록 화면에서 실제 차량별로 분류한 뒤 다시 분석하세요." else "현재 분류된 기록에서는 등록되지 않은 코드를 찾지 못했습니다.")
                Text("후보가 없다는 사실은 차량에 고장이 없다는 판정이 아닙니다.", style = MaterialTheme.typography.bodySmall)
            }
        }

        candidates.take(200).forEach { candidate ->
            SectionCard("${candidate.code} · ECU ${candidate.ecu}") {
                Text(candidate.vehicleLabel, style = MaterialTheme.typography.titleMedium)
                Text(candidate.coverageLabel ?: "기존 기록 · 차량 계열 미저장", style = MaterialTheme.typography.bodySmall)
                StatusRow("서로 다른 진단", "${candidate.sessionCount}건")
                StatusRow("수신 기간", "${candidate.firstSeen.toLocalDate()} ~ ${candidate.lastSeen.toLocalDate()}")
                StatusRow("상태 바이트/구분", candidate.statuses.joinToString())
                StatusRow("코드 형태", candidate.formatLabel)
                Text("검증 상태: 정의 확인 전", color = MaterialTheme.colorScheme.primary)
                Text("제조사·차종·연식·판매지역·ECU와 공식 원문을 대조해야 정식 해설에 등록할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onOpenCode(candidate.code) }) { Text("현재 해설 검색") }
            }
        }
        if (candidates.size > 200) Text("화면에는 우선순위 상위 200건만 표시합니다. 복사 목록에는 전체 후보가 포함됩니다.")
    }
}
