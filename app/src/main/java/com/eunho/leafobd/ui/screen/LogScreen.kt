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

    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        if (state.savedSessions.isEmpty()) {
            SectionCard("기록 없음") {
                Text(
                    "아직 저장된 진단 기록이 없습니다. 진단을 실행하면 자동으로 저장됩니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        state.savedSessions.forEach { saved ->
            SectionCard(saved.displayName) {
                StatusRow("저장 시각", formatMillis(saved.savedAtMillis))
                StatusRow("JSON 파일", if (saved.jsonFile.exists()) "있음" else "없음")

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
