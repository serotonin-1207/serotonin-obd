package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.data.VehicleProfile
import com.eunho.leafobd.data.DiagnosticDataHierarchy
import com.eunho.leafobd.data.ApplicationMatchState
import com.eunho.leafobd.data.PowertrainSystemCatalog
import com.eunho.leafobd.data.DomesticSpecificationCatalog
import com.eunho.leafobd.data.DomesticSpecificationMatchState
import com.eunho.leafobd.data.VehicleSupportLevel
import com.eunho.leafobd.data.VehicleSupportMatrix
import com.eunho.leafobd.ui.component.SectionCard

@Composable
fun VehicleSupportScreen(profile: VehicleProfile?, onManageVehicles: () -> Unit, onOpenUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("내 차에서 가능한 기능", style = MaterialTheme.typography.headlineSmall)
        if (profile == null) {
            SectionCard("차량을 먼저 선택하세요") {
                Text("제조사·차종·연식·동력계를 알아야 예상 지원 범위를 구분할 수 있습니다.")
                Button(onClick = onManageVehicles, modifier = Modifier.fillMaxWidth()) { Text("차량 선택·등록") }
            }
            return@Column
        }
        val assessment = VehicleSupportMatrix.assess(profile)
        val dataLayers = DiagnosticDataHierarchy.assess(profile)
        val powertrainMatches = PowertrainSystemCatalog.assess(profile)
        val domesticMatches = DomesticSpecificationCatalog.assess(profile)
        Text(profile.description)
        Text(profile.specification, style = MaterialTheme.typography.bodySmall)
        SectionCard("적용한 차량 카탈로그") {
            Text(assessment.coverage.label, style = MaterialTheme.typography.titleMedium)
            Text(assessment.coverage.evidenceState.label, color = MaterialTheme.colorScheme.primary)
            Text(assessment.coverage.note)
            assessment.coverage.sources.forEach { source ->
                TextButton(onClick = { onOpenUrl(source.url) }) { Text("근거 열기 · ${source.title}") }
            }
            Text("카탈로그 리비전 ${com.eunho.leafobd.data.VehicleCoverageCatalog.revision}", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("지원 현황") {
            Text("지원 ${assessment.supportedCount} · 부분 지원 ${assessment.partialCount} · 실차 검증 필요 ${assessment.fieldTestCount} · 미지원 ${assessment.unsupportedCount}")
            Text("표의 ‘지원’은 앱 기능 구현 상태입니다. 실제 차량 응답과 정비 결과를 보장하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("차량 데이터 적용 계층") {
            Text("같은 제조사 차량은 일부 체계를 공유하지만, 제조사 전용 코드의 뜻까지 같다고 가정하지 않습니다.")
            dataLayers.forEach { layer ->
                Text("${layer.layer.order}. ${layer.layer.label} · ${layer.label}", style = MaterialTheme.typography.titleSmall)
                Text(layer.status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text(layer.reuseRule, style = MaterialTheme.typography.bodySmall)
                layer.source?.let { source ->
                    TextButton(onClick = { onOpenUrl(source.url) }) { Text("분류 근거 열기 · ${source.title}") }
                }
            }
            Text("데이터 계층 리비전 ${DiagnosticDataHierarchy.revision}", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("파워트레인 공개 근거표") {
            Text("확보 ${PowertrainSystemCatalog.systems.size}개 시스템 · ${PowertrainSystemCatalog.systems.sumOf { it.applications.size }}개 적용 조건")
            if (powertrainMatches.isEmpty()) {
                Text("현재 입력한 차종·연식·동력계와 맞는 공개 적용표가 아직 없습니다.")
                Text("이는 진단 불가나 고장 없음 판정이 아닙니다.", style = MaterialTheme.typography.bodySmall)
            }
            powertrainMatches.forEach { match ->
                HorizontalDivider()
                Text(match.system.label, style = MaterialTheme.typography.titleSmall)
                Text(match.application.label)
                Text(match.state.label, color = when (match.state) {
                    ApplicationMatchState.DOCUMENT_MATCH -> MaterialTheme.colorScheme.primary
                    ApplicationMatchState.SPECIFICATION_REQUIRED -> MaterialTheme.colorScheme.tertiary
                    ApplicationMatchState.REGION_REVIEW_REQUIRED -> MaterialTheme.colorScheme.error
                })
                if (match.missingInformation.isNotEmpty()) Text("추가 확인: ${match.missingInformation.joinToString()}", style = MaterialTheme.typography.bodySmall)
                Text(match.system.reuseRule, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onOpenUrl(match.system.source.url) }) { Text("적용표 원문 열기 · ${match.system.source.title}") }
            }
            Text("파워트레인 카탈로그 리비전 ${PowertrainSystemCatalog.revision}", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("대한민국 공식 사양 대조") {
            Text("확보 ${DomesticSpecificationCatalog.entries.size}개 사양 · 카탈로그 리비전 ${DomesticSpecificationCatalog.revision}")
            if (domesticMatches.isEmpty()) {
                Text("현재 프로필과 정확히 비교할 국내 공식 사양 자료가 아직 없습니다.")
            }
            domesticMatches.forEach { match ->
                Text(match.specification.label, style = MaterialTheme.typography.titleSmall)
                Text(match.state.label, color = if (match.state == DomesticSpecificationMatchState.PROFILE_MATCH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                Text("엔진: ${match.specification.engineLabel} · 변속기: ${match.specification.transmissionLabel}")
                if (match.specification.batteryLabel.isNotBlank()) Text("배터리: ${match.specification.batteryLabel} · 구동: ${match.specification.driveLabel}")
                if (match.state == DomesticSpecificationMatchState.SPECIFICATION_REQUIRED) {
                    Text("차량 프로필의 ‘엔진·배터리 사양’에 용량·배기량과 구동방식(예: 84 kWh AWD)을 입력하면 후보를 좁힐 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                }
                if (match.specification.comparisonNote.isNotBlank()) Text(match.specification.comparisonNote, style = MaterialTheme.typography.bodySmall)
                Text(match.specification.limitation, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onOpenUrl(match.specification.source.url) }) { Text("국내 공식 사양 열기 · ${match.specification.source.title}") }
            }
        }
        assessment.items.forEach { item ->
            SectionCard(item.capability.label) {
                Text(item.level.label, color = when (item.level) {
                    VehicleSupportLevel.SUPPORTED -> MaterialTheme.colorScheme.primary
                    VehicleSupportLevel.PARTIAL -> MaterialTheme.colorScheme.tertiary
                    VehicleSupportLevel.FIELD_TEST_NEEDED -> MaterialTheme.colorScheme.secondary
                    VehicleSupportLevel.NOT_SUPPORTED -> MaterialTheme.colorScheme.error
                }, style = MaterialTheme.typography.titleSmall)
                Text(item.explanation)
                Text("판정 근거: ${item.evidence}", style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCard("표를 읽는 방법") {
            Text("지원: 앱 기능 구현 완료 · 부분 지원: 일부 ECU·값만 가능 · 실차 검증 필요: 후보 구현만 있음 · 미지원: 사용하지 않음")
            Text("응답 없음은 고장 없음이 아니며, 코드 삭제는 수리가 아닙니다.", color = MaterialTheme.colorScheme.primary)
        }
    }
}
