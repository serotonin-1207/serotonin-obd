package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.data.DiagnosticKnowledge
import com.eunho.leafobd.data.DiagnosticUrgency
import com.eunho.leafobd.data.KnowledgeCatalog
import com.eunho.leafobd.data.VehicleProfile
import com.eunho.leafobd.data.VehicleDtcEvidenceCatalog
import com.eunho.leafobd.data.VehicleDtcMatchState
import com.eunho.leafobd.data.verificationLevel
import com.eunho.leafobd.data.DomesticSpecificationCatalog
import com.eunho.leafobd.ui.component.SectionCard

@Composable
fun KnowledgeScreen(onOpenUrl: (String) -> Unit, initialCode: String = "", vehicleProfile: VehicleProfile? = null) {
    var query by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    var professional by rememberSaveable { mutableStateOf(false) }
    var urgencyFilter by rememberSaveable { mutableStateOf<DiagnosticUrgency?>(null) }
    var visibleCount by rememberSaveable { mutableIntStateOf(30) }
    val matchingCodes = DiagnosticKnowledge.search(query)
    val results = matchingCodes.filter { urgencyFilter == null || it.urgency == urgencyFilter }
    val shownResults = results.take(visibleCount)
    val catalogStatus = DiagnosticKnowledge.catalogStatus
    val vehicleEvidence = VehicleDtcEvidenceCatalog.search(query, vehicleProfile)
    LaunchedEffect(query, urgencyFilter) { visibleCount = 30 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("코드의 의미부터, 다음 확인까지", style = MaterialTheme.typography.headlineSmall)
        Text("오프라인 해설 ${catalogStatus.entryCount}건 · 근거 원문 ${catalogStatus.sourceCount}건 · ${DiagnosticKnowledge.VERSION}", style = MaterialTheme.typography.bodySmall)
        Text("자동 수집 데이터베이스가 아닙니다. 제조사·공식 공개 자료를 사람이 대조한 항목만 포함하며, 확인되지 않은 세부 정의는 미검증으로 표시합니다.", style = MaterialTheme.typography.bodySmall)
        if (DiagnosticKnowledge.loadError) Text("내장 데이터 팩을 읽지 못해 해설을 표시할 수 없습니다.", color = MaterialTheme.colorScheme.error)
        SectionCard("데이터 검증 현황") {
            Text("공통 OBD ${catalogStatus.commonObdEntries}건 · 제조사 전용 ${catalogStatus.manufacturerSpecificEntries}건")
            Text("제조사 문서 대조 ${catalogStatus.oemDocumentEntries}건 · 정의 확인 중 ${catalogStatus.pendingDefinitionEntries}건")
            Text(if (catalogStatus.issues.isEmpty()) "출처·권리·대조일 자동 검사 통과" else "데이터 검사 문제 ${catalogStatus.issues.size}건", style = MaterialTheme.typography.bodySmall)
            Text("유료 정비 자료는 앱 배포·표시 권한이 명시된 기업용 계약을 체결한 뒤에만 추가합니다.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(query, { query = it.take(80) }, label = { Text("오류코드 또는 계통 검색") },
            supportingText = { Text("예: P0300, P0171, 촉매") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (vehicleEvidence.isNotEmpty()) SectionCard("차량별 제조사 문서 근거") {
            vehicleEvidence.forEach { match ->
                Text("${match.evidence.code} · ${match.system.label}", style = MaterialTheme.typography.titleSmall)
                Text(match.state.label, color = when (match.state) {
                    VehicleDtcMatchState.DOCUMENT_CONDITIONS_MATCH -> MaterialTheme.colorScheme.primary
                    VehicleDtcMatchState.SELECTED_VEHICLE_DIFFERS -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                })
                match.matchedApplication?.let { Text("적용표: ${it.label}", style = MaterialTheme.typography.bodySmall) }
                if (match.missingInformation.isNotEmpty()) Text("추가 확인: ${match.missingInformation.joinToString()}", style = MaterialTheme.typography.bodySmall)
                Text(match.evidence.documentContext)
                Text("교차 검증: ${match.evidence.verificationLevel.label}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text("해설 검토: ${match.evidence.promotionDecision.label}", style = MaterialTheme.typography.bodySmall)
                match.evidence.secondarySources.forEach { source ->
                    Text("${source.relationship.label} · ${source.note}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onOpenUrl(source.url) }) { Text("대조 원문 열기 · ${source.title}") }
                }
                Text(match.evidence.cautions, style = MaterialTheme.typography.bodySmall)
                DomesticSpecificationCatalog.relatedTo(vehicleProfile, match.system.id).forEach { domestic ->
                    Text("국내 사양 대조: ${domestic.specification.label}", style = MaterialTheme.typography.titleSmall)
                    Text(domestic.specification.comparisonNote, style = MaterialTheme.typography.bodySmall)
                    Text("국내 DTC 적용은 아직 확인되지 않았습니다.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onOpenUrl(domestic.specification.source.url) }) { Text("국내 공식 사양 열기") }
                }
                TextButton(onClick = { onOpenUrl(match.system.source.url) }) { Text("제조사 공지 원문 열기 · ${match.system.source.title}") }
            }
            if (vehicleProfile == null) Text("내 차량을 등록·선택하면 문서의 차종·연식·사양과 비교합니다.", style = MaterialTheme.typography.bodySmall)
            Text("차량별 DTC 근거표 리비전 ${VehicleDtcEvidenceCatalog.revision}", style = MaterialTheme.typography.bodySmall)
        }
        Text("검색 결과 ${results.size}건${if (results.size > shownResults.size) " · ${shownResults.size}건 표시 중" else ""}", style = MaterialTheme.typography.bodySmall)
        FilterChip(selected = professional, onClick = { professional = !professional }, label = { Text(if (professional) "정비사 상세 켜짐" else "정비사 상세 보기") })
        Text("대응 단계로 보기", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = urgencyFilter == null, onClick = { urgencyFilter = null }, label = { Text("전체") })
            FilterChip(selected = urgencyFilter == DiagnosticUrgency.STOP_AND_TOW, onClick = { urgencyFilter = DiagnosticUrgency.STOP_AND_TOW }, label = { Text("운행 중단") })
            FilterChip(selected = urgencyFilter == DiagnosticUrgency.PROMPT_SERVICE, onClick = { urgencyFilter = DiagnosticUrgency.PROMPT_SERVICE }, label = { Text("빠른 점검") })
            FilterChip(selected = urgencyFilter == DiagnosticUrgency.SERVICE_SOON, onClick = { urgencyFilter = DiagnosticUrgency.SERVICE_SOON }, label = { Text("조속한 점검") })
            FilterChip(selected = urgencyFilter == DiagnosticUrgency.CONTEXT_REQUIRED, onClick = { urgencyFilter = DiagnosticUrgency.CONTEXT_REQUIRED }, label = { Text("조건 확인") })
        }
        if (results.isEmpty()) SectionCard("등록된 해설이 없습니다") {
            Text(if (matchingCodes.isNotEmpty()) "현재 선택한 대응 단계에 해당하는 검색 결과가 없습니다. ‘전체’를 선택해 다시 확인하세요." else if (DiagnosticKnowledge.validCode(query) && vehicleEvidence.isNotEmpty()) "제조사 문서 근거는 있지만 검증된 일반 해설은 아직 데이터 팩에 등록되지 않았습니다. 위 적용 조건과 원문을 확인하세요." else if (DiagnosticKnowledge.validCode(query)) "형식은 인식했습니다. 이 코드의 검증된 설명은 아직 등록되지 않았습니다." else "코드 형식과 입력 내용을 확인하세요. 제조사 세부 코드도 원문 그대로 보관하세요.")
            Text("코드가 검색되지 않아도 고장이 없다는 뜻은 아닙니다.")
        }
        shownResults.forEach { entry ->
            SectionCard("${entry.code} · ${entry.title}") {
                Text(entry.codeScope.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(entry.urgency.label, color = when (entry.urgency) {
                    DiagnosticUrgency.STOP_AND_TOW -> MaterialTheme.colorScheme.error
                    DiagnosticUrgency.PROMPT_SERVICE -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }, style = MaterialTheme.typography.titleSmall)
                Text(entry.drivingAdvice)
                Text(entry.explanation)
                if (entry.nextChecks.isNotEmpty()) {
                    Text(entry.verification, style = MaterialTheme.typography.bodySmall)
                    Text("확인 순서", style = MaterialTheme.typography.titleSmall)
                    entry.nextChecks.forEachIndexed { i, step -> Text("${i + 1}. $step") }
                    Text(entry.limitations, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.ownerChecks.isNotEmpty()) {
                    Text("직접 확인할 항목", style = MaterialTheme.typography.titleSmall)
                    entry.ownerChecks.forEach { Text("• $it") }
                }
                Text("삭제 전에 저장", style = MaterialTheme.typography.titleSmall)
                Text(entry.beforeClear.joinToString(" · "))
                Spacer(Modifier.height(6.dp))
                Text("기록 확보 → 차량 사양 확인 → 제조사 절차 대조", color = MaterialTheme.colorScheme.primary)
                Text(entry.evidence, style = MaterialTheme.typography.bodyMedium)
                Text("적용 범위: ${entry.sourceScope}", style = MaterialTheme.typography.bodySmall)
                Text("참고 문서: ${entry.applicability.manufacturer} · ${entry.applicability.region} · ${entry.applicability.model}")
                Text("국내 사양 대조: ${if (entry.applicability.domesticVerified) "검증됨" else "미검증"}", style = MaterialTheme.typography.bodySmall)
                if (professional) {
                    if (entry.technicianHandoff.isNotEmpty()) {
                        Text("정비사 전달 항목", style = MaterialTheme.typography.titleSmall)
                        entry.technicianHandoff.forEach { Text("• $it") }
                    }
                    KnowledgeCatalog.source(entry.sourceId)?.let { source ->
                        Text("근거 등급: ${source.grade.label} · 이용 범위: ${source.right.label}", style = MaterialTheme.typography.bodySmall)
                        Text("발행: ${source.publisher}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("연식: ${entry.applicability.modelYears}\n동력계: ${entry.applicability.powertrain}")
                    Text(entry.redistribution, style = MaterialTheme.typography.bodySmall)
                    Text(entry.verification, style = MaterialTheme.typography.bodySmall)
                    Text("대조일 ${entry.reviewedAt} · 원문 재배포 없이 직접 작성한 한국어 요약", style = MaterialTheme.typography.bodySmall)
                    Text("ECU 식별·연식·판매지역을 확인한 뒤 원문의 적용 조건을 판단하세요. 이 해설은 부품 교체 지시가 아닙니다.")
                }
                if (entry.sourceUrl.isNotBlank()) TextButton(onClick = { onOpenUrl(entry.sourceUrl) }) { Text("${entry.sourceLabel} 열기 · 인터넷") }
            }
        }
        if (shownResults.size < results.size) {
            OutlinedButton(onClick = { visibleCount += 30 }, modifier = Modifier.fillMaxWidth()) {
                Text("30개 더 보기 · 남은 ${results.size - shownResults.size}건")
            }
        }
        SectionCard("미지원과 정상은 다릅니다") {
            Text("응답 없음은 고장 없음이 아닙니다. 저장·보류·영구 코드를 구분하고, 삭제 전에 발생 당시 기록을 보존하세요.")
            Text("고전압·제동·에어백 관련 작업은 해당 차량의 전문 정비 절차가 필요합니다.")
        }
    }
}
