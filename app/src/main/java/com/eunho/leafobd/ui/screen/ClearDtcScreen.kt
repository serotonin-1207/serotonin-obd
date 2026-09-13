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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.obd.DtcCode
import com.eunho.leafobd.ui.component.ExpandableRaw
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.ui.component.SimulatedBadge
import com.eunho.leafobd.ui.component.StatusRow
import com.eunho.leafobd.ui.theme.StatusFail
import com.eunho.leafobd.viewmodel.ClearConfirmation
import com.eunho.leafobd.viewmodel.MainUiState

@Composable
fun ClearDtcScreen(
    state: MainUiState,
    onToggleCheck: (Int, Boolean) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onClear: () -> Unit,
    onGoToUdsClear: () -> Unit
) {
    val eligibility = state.clearEligibility

    // 표준 OBD 로 코드가 하나도 안 나오는 차량(전기차 등)에서는
    // 이 화면의 Mode 04 삭제가 아무 일도 하지 않는다. 헷갈리지 않게 먼저 알린다.
    val standardObdUseless = state.modeResults.isNotEmpty() && state.dtcs.isEmpty()
    val hasUdsCodes = (state.udsResult?.allCodes?.size ?: 0) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.simulated) SimulatedBadge()

        // 이 화면이 이 차량에서 의미가 없을 때 가장 먼저 알린다.
        if (standardObdUseless || hasUdsCodes) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "이 화면은 이 차량에 맞지 않습니다",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "여기는 **표준 OBD 삭제(Mode 04)** 화면입니다. " +
                            "이 차량은 표준 OBD 요청에 응답하지 않으므로 " +
                            "여기서 삭제해도 NO DATA 만 돌아옵니다.\n\n" +
                            "ECU 주소를 직접 지정해 읽은 오류코드는 " +
                            "**ECU 오류코드 삭제** 화면에서 지워야 합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = onGoToUdsClear, modifier = Modifier.fillMaxWidth()) {
                        Text("ECU 오류코드 삭제 화면으로 이동")
                    }
                }
            }
        }

        // 최상단 빨간 경고 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "반드시 읽어 주십시오",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    ClearConfirmation.WARNING,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        SectionCard("표준 삭제 명령의 범위") {
            Text(ClearConfirmation.SCOPE_NOTICE, style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard("서비스센터 안내로 삭제하는 경우") {
            Text(ClearConfirmation.SERVICE_CENTER_NOTICE, style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard("삭제하면 함께 사라지는 것") {
            Text(
                "프리즈 프레임(고장 발생 순간의 데이터 스냅샷)도 함께 지워집니다. " +
                    "이 앱은 삭제 전에 프리즈 프레임을 읽어 로그 파일에 저장해 둡니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            val frame = state.freezeFrame
            StatusRow(
                "프리즈 프레임 확보",
                when {
                    frame == null -> "확인 안 됨"
                    !frame.hasData -> "읽지 못함 또는 차량에 저장된 값 없음"
                    else -> "저장됨 (원인 DTC ${frame.triggerDtc ?: "확인 불가"})"
                },
                valueColor = if (frame == null || !frame.hasData) StatusFail
                else MaterialTheme.colorScheme.onSurface
            )
            if (frame == null) {
                Text(
                    "진단을 먼저 실행하면 프리즈 프레임을 읽어 저장합니다. " +
                        "설정에서 이 조회를 꺼 두었다면 켜고 다시 진단하십시오.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard("사전 조건") {
            ConditionRow("어댑터 연결", eligibility.connected)
            ConditionRow("ELM327 초기화", eligibility.initialized)
            ConditionRow("오류코드 읽기 1회 이상", eligibility.dtcRead)
            ConditionRow("원시 응답 파일 저장", eligibility.logSaved)
            ConditionRow("안전 확인 3개 선택", eligibility.safetyChecked)
            ConditionRow("확인 문구 입력", eligibility.confirmationTyped)
            if (eligibility.alreadyCleared) {
                Text(
                    "이번 세션에서 이미 삭제를 실행했습니다. 반복 삭제는 허용하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusFail
                )
            }
        }

        SectionCard("안전 확인") {
            ClearConfirmation.CHECKLIST.forEachIndexed { index, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.safetyChecks.getOrElse(index) { false },
                        onCheckedChange = { onToggleCheck(index, it) },
                        enabled = !state.clearAttempted
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionCard("확인 문구 입력") {
            Text(
                "아래 문구를 그대로 입력해야 삭제 버튼이 활성화됩니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                ClearConfirmation.PHRASE,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = state.confirmationInput,
                onValueChange = onConfirmationChange,
                singleLine = true,
                enabled = !state.clearAttempted,
                label = { Text("확인 문구") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!eligibility.eligible && eligibility.unmetReasons.isNotEmpty()) {
            SectionCard("아직 삭제할 수 없는 이유") {
                eligibility.unmetReasons.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Button(
            onClick = onClear,
            enabled = eligibility.eligible && !state.clearRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.clearRunning) "삭제 및 재조회 중…" else "표준 오류코드 삭제")
        }

        if (state.clearRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "삭제 명령을 1회 보낸 뒤 3초 기다렸다가 자동으로 다시 읽습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.clearComparison?.let { comparison ->
            SectionCard("삭제 결과") {
                Text(if (comparison.verificationComplete) "재조회 완료 · 수리 완료를 뜻하지 않습니다." else "재조회 불완전 · 코드 소거 여부 확인 불가", color = MaterialTheme.colorScheme.primary)
                StatusRow("어댑터 수신", if (comparison.accepted) "정상 응답(44)" else "확인 불가")
                ExpandableRaw(label = "삭제 명령 원시 응답 보기", raw = comparison.response)
            }

            CodeGroup("삭제 전 코드", comparison.before)
            CodeGroup("삭제 후 코드", comparison.after)
            CodeGroup("사라진 코드", comparison.cleared)
            CodeGroup("남은 코드 (재발)", comparison.remaining, highlight = true)
            CodeGroup("새로 나타난 코드", comparison.appeared, highlight = true)

            if (comparison.hasRecurrence) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "코드가 다시 나타났습니다. 활성 고장일 수 있습니다.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "고전압 배터리, 절연, 충전 계통 관련 코드라면 충전과 운행을 중단하고 " +
                                "즉시 전문 점검을 받으십시오. 반복해서 삭제하지 마십시오.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (comparison.verificationComplete) {
                SectionCard("다음 절차") {
                    Text(
                        "1. 차량 전원을 완전히 끕니다.\n" +
                            "2. 스마트키를 차량에서 멀리 둡니다.\n" +
                            "3. 수 분 후 차량을 다시 켭니다.\n" +
                            "4. 오류코드를 다시 읽습니다.\n" +
                            "5. 충전을 시도하기 전에 고전압 관련 코드가 재발했는지 확인합니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SectionCard("전문 점검 권고") {
                Text(
                    "삭제 결과와 무관하게, EV SYSTEM 경고나 충전 불가 증상이 계속되면 " +
                        "공식 서비스센터 또는 자격을 갖춘 정비사의 점검을 받으십시오.\n" +
                        "표준 OBD 삭제는 Nissan Leaf의 EV 전용 오류를 지우지 못할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ConditionRow(label: String, met: Boolean) {
    StatusRow(
        label = label,
        value = if (met) "충족" else "미충족",
        valueColor = if (met) MaterialTheme.colorScheme.primary else StatusFail
    )
}

@Composable
private fun CodeGroup(title: String, codes: List<DtcCode>, highlight: Boolean = false) {
    SectionCard(title) {
        if (codes.isEmpty()) {
            Text("없음", style = MaterialTheme.typography.bodyMedium)
        } else {
            codes.forEach { DtcRow(it, highlight = highlight) }
        }
    }
}
