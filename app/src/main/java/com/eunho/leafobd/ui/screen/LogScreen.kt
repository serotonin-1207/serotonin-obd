package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.log.LogExporter
import com.eunho.leafobd.log.SavedSession
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.viewmodel.MainUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onDelete: (SavedSession) -> Unit,
    onRead: suspend (SavedSession) -> String,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openedSession by remember { mutableStateOf<SavedSession?>(null) }
    var openedText by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<String?>(null) }
    var savedReport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reportBusy by remember { mutableStateOf(false) }
    var historySelection by remember(state.savedSessions) { mutableStateOf(setOf<String>()) }
    var sameVehicleConfirmed by remember(historySelection) { mutableStateOf(false) }
    var historyText by remember(historySelection, state.savedSessions) { mutableStateOf<String?>(null) }
    var historyBusy by remember { mutableStateOf(false) }

    val groupStore = remember(context) { com.eunho.leafobd.log.RecordVehicleGroups(context) }
    var vehicleLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var vehicleFilter by remember { mutableStateOf<String?>(null) }
    var groupName by remember { mutableStateOf("") }
    var groupBusy by remember { mutableStateOf(false) }
    var groupsReady by remember { mutableStateOf(false) }
    var groupReload by remember { mutableStateOf(0) }
    val visibleSessions = state.savedSessions.filter { vehicleFilter == null || vehicleLabels[it.baseName].orEmpty() == vehicleFilter }
    LaunchedEffect(state.savedSessions, groupReload) {
        groupsReady = false
        try {
            vehicleLabels = withContext(Dispatchers.IO) { groupStore.labels(state.savedSessions) }
            groupsReady = true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { onMessage("차량 분류를 읽지 못했습니다. 원본 기록은 유지됩니다. 다시 불러오기를 눌러 주세요.") }
    }
    fun assignGroup(label: String?) {
        val picked = state.savedSessions.filter { it.baseName in historySelection }
        scope.launch {
            groupBusy = true
            try {
                withContext(Dispatchers.IO) { groupStore.assign(picked, label) }
                vehicleLabels = withContext(Dispatchers.IO) { groupStore.labels(state.savedSessions) }
                historySelection = emptySet()
                vehicleFilter = label?.trim() ?: ""
                onMessage(if (label == null) "차량 분류를 해제했습니다." else "선택한 기록의 차량 분류를 저장했습니다.")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onMessage("분류를 저장하지 못했습니다. 차량 별칭과 기록 파일을 확인하세요.") }
            finally { groupBusy = false }
        }
    }

    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("개인정보 제외 정비 요약") {
            Text("현재 열린 진단 결과와 배터리 기록으로 만듭니다. 아래 저장 목록의 과거 기록을 자동으로 포함하지 않습니다.")
            OutlinedButton(onClick = {
                report = com.eunho.leafobd.log.WorkshopReport.create(state.dtcs, state.udsResult?.results.orEmpty(), state.batterySnapshot, state.simulated)
            }, modifier = Modifier.fillMaxWidth(), enabled = !state.diagnosisRunning && !state.udsRunning && !state.batteryRunning && !state.clearRunning && !state.udsClearRunning) { Text("정비 요약 미리보기") }
            report?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    LogExporter.copyToClipboard(context, "정비소 전달용 요약", text)
                        .onSuccess { onMessage("개인정보 제외 요약을 복사했습니다.") }
                        .onFailure { onMessage("요약 복사에 실패했습니다.") }
                }, modifier = Modifier.fillMaxWidth()) { Text("이 요약 복사") }
                OutlinedButton(onClick = {
                    runCatching {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "정비 요약 공유"))
                    }.onFailure { onMessage("공유 창을 열지 못했습니다.") }
                }, modifier = Modifier.fillMaxWidth()) { Text("이 요약 공유") }
                TextButton(onClick = { report = null }) { Text("미리보기 닫기") }
            }
        }
        SectionCard("저장된 진단 기록") {
            StatusRow("기록 수", "${state.savedSessions.size}건")
            Text(
                "기록은 앱 내부 저장소에만 보관되며 인터넷으로 전송되지 않습니다.\n" +
                    "공유 버튼을 눌렀을 때만 Android 공유 창이 열립니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("목록 새로 고침")
            }
        }

        SectionCard("차량별 기록 정리") {
            Text("아래 기록을 선택한 뒤 같은 실제 차량에 속하는지 확인하고 별칭을 저장하세요. 같은 차종을 여러 대 보유했다면 서로 다른 별칭을 사용하세요.")
            Text("별칭은 이 휴대폰에만 저장되며 보고서에 포함하지 않습니다. 차량번호·VIN 대신 별칭을 사용하세요.", style = MaterialTheme.typography.bodySmall)
            Text("현재 필터: ${vehicleFilter?.ifEmpty { "미분류" } ?: "전체"} · ${visibleSessions.size}건")
            val filters = listOf<String?>(null, "") + vehicleLabels.values.distinct().sorted()
            filters.forEach { label ->
                androidx.compose.material3.FilterChip(selected = vehicleFilter == label, onClick = {
                    vehicleFilter = label
                    historySelection = emptySet()
                    historyText = null
                }, enabled = groupsReady && !groupBusy && !historyBusy, label = { Text(label?.ifEmpty { "미분류" } ?: "전체") })
            }
            androidx.compose.material3.OutlinedTextField(value = groupName, onValueChange = { groupName = it.take(40) },
                label = { Text("차량 별칭 · 예: 내 리프 EV") }, singleLine = true, enabled = !groupBusy,
                modifier = Modifier.fillMaxWidth())
            Text("선택 ${historySelection.size}건 · 저장하면 기존 분류가 이 별칭으로 바뀝니다.")
            Row {
                androidx.compose.material3.Checkbox(checked = sameVehicleConfirmed, onCheckedChange = { sameVehicleConfirmed = it }, enabled = !groupBusy && !historyBusy)
                Text("선택한 기록이 같은 실제 차량의 기록임을 확인했습니다.", modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = { assignGroup(groupName) }, enabled = groupsReady && !groupBusy && !historyBusy && historySelection.isNotEmpty() && sameVehicleConfirmed && runCatching { com.eunho.leafobd.log.GroupLabel.normalize(groupName) }.isSuccess,
                modifier = Modifier.fillMaxWidth()) { Text(if (groupBusy) "분류 저장 중…" else "선택한 기록을 이 차량으로 분류") }
            TextButton(onClick = { assignGroup(null) }, enabled = groupsReady && !groupBusy && !historyBusy && historySelection.isNotEmpty()) { Text("선택한 기록의 차량 분류 해제") }
            OutlinedButton(onClick = {
                historySelection = visibleSessions.filter { it.jsonFile.exists() }.take(30).map { it.baseName }.toSet()
            }, enabled = groupsReady && !groupBusy && !historyBusy && vehicleFilter != null && vehicleFilter != "" && visibleSessions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("이 차량 기록 선택 · 목록 순서로 최대 30건") }
            if (!groupsReady) TextButton(onClick = { groupReload++ }, enabled = !groupBusy) { Text("차량 분류 다시 불러오기") }
        }

        SectionCard("같은 차량의 코드 수신 이력") {
            Text("아래 목록에서 같은 차량의 진단 기록을 2~30개 선택하세요. 차량 이름이나 어댑터만으로 같은 차량이라고 자동 판단하지 않습니다.")
            Text("선택 ${historySelection.size}건 · 모의·배터리 전용 기록 제외", style = MaterialTheme.typography.bodySmall)
            Row {
                androidx.compose.material3.Checkbox(checked = sameVehicleConfirmed, onCheckedChange = { sameVehicleConfirmed = it; historyText = null }, enabled = !historyBusy && !groupBusy)
                Text("선택한 기록이 모두 같은 실제 차량의 기록임을 확인했습니다.", modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = {
                val picked = state.savedSessions.filter { it.baseName in historySelection }
                scope.launch {
                    historyBusy = true
                    historyText = null
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val records = picked.map { saved ->
                                require(saved.jsonFile.length() in 1..com.eunho.leafobd.log.SavedWorkshopReport.MAX_BYTES.toLong())
                                com.eunho.leafobd.log.SavedCodeHistory.parse(saved.jsonFile.readText())
                            }
                            com.eunho.leafobd.log.CodeHistory.describe(records)
                        }
                        if (historySelection == picked.map { it.baseName }.toSet()) historyText = result
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { onMessage("비교하지 못했습니다. 모의·배터리 전용·중복 세션·손상되거나 지원하지 않는 기록을 제외해 주세요.") }
                    finally { historyBusy = false }
                }
            }, enabled = !historyBusy && !groupBusy && sameVehicleConfirmed && historySelection.size in 2..30, modifier = Modifier.fillMaxWidth()) {
                Text(if (historyBusy) "기록 비교 중…" else "선택한 기록의 코드 비교")
            }
            historyText?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()))
            }
            if (historySelection.isNotEmpty()) TextButton(onClick = { historySelection = emptySet() }, enabled = !historyBusy && !groupBusy) { Text("비교 선택 초기화") }
        }

        if (state.savedSessions.isEmpty()) {
            SectionCard("기록 없음") {
                Text(
                    "아직 저장된 진단 기록이 없습니다. 진단을 실행하면 자동으로 저장됩니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.savedSessions.isNotEmpty() && visibleSessions.isEmpty()) Text("선택한 차량 분류의 기록이 없습니다.")
        visibleSessions.forEach { saved ->
            SectionCard(saved.displayName) {
                StatusRow("저장 시각", formatMillis(saved.savedAtMillis))
                Text("차량 분류: ${vehicleLabels[saved.baseName] ?: "미분류"}")
                OutlinedButton(onClick = {
                    historySelection = if (saved.baseName in historySelection) historySelection - saved.baseName else historySelection + saved.baseName
                }, enabled = !historyBusy && !groupBusy && saved.jsonFile.exists() && (saved.baseName in historySelection || historySelection.size < 30), modifier = Modifier.fillMaxWidth()) {
                    Text(if (saved.baseName in historySelection) "비교에 선택됨 · 누르면 해제" else "이 기록을 비교에 선택")
                }
                StatusRow("JSON 파일", if (saved.jsonFile.exists()) "있음" else "없음")
                OutlinedButton(onClick = {
                    scope.launch {
                        reportBusy = true
                        savedReport = null
                        try {
                            val text = withContext(Dispatchers.IO) {
                                require(saved.jsonFile.length() in 1..com.eunho.leafobd.log.SavedWorkshopReport.MAX_BYTES.toLong())
                                com.eunho.leafobd.log.SavedWorkshopReport.create(saved.jsonFile.readText())
                            }
                            savedReport = saved.baseName to text
                        } catch (_: Exception) { onMessage("보고서를 만들지 못했습니다. JSON 파일의 형식이나 크기를 확인하세요.") }
                        finally { reportBusy = false }
                    }
                }, enabled = !reportBusy && saved.jsonFile.exists(), modifier = Modifier.fillMaxWidth()) { Text("이 기록의 삭제 전후 보고서") }
                savedReport?.takeIf { it.first == saved.baseName }?.let { (_, text) ->
                    Text("공유 전 미리보기 · 이 기록만 포함", style = MaterialTheme.typography.titleMedium)
                    Text(text, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        LogExporter.copyToClipboard(context, "정비 보고서", text)
                            .onSuccess { onMessage("보고서를 복사했습니다.") }
                            .onFailure { onMessage("복사에 실패했습니다.") }
                    }, modifier = Modifier.fillMaxWidth()) { Text("보고서 텍스트 복사") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            reportBusy = true
                            try {
                                val file = withContext(Dispatchers.IO) { com.eunho.leafobd.log.WorkshopPdf.create(context, text) }
                                com.eunho.leafobd.log.WorkshopPdf.share(context, file)
                            } catch (_: Exception) { onMessage("PDF 생성 또는 공유 창 열기에 실패했습니다.") }
                            finally { reportBusy = false }
                        }
                    }, enabled = !reportBusy, modifier = Modifier.fillMaxWidth()) { Text(if (reportBusy) "보고서 처리 중…" else "이 보고서 PDF 공유") }
                    TextButton(onClick = { savedReport = null }) { Text("보고서 닫기") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            openedSession = saved
                            openedText = onRead(saved)
                        }
                    }) { Text("상세보기") }

                    TextButton(onClick = {
                        LogExporter.shareText(context, saved)
                            .onFailure { onMessage(it.message ?: "공유에 실패했습니다.") }
                    }) { Text("TXT 공유") }

                    TextButton(onClick = {
                        LogExporter.shareJson(context, saved)
                            .onFailure { onMessage(it.message ?: "공유에 실패했습니다.") }
                    }) { Text("JSON 공유") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        LogExporter.shareBoth(context, saved)
                            .onFailure { onMessage(it.message ?: "공유에 실패했습니다.") }
                    }) { Text("둘 다 공유") }

                    TextButton(onClick = { onDelete(saved) }) { Text("삭제") }
                }

                if (openedSession?.baseName == saved.baseName && openedText.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = openedText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                    TextButton(onClick = {
                        LogExporter.copyToClipboard(context, saved.baseName, openedText)
                            .onSuccess { onMessage("로그 전체를 클립보드에 복사했습니다.") }
                            .onFailure { onMessage(it.message ?: "복사에 실패했습니다.") }
                    }) { Text("전체 복사") }
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(millis))
