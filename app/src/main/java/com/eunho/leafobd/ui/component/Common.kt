package com.eunho.leafobd.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.eunho.leafobd.ui.theme.StatusFail
import com.eunho.leafobd.ui.theme.StatusOk
import com.eunho.leafobd.ui.theme.StatusWarn
import com.eunho.leafobd.viewmodel.StepStatus

/**
 * 앱 전체에 반복해서 표시하는 안전 경고 카드.
 *
 * 지시서 2.1에서 요구한 문구를 담고 있으며, 홈 화면과 삭제 화면에 반드시 표시한다.
 */
@Composable
fun SafetyWarningCard(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "안전 경고",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (emphasized) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "이 앱은 개인 진단 보조 도구이며 정비소의 전문 진단을 대체하지 않습니다.\n" +
                    "오류코드 삭제는 고장을 수리하지 않습니다.\n" +
                    "고전압 배터리, 절연, 충전기, 브레이크, 에어백 관련 이상이 있으면 " +
                    "차량을 운행하거나 충전하지 말고 전문 점검을 받으십시오.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasized) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 테스트 모드에서 항상 보여야 하는 배지. */
@Composable
fun SimulatedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "모의 데이터 — 실제 차량 결과 아님",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** 제목이 있는 카드. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

/** `라벨 ......... 값` 한 줄. */
@Composable
fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/** 진단 단계 상태를 나타내는 작은 점. */
@Composable
fun StepDot(status: StepStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        StepStatus.PENDING -> MaterialTheme.colorScheme.outlineVariant
        StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
        StepStatus.SUCCESS -> StatusOk
        StepStatus.WARNING -> StatusWarn
        StepStatus.FAILED -> StatusFail
    }
    Spacer(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

fun stepStatusLabel(status: StepStatus): String = when (status) {
    StepStatus.PENDING -> "대기"
    StepStatus.RUNNING -> "진행 중"
    StepStatus.SUCCESS -> "성공"
    StepStatus.WARNING -> "주의"
    StepStatus.FAILED -> "실패"
}

/**
 * 원시 응답을 접었다 펼 수 있는 영역.
 *
 * 원시 응답은 진단의 근거이므로 항상 볼 수 있어야 하지만,
 * 기본으로 펼쳐 두면 화면이 읽기 어려워지므로 접어 둔다.
 */
@Composable
fun ExpandableRaw(
    label: String = "원시 응답 보기",
    raw: String,
    modifier: Modifier = Modifier
) {
    if (raw.isBlank()) return
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "원시 응답 접기" else label)
        }
        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = raw.replace("\r", "\\r").replace("\n", "\\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}
