package com.eunho.leafobd.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.data.BatteryCsv
import com.eunho.leafobd.data.BatterySnapshot
import com.eunho.leafobd.ui.component.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun Double.display() = String.format(Locale.ROOT, "%.3f", this)

@Composable
fun BatteryScreen(state: com.eunho.leafobd.viewmodel.MainUiState,
    onSnapshot: (BatterySnapshot?) -> Unit,
    onRead: (com.eunho.leafobd.ev.EvProfile, Boolean) -> Unit,
    onConnect: () -> Unit,
    onProfile: (com.eunho.leafobd.ev.EvProfile) -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onDeleteRecord: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot = state.batterySnapshot
    val profile = state.batteryProfile
    var batteryReport by remember(snapshot) { mutableStateOf<com.eunho.leafobd.log.BatteryReport?>(null) }
    var reportError by remember(snapshot) { mutableStateOf<String?>(null) }
    var readEnabled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var professional by rememberSaveable { mutableStateOf(false) }
    var showTerms by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var expandedScale by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showExternal by rememberSaveable { mutableStateOf(false) }
    var historyLimit by remember { mutableIntStateOf(10) }
    var deleting by remember { mutableStateOf<com.eunho.leafobd.data.BatteryRecord?>(null) }
    var now by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = java.time.Instant.now(); kotlinx.coroutines.delay(30_000) } }
    LaunchedEffect(snapshot) { page = 0 }
    val locked = state.batteryRunning || state.batterySaving || busy
    deleting?.let { record -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text("저장 기록 삭제") }, text = { Text("${record.snapshot.vehicle}\n${record.snapshot.capturedAt}\n이 배터리 기록을 삭제합니다. 별도 진단 원시 로그는 남아 있습니다.") },
        confirmButton = { TextButton(onClick = { onDeleteRecord(record.id); deleting = null }, enabled = !locked) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (output.size() <= BatteryCsv.MAX_BYTES) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: throw IllegalArgumentException("파일을 열 수 없습니다.")
                    require(bytes.size <= BatteryCsv.MAX_BYTES) { "256 KB 이하 파일만 가져올 수 있습니다." }
                    BatteryCsv.parse(bytes.toString(Charsets.UTF_8))
                }
            }
            result.onSuccess { onSnapshot(it); page = 0 }.onFailure { error = it.message ?: "가져오기에 실패했습니다." }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("배터리 데이터 살펴보기", style = MaterialTheme.typography.headlineSmall)
        Text("니로·리프 배터리 읽기 시험 기능입니다. 실차 대조 전이며, 고장 확정이나 용량 시험 결과가 아닙니다.")
        SectionCard("차량에서 한 번 읽기 · 기본 꺼짐") {
            com.eunho.leafobd.ev.EvProfile.entries.forEach { candidate ->
                FilterChip(selected = profile == candidate, enabled = !locked,
                    onClick = { onProfile(candidate); readEnabled = false; page = 0 }, label = { Text(candidate.label) })
            }
            Text("선택한 차량인지 확인하고 정차·주차 상태에서 사용하세요. Vgate iCar Pro BT3.0 연결이 필요합니다.")
            Row {
                Checkbox(checked = readEnabled, enabled = !locked, onCheckedChange = { readEnabled = it })
                Text("선택 차량과 정차 상태 확인 · 읽기 시험 켜기", modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onConnect, enabled = !locked) { Text("어댑터 선택·연결") }
            Button(onClick = { error = null; page = 0; onRead(profile, readEnabled) }, enabled = readEnabled && !locked,
                modifier = Modifier.fillMaxWidth()) { Text(if (state.batteryRunning) "조회 중…" else "배터리 한 번 조회") }
            Text("자동 반복 없음 · 완료 후 연결 해제 · 재조회할 때 다시 연결", style = MaterialTheme.typography.bodySmall)
            if (state.batteryMessage.isNotBlank()) Text(state.batteryMessage)
            if (state.batteryRunning) {
                Text(state.batteryStage)
                LinearProgressIndicator(progress = { state.batteryProgress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onStop, enabled = !state.batteryStopRequested) { Text("조회 중단") }
                if (state.batteryStopRequested) Text("중단 요청됨 · 현재 요청이 끝나면 후속 조회를 멈춥니다. 현재 요청은 최대 약 8초, 설정 복원 시간이 추가될 수 있습니다.")
            }
        }
        OutlinedButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth()) { Text(if (showHistory) "저장 기록 접기" else "저장 기록 · 다시 열기") }
        if (showHistory) SectionCard("차량별 배터리 기록") {
            FilterChip(selected = !showExternal, onClick = { showExternal = false; historyLimit = 10 }, label = { Text("선택 차량 · ${profile.label}") })
            FilterChip(selected = showExternal, onClick = { showExternal = true; historyLimit = 10 }, label = { Text("외부 CSV · 차량 자동 분류 안 함") })
            Text("직접 조회는 자동 저장합니다. 외부 CSV는 아래 저장 버튼으로 보관하세요.")
            if (state.batteryHistoryError.isNotBlank()) Text(state.batteryHistoryError)
            val records = state.batteryRecords.filter { if (showExternal) it.profile == null else it.profile == profile }
            if (records.isEmpty()) Text("이 구분에 저장된 기록이 없습니다.")
            records.take(historyLimit).forEach { record ->
                val linkedVehicle = state.vehicleProfiles.firstOrNull { it.id == record.vehicleProfileId }
                Text("차량 프로필: ${linkedVehicle?.alias ?: if (record.vehicleProfileId == null) "기존·미연결 기록" else "삭제된 프로필"}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { error = null; onOpenRecord(record.id); page = 0 }, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                    Text("${record.snapshot.vehicle}\n${record.snapshot.capturedAt}\n전압 ${record.snapshot.channels.count { it.volts != null }}/${record.snapshot.expectedChannels} · 열기")
                }
                TextButton(onClick = { deleting = record }, enabled = !locked) { Text("이 기록 삭제") }
            }
            if (records.size > historyLimit) TextButton(onClick = { historyLimit += 10 }) { Text("기록 더 보기") }
        }
        Button(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "파일 확인 중…" else "측정 CSV 가져오기")
        }
        OutlinedButton(onClick = { onSnapshot(BatteryCsv.example()); error = null; page = 0 }, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text("가상 데이터로 화면 둘러보기") }
        error?.let { Text("가져오기 실패: $it\n아래 값은 기존 데이터입니다.", color = MaterialTheme.colorScheme.error) }
        FilterChip(selected = professional, onClick = { professional = !professional }, label = { Text(if (professional) "정비사 상세 켜짐" else "정비사 상세 보기") })
        OutlinedButton(onClick = { showTerms = !showTerms }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showTerms) "용어 설명 접기" else "SOC·SOH 등 용어 설명 보기")
        }
        if (showTerms) SectionCard("배터리 용어 바로 알기") {
            Text("SOC · State of Charge\n현재 사용할 수 있다고 차량이 계산한 충전 잔량입니다. 휴대전화 배터리의 잔량과 비슷한 개념입니다.")
            Text("SOH · State of Health\n새 배터리와 비교한 건강 상태를 차량 BMS가 추정한 값입니다. 실제 주행거리나 정밀 용량 시험 결과와 같지는 않습니다.")
            Text("BMS · Battery Management System\n고전압 배터리의 전압·온도·충전 상태를 감시하고 보호하는 차량 내부 제어 장치입니다.")
            Text("셀 전압 · 배터리를 이루는 각 셀 그룹의 전압입니다. 앱의 채널 번호는 실제 배터리 내부 위치와 다를 수 있습니다.")
            Text("셀 전압 편차 · 같은 시각의 최고 전압과 최저 전압 차이입니다. mV는 1V의 1,000분의 1입니다. 차량 상태와 측정 조건을 함께 봐야 합니다.")
            Text("배터리 온도 · BMS가 보고한 온도 센서 값입니다. 외기온, 충전, 직전 주행에 따라 달라집니다.")
        }
        val data = snapshot
        if (data == null) SectionCard("아직 측정 데이터가 없습니다") {
            Text("충전 잔량 · 미확인\nBMS 보고 건강도 · 미확인\n셀 전압 · 미확인\n온도 · 미확인")
            Text("어댑터 전압을 고전압 배터리의 건강도로 표시하지 않습니다.")
        } else {
            SectionCard(if (data.demo) "가상 예시 · 실제 차량 측정 아님" else "측정값 · 출처와 시각 확인") {
                Text(data.vehicle, style = MaterialTheme.typography.titleMedium)
                Text("측정 시각: ${data.capturedAt}")
                Text(com.eunho.leafobd.data.BatteryPresentation.age(data, now), style = MaterialTheme.typography.titleMedium)
                Text("출처: ${data.source}")
                Text(data.acquisitionNote, style = MaterialTheme.typography.bodySmall)
                Text("앱이 측정 정확성을 인증하지는 않습니다.", style = MaterialTheme.typography.bodySmall)
                if (!data.demo) Button(onClick = onSave, enabled = !locked && state.batteryRecordId == null) {
                    Text(if (state.batterySaving) "저장 중…" else if (state.batteryRecordId != null) "기기에 저장됨" else "측정값 저장 · 다시 열기 가능")
                }
            }
            SectionCard("배터리 정비 보고서") {
                Text("현재 열린 측정값 한 건으로 만듭니다. 차량명·출처 자유 입력·센서 이름은 제외하고 수치와 그래프를 담습니다.")
                OutlinedButton(onClick = {
                    runCatching { com.eunho.leafobd.log.BatteryReport.from(data) }
                        .onSuccess { batteryReport = it; reportError = null }
                        .onFailure { reportError = "측정값의 채널 번호나 범위를 확인하세요." }
                }, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text("배터리 보고서 미리보기") }
                batteryReport?.let { report ->
                    Text(report.summary, style = MaterialTheme.typography.bodySmall)
                    Text("PDF에 그래프 ${report.charts.size}페이지가 추가됩니다. 가상 예시는 모든 그래프에 표시됩니다.")
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            reportError = null
                            try {
                                val file = withContext(Dispatchers.IO) { com.eunho.leafobd.log.BatteryReportPdf.create(context, report) }
                                com.eunho.leafobd.log.WorkshopPdf.share(context, file)
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { reportError = "PDF 생성 또는 공유 창 열기에 실패했습니다." }
                            finally { busy = false }
                        }
                    }, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text("이 배터리 보고서 PDF 공유") }
                    TextButton(onClick = { batteryReport = null }, enabled = !locked) { Text("보고서 미리보기 닫기") }
                }
                reportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            SectionCard("충전 잔량과 건강도") {
                Text("충전 잔량 (SOC): ${com.eunho.leafobd.data.BatteryPresentation.percent(data.soc, 1)}", style = MaterialTheme.typography.titleLarge)
                data.soc?.let { LinearProgressIndicator(progress = { (it / 100).toFloat() }, modifier = Modifier.fillMaxWidth()) }
                Text("BMS 보고 건강도 (SOH): ${com.eunho.leafobd.data.BatteryPresentation.percent(data.reportedSoh, 2)}")
                Text("SOH는 차량 내부 추정값입니다. 별도 용량 시험 결과가 아닙니다.")
            }
            val pageCount = (data.expectedChannels + 23) / 24
            val start = page.coerceIn(0, pageCount - 1) * 24 + 1
            val ids = start..minOf(start + 23, data.expectedChannels)
            val byId = data.channels.associateBy { it.id }
            SectionCard("셀 채널 전압 · 번호 순서") {
                Text("수신 ${data.channels.count { it.volts != null }}/${data.expectedChannels}채널 · 전체 편차 ${data.spreadMv?.let { "${it.display()} mV" } ?: "확인 불가"}")
                Text(com.eunho.leafobd.data.BatteryPresentation.extrema(data))
                val received = data.channels.mapNotNull { it.volts }
                val lowest = received.minOrNull(); val highest = received.maxOrNull()
                val lower = if (expandedScale && lowest != null) (lowest - 0.01).coerceAtLeast(0.0) else 0.0
                val upper = if (expandedScale && highest != null) (highest + 0.01).coerceAtMost(6.0) else 6.0
                FilterChip(selected = expandedScale, onClick = { expandedScale = !expandedScale }, enabled = received.isNotEmpty(), label = { Text("전압 차이 확대 보기") })
                Text("눈금 ${lower.display()}~${upper.display()} V · ${if (expandedScale) "확대 눈금은 작은 차이도 크게 표시합니다" else "전체 눈금"}\n실제 내부 배치도 아님 · 빈 채널은 미수신", style = MaterialTheme.typography.bodySmall)
                val accent = MaterialTheme.colorScheme.primary
                val lowColor = MaterialTheme.colorScheme.tertiary
                val muted = MaterialTheme.colorScheme.surfaceVariant
                Text("최저 채널은 보조색 · 최고 채널은 기본색 · 색상은 고장 판정이 아닙니다.", style = MaterialTheme.typography.bodySmall)
                Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = "채널 $start 부터 ${ids.last} 전압 그래프. 아래 표에서 정확한 값 확인." }) {
                    val width = size.width / ids.count()
                    ids.forEachIndexed { i, id ->
                        drawRect(muted, Offset(i * width, 0f), Size((width - 3).coerceAtLeast(1f), size.height))
                        byId[id]?.volts?.let { v ->
                            val height = ((v - lower) / (upper - lower) * size.height).toFloat().coerceIn(0f, size.height)
                            drawRect(if (v == lowest && lowest != highest) lowColor else accent, Offset(i * width, size.height - height), Size((width - 3).coerceAtLeast(1f), height))
                        }
                    }
                }
                ids.toList().chunked(3).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.forEach { id -> Text("$id\n${byId[id]?.volts?.let { "${it.display()} V" } ?: "미수신"}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (pageCount > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("이전") }
                    Text("${page + 1}/$pageCount")
                    TextButton(onClick = { page++ }, enabled = page + 1 < pageCount) { Text("다음") }
                }
                Text("편차만으로 고장을 판정하지 않습니다. 차종·충전량·온도·부하 조건을 함께 확인하세요.")
            }
            SectionCard(if (data.temperatureSensors.isNotEmpty()) "배터리 온도 센서 · 셀 번호와 별개" else "온도 · 채널 $start~${ids.last}") {
                val allTemperatures = data.temperatureSensors.map { it.second } + data.channels.mapNotNull { it.celsius }
                if (allTemperatures.isNotEmpty()) Text("수신 온도 최저 ${allTemperatures.min().display()}℃ · 최고 ${allTemperatures.max().display()}℃ · 차이 ${(allTemperatures.max() - allTemperatures.min()).display()}℃")
                data.temperatureSensors.forEach { (label, temp) ->
                    Text("$label · ${String.format(Locale.ROOT, "%.1f", temp)} °C")
                    LinearProgressIndicator(progress = { ((temp + 80) / 230).toFloat() }, modifier = Modifier.fillMaxWidth())
                }
                ids.mapNotNull { id -> byId[id]?.celsius?.let { id to it } }.let { temperatures ->
                    if (temperatures.isEmpty() && data.temperatureSensors.isEmpty()) Text("이 구간에서 온도를 수신하지 못했습니다.")
                    temperatures.forEach { (id, temp) ->
                        Text("채널 $id · $temp °C")
                        LinearProgressIndicator(progress = { ((temp + 80) / 230).toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Text("눈금 −80~150°C · 정상/위험 기준이 아닌 표시 범위", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("이번 결과 읽기") {
                com.eunho.leafobd.data.BatteryPresentation.interpretation(data).forEach { Text("• $it") }
                Text("경고등, 출력 제한, 충전 이상, 타는 냄새 또는 비정상 발열이 있으면 수치와 관계없이 운행을 멈추고 정비 전문가에게 점검을 의뢰하세요.", color = MaterialTheme.colorScheme.error)
            }
            if (professional) SectionCard("진단 조건") {
                Text("${data.acquisitionNote}\n전류/부하 조건 미제공 · 차종별 한계값 미등록 · 용량 시험 없음\n누락 채널을 제외한 편차로 전체 상태를 판정하지 않음")
                if (state.batteryRaw.isNotBlank()) Text(state.batteryRaw, style = MaterialTheme.typography.bodySmall)
            }
            val previous = state.batteryRecords.firstOrNull { it.profile != null && it.profile == state.batteryRecordProfile &&
                java.time.OffsetDateTime.parse(it.snapshot.capturedAt).toInstant().isBefore(java.time.OffsetDateTime.parse(data.capturedAt).toInstant()) }
            if (previous != null) SectionCard("같은 차량의 이전 기록과 비교") {
                Text("이전: ${previous.snapshot.capturedAt}")
                fun delta(a: Double?, b: Double?) = if (a == null || b == null) "비교 불가" else "${(a - b).display()}%p"
                Text("잔량 변화 ${delta(data.soc, previous.snapshot.soc)}\nBMS 건강도 변화 ${delta(data.reportedSoh, previous.snapshot.reportedSoh)}")
                Text("충전량·온도·부하가 다를 수 있습니다. 차이를 배터리 열화나 수리 효과로 단정하지 않습니다.")
            }
            TextButton(onClick = { onSnapshot(null); page = 0 }, enabled = !locked) { Text("화면의 측정값 비우기") }
        }
        SectionCard("CSV 형식 · 기기 안에서 처리") {
            Text("#vehicle=차종과 연식\n#captured_at=2026-09-11T10:00:00+09:00\n#source=측정 장비와 버전\n#expected_channels=2\n#soc=68\n#soh=94\nchannel,voltage_v,temperature_c\n1,3.702,25\n2,3.700,26", style = MaterialTheme.typography.bodySmall)
            Text("SOC·SOH·전압·온도는 없으면 비워 두세요. CSV 가져오기는 차량 명령을 보내지 않습니다. 저장한 측정값은 앱을 종료해도 저장 기록에서 다시 열 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
