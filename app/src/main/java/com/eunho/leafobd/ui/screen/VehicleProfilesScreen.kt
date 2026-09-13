package com.eunho.leafobd.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eunho.leafobd.data.*
import com.eunho.leafobd.ev.EvProfile
import com.eunho.leafobd.ui.component.SectionCard
import com.eunho.leafobd.viewmodel.MainUiState

@Composable
fun VehicleProfilesScreen(state: MainUiState, onSave: (VehicleProfile) -> Unit, onSelect: (String?) -> Unit,
    onDelete: (String) -> Unit, onOpenSupport: () -> Unit) {
    var alias by remember { mutableStateOf("") }; var manufacturer by remember { mutableStateOf("") }; var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2019") }; var powertrain by remember { mutableStateOf(VehiclePowertrain.ELECTRIC) }; var evProfile by remember { mutableStateOf<EvProfile?>(null) }
    var market by remember { mutableStateOf(VehicleMarket.UNKNOWN) }; var engine by remember { mutableStateOf("") }; var transmission by remember { mutableStateOf("") }
    var makerMenuOpen by remember { mutableStateOf(false) }; var familyMenuOpen by remember { mutableStateOf(false) }
    var selectedFamilyMaker by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<VehicleProfile?>(null) }; val locked = state.diagnosisRunning || state.batteryRunning
    deleting?.let { profile -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("차량 프로필 삭제") },
        text = { Text("${profile.alias} 프로필을 삭제합니다. 기존 진단·배터리 기록은 삭제되지 않습니다.") },
        confirmButton = { TextButton(onClick = { onDelete(profile.id); deleting = null }) { Text("프로필 삭제") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("진단할 차량을 먼저 선택합니다", style = MaterialTheme.typography.headlineSmall)
        Text("차대번호·차량번호는 저장하지 않습니다. 같은 차종을 여러 대 관리하면 서로 다른 별칭을 사용하세요.")
        SectionCard("내 차량") {
            if (state.vehicleProfiles.isEmpty()) Text("등록된 차량이 없습니다. 아래에서 차량을 추가하세요.")
            state.vehicleProfiles.forEach { profile -> Card(colors = CardDefaults.cardColors(containerColor = if (state.selectedVehicleProfile?.id == profile.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) { Text(profile.alias, style = MaterialTheme.typography.titleMedium); Text(profile.description)
                    Text(profile.specification, style = MaterialTheme.typography.bodySmall)
                    Text("배터리 직접 조회: ${profile.evProfile?.label ?: "지정 안 함"}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onSelect(profile.id) }, enabled = !locked && state.selectedVehicleProfile?.id != profile.id, modifier = Modifier.fillMaxWidth()) { Text(if (state.selectedVehicleProfile?.id == profile.id) "현재 진단 차량" else "이 차량으로 진단") }
                    if (state.selectedVehicleProfile?.id == profile.id) OutlinedButton(onClick = onOpenSupport, modifier = Modifier.fillMaxWidth()) { Text("이 차량 지원 범위 보기") }
                    TextButton(onClick = { deleting = profile }, enabled = !locked) { Text("프로필 삭제") } }
            } }
            if (state.selectedVehicleProfile != null) TextButton(onClick = { onSelect(null) }, enabled = !locked) { Text("차량 선택 해제") }
        }
        SectionCard("차량 추가") {
            Text("빠른 입력", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { alias = "내 리프 EV"; manufacturer = "닛산"; model = "리프 ZE1"; year = "2019"; powertrain = VehiclePowertrain.ELECTRIC; evProfile = EvProfile.LEAF_ZE1; market = VehicleMarket.KOREA; engine = ""; transmission = "" }, modifier = Modifier.fillMaxWidth()) { Text("2019 리프 ZE1 · 검증 대상") }
            OutlinedButton(onClick = { alias = "내 니로 EV"; manufacturer = "기아"; model = "니로 EV DE"; year = "2019"; powertrain = VehiclePowertrain.ELECTRIC; evProfile = EvProfile.NIRO_DE; market = VehicleMarket.KOREA; engine = ""; transmission = "" }, modifier = Modifier.fillMaxWidth()) { Text("2019 니로 EV DE · 검증 대상") }
            OutlinedButton(onClick = { alias = "내 현대차"; manufacturer = "현대"; model = ""; year = ""; powertrain = VehiclePowertrain.GASOLINE; evProfile = null; market = VehicleMarket.KOREA; engine = ""; transmission = "" }, modifier = Modifier.fillMaxWidth()) { Text("현대 내연기관 · 차종 직접 입력") }
            OutlinedButton(onClick = { alias = "내 기아차"; manufacturer = "기아"; model = ""; year = ""; powertrain = VehiclePowertrain.GASOLINE; evProfile = null; market = VehicleMarket.KOREA; engine = ""; transmission = "" }, modifier = Modifier.fillMaxWidth()) { Text("기아 내연기관 · 차종 직접 입력") }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { makerMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedFamilyMaker?.let { "제조사: $it" } ?: "차종 목록의 제조사 선택")
                }
                DropdownMenu(expanded = makerMenuOpen, onDismissRequest = { makerMenuOpen = false }) {
                    KoreaVehicleFamilies.manufacturers.forEach { maker -> DropdownMenuItem(text = { Text(maker) }, onClick = {
                        selectedFamilyMaker = maker; makerMenuOpen = false
                    }) }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { familyMenuOpen = true }, enabled = selectedFamilyMaker != null, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedFamilyMaker?.let { "$it 차종 선택" } ?: "제조사를 먼저 선택하세요")
                }
                DropdownMenu(expanded = familyMenuOpen, onDismissRequest = { familyMenuOpen = false }) {
                    KoreaVehicleFamilies.all.filter { it.manufacturer == selectedFamilyMaker }.forEach { family -> DropdownMenuItem(text = { Text(family.modelInput) }, onClick = {
                        alias = "내 ${family.modelInput}"; manufacturer = family.manufacturer; model = family.modelInput; year = ""
                        powertrain = family.defaultPowertrain; evProfile = null; market = VehicleMarket.KOREA; engine = ""; transmission = ""
                        familyMenuOpen = false
                    }) }
                }
            }
            Text("차종 목록은 이름 입력을 돕는 분류표입니다. 연식·세대·동력계·사양은 등록증과 실제 차량에 맞게 반드시 확인하세요. 전기차·하이브리드가 명확한 일부 모델 외에는 동력계를 ‘기타’로 두어 자동 추정하지 않습니다.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(alias, { alias = it.take(40) }, label = { Text("차량 별칭") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(manufacturer, { manufacturer = it.take(40) }, label = { Text("제조사") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it.take(40) }, label = { Text("차종·세대") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("연식") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("판매 지역"); VehicleMarket.entries.forEach { item -> FilterChip(selected = market == item, onClick = { market = item }, label = { Text(item.label) }) }
            Text("동력계"); VehiclePowertrain.entries.forEach { item -> FilterChip(selected = powertrain == item, onClick = { powertrain = item; if (item != VehiclePowertrain.ELECTRIC) evProfile = null }, label = { Text(item.label) }) }
            OutlinedTextField(engine, { engine = it.take(40) }, label = { Text("엔진·배터리 사양 (선택)") }, supportingText = { Text("예: 2.5 가솔린, 81.4 kWh 4WD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(transmission, { transmission = it.take(40) }, label = { Text("변속기 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (powertrain == VehiclePowertrain.ELECTRIC) { Text("배터리 직접 조회 규격"); FilterChip(selected = evProfile == null, onClick = { evProfile = null }, label = { Text("지정 안 함") })
                EvProfile.entries.forEach { item -> FilterChip(selected = evProfile == item, onClick = { evProfile = item }, label = { Text(item.label) }) }
                Text("차종과 규격이 정확히 일치할 때만 선택하세요. 지정 안 함이면 범용 오류코드 진단만 사용합니다.", style = MaterialTheme.typography.bodySmall) }
            val candidate = runCatching { VehicleProfiles.validate(VehicleProfile(alias = alias, manufacturer = manufacturer, model = model, modelYear = year.toInt(), powertrain = powertrain, evProfile = evProfile, market = market, engine = engine, transmission = transmission)) }.getOrNull()
            val systemCandidates = candidate?.let(PowertrainSystemCatalog::assess).orEmpty()
            val domesticCandidates = candidate?.let(DomesticSpecificationCatalog::assess).orEmpty()
            if (systemCandidates.isNotEmpty()) {
                Text("공개 적용표 후보", style = MaterialTheme.typography.titleSmall)
                systemCandidates.take(3).forEach { match ->
                    Text("${match.system.label} · ${match.state.label}")
                    if (match.missingInformation.isNotEmpty()) Text("추가 확인: ${match.missingInformation.joinToString()}", style = MaterialTheme.typography.bodySmall)
                }
                Text("후보 표시는 동일한 수리 절차가 확인됐다는 뜻이 아닙니다. 저장 후 지원 범위 화면에서 원문과 조건을 확인하세요.", style = MaterialTheme.typography.bodySmall)
            }
            if (domesticCandidates.isNotEmpty()) {
                Text("대한민국 공식 사양 후보", style = MaterialTheme.typography.titleSmall)
                domesticCandidates.forEach { match ->
                    Text("${match.specification.label} · ${match.state.label}")
                    Text("${match.specification.engineLabel} · ${match.specification.transmissionLabel}", style = MaterialTheme.typography.bodySmall)
                    if (match.specification.batteryLabel.isNotBlank()) Text("${match.specification.batteryLabel} · ${match.specification.driveLabel}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { candidate?.let(onSave); alias = ""; manufacturer = ""; model = ""; evProfile = null; market = VehicleMarket.UNKNOWN; engine = ""; transmission = "" }, enabled = candidate != null && !locked, modifier = Modifier.fillMaxWidth()) { Text("차량 프로필 저장") }
        }
        SectionCard("기록 연결 방식") { Text("앞으로 생성하는 진단·배터리 기록은 현재 선택한 차량 프로필로 연결됩니다."); Text("기존 기록은 진단 기록 화면에서 차량별 분류를 계속 사용할 수 있습니다. 프로필 삭제 시 기록은 남습니다.") }
    }
}
