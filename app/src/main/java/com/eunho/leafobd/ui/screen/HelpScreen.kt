package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.ui.component.SafetyWarningCard
import com.eunho.leafobd.ui.component.SectionCard

/** 실제 차량에서의 사용 절차. */
@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SafetyWarningCard()

        SectionCard("1. 진단 전 준비") {
            Text(
                "1. 차량을 안전한 장소에 주차합니다.\n" +
                    "2. 변속 위치를 P에 둡니다.\n" +
                    "3. 주차 브레이크를 체결합니다.\n" +
                    "4. 충전 케이블을 분리합니다.\n" +
                    "5. 고전압 부품(주황색 배선)을 만지지 않습니다.\n" +
                    "6. OBD2 어댑터를 운전석 하단 포트에 꽂습니다.\n" +
                    "7. 스마트폰 Bluetooth 설정에서 어댑터를 페어링합니다.\n" +
                    "8. 차량 전원을 켭니다. (전기차는 브레이크를 밟고 전원 버튼)\n" +
                    "   전원이 꺼져 있으면 일부 ECU가 응답하지 않습니다.\n" +
                    "9. Serotonin OBD 앱을 실행합니다.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SectionCard("2. 진단 (버튼 한 번)") {
            Text(
                "어댑터 연결 → 진단 시작 → 진단 실행 을 누르면 아래가 자동으로 진행됩니다.\n\n" +
                    "1. ELM327 초기화\n" +
                    "2. 어댑터 정보·전압 확인\n" +
                    "3. 연결 프로토콜 자동 탐색\n" +
                    "4. 표준 OBD 오류코드 읽기 (Mode 03/07/0A)\n" +
                    "5. 표준으로 코드가 없으면 ECU 주소를 직접 찾아 조회\n" +
                    "6. 결과를 파일로 저장",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "일반 휘발유·경유 차량은 4단계에서 코드가 나옵니다.\n" +
                    "전기차(예: Nissan Leaf)는 표준 OBD를 지원하지 않아 " +
                    "5단계에서 ECU를 직접 찾아 코드를 읽습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("3. 결과 확인과 전달") {
            Text(
                "• 진단 화면에서 읽은 코드를 확인합니다.\n" +
                    "• ECU 오류코드 화면에서 '결과 복사'를 누르면 " +
                    "정비소에 그대로 전달할 형식으로 복사됩니다.\n" +
                    "• 진단 기록 화면에서 TXT·JSON 파일을 공유할 수 있습니다.\n" +
                    "• ECU 데이터 스캔으로 부품번호·소프트웨어 버전 등을 추가로 찾을 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "코드 표기 P33ED-00 에서 뒤 2자리(고장 유형)까지 함께 전달하십시오. " +
                    "같은 코드라도 원인이 갈립니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("4. 삭제를 시도한다면") {
            Text(
                "삭제 화면이 두 가지입니다. 차량에 맞는 쪽을 쓰십시오.\n\n" +
                    "• 오류코드 삭제 — 표준 OBD(Mode 04). 일반 차량용.\n" +
                    "• ECU 오류코드 삭제 — ECU 주소로 직접 삭제(UDS). " +
                    "전기차처럼 표준 OBD가 안 되는 차량용.\n\n" +
                    "잘못된 화면을 열면 앱이 경고하고 맞는 화면으로 안내합니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "삭제 절차\n" +
                    "1. 먼저 진단을 실행해 코드를 읽고 저장합니다. (필수)\n" +
                    "2. 안전 확인 항목을 모두 선택합니다.\n" +
                    "3. 확인 문구를 입력합니다.\n" +
                    "4. 삭제를 실행합니다. (세션당 1회, 자동 반복 없음)\n" +
                    "5. 3초 뒤 자동으로 다시 읽어 전후를 비교합니다.\n" +
                    "6. 차량 전원을 껐다가 수 분 후 다시 켜고 재조회합니다.\n" +
                    "7. 충전·운행 전에 코드가 재발했는지 확인합니다.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "삭제해도 고쳐지지 않는 경우",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "코드를 지운 직후 화면에 '지금도 고장을 감지하고 있습니다'라고 나오면, " +
                        "ECU가 그 고장을 실시간으로 보고 있다는 뜻입니다. " +
                        "반복해서 지워도 결과는 같습니다.\n\n" +
                        "특히 고전압 배터리 계통 코드가 이 상태라면, 차량이 충전이나 주행을 막는 것은 " +
                        "보호 동작입니다. 기록을 지워 잠시 동작하더라도 원인이 해결된 것은 아닙니다. " +
                        "전문 점검을 받으십시오.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "즉시 중단 조건",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "• 타는 냄새\n" +
                        "• 연기\n" +
                        "• 비정상적인 열\n" +
                        "• 고전압 경고\n" +
                        "• 절연 관련 오류\n" +
                        "• 배터리 누액 의심\n" +
                        "• 충돌 후 발생한 오류\n" +
                        "• 브레이크 또는 에어백 경고\n" +
                        "• 코드 삭제 직후 즉시 재발\n" +
                        "• 차량이 READY 상태로 들어가지 않음",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "위 상황 중 하나라도 해당하면 작업을 멈추고 차량에서 떨어진 뒤 전문가에게 연락하십시오. " +
                        "충전 중이라면 즉시 충전을 중단하십시오.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        SectionCard("이 앱이 하지 않는 것") {
            Text(
                "• 고전압 시스템 강제 활성화\n" +
                    "• 충전 접촉기 강제 작동\n" +
                    "• 충전 강제 시작\n" +
                    "• ECU 코딩 및 펌웨어 변경\n" +
                    "• 보안 접근 우회, 데이터 쓰기\n" +
                    "• 에어백·ABS·브레이크 관련 임의 명령\n" +
                    "• 검증되지 않은 제조사 전용 명령 전송\n" +
                    "• 자동 삭제, 앱 실행과 동시 삭제, 무한 재시도",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "읽기와 표준 삭제만 합니다. 차량을 억지로 동작시키는 명령은 넣지 않았습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
