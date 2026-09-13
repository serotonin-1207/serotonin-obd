package com.eunho.leafobd.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eunho.leafobd.ui.screen.BusMonitorScreen
import com.eunho.leafobd.ui.screen.BatteryScreen
import com.eunho.leafobd.ui.screen.KnowledgeScreen
import com.eunho.leafobd.ui.screen.ClearDtcScreen
import com.eunho.leafobd.ui.screen.DidScanScreen
import com.eunho.leafobd.ui.screen.UdsClearScreen
import com.eunho.leafobd.ui.screen.DeviceScreen
import com.eunho.leafobd.ui.screen.DiagnosisScreen
import com.eunho.leafobd.ui.screen.EcuScanScreen
import com.eunho.leafobd.ui.screen.HelpScreen
import com.eunho.leafobd.ui.screen.HomeScreen
import com.eunho.leafobd.ui.screen.LogScreen
import com.eunho.leafobd.ui.screen.SettingsScreen
import com.eunho.leafobd.ui.screen.UdsDtcScreen
import com.eunho.leafobd.ui.screen.VehicleProfilesScreen
import com.eunho.leafobd.ui.screen.VehicleSupportScreen
import com.eunho.leafobd.ui.screen.UnknownCodeQueueScreen
import com.eunho.leafobd.viewmodel.MainViewModel

/** 화면 경로. */
object Routes {
    const val HOME = "home"
    const val BATTERY = "battery"
    const val KNOWLEDGE = "knowledge"
    const val UNKNOWN_CODES = "unknowncodes"
    const val DEVICES = "devices"
    const val DIAGNOSIS = "diagnosis"
    const val CLEAR = "clear"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    const val VEHICLES = "vehicles"
    const val SUPPORT = "support"
    const val HELP = "help"
    const val BUS_MONITOR = "busmonitor"
    const val ECU_SCAN = "ecuscan"
    const val UDS_DTC = "udsdtc"
    const val UDS_CLEAR = "udsclear"
    const val DID_SCAN = "didscan"
}

private fun titleFor(route: String?): String = when (route) {
    Routes.BATTERY -> "배터리"
    "knowledge?code={code}", Routes.KNOWLEDGE -> "오류코드 해설"
    Routes.UNKNOWN_CODES -> "미해설 코드 대기함"
    Routes.DEVICES -> "어댑터 선택"
    Routes.DIAGNOSIS -> "진단"
    Routes.CLEAR -> "오류코드 삭제"
    Routes.LOGS -> "진단 기록"
    Routes.SETTINGS -> "설정"
    Routes.VEHICLES -> "내 차량"
    Routes.SUPPORT -> "차량 지원 범위"
    Routes.HELP -> "사용 절차"
    Routes.BUS_MONITOR -> "CAN 버스 확인"
    Routes.ECU_SCAN -> "ECU 응답 스캔"
    Routes.UDS_DTC -> "ECU 오류코드"
    Routes.UDS_CLEAR -> "ECU 오류코드 삭제"
    Routes.DID_SCAN -> "ECU 데이터 스캔"
    else -> "Serotonin OBD"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeafObdApp(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenUrl: (String) -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 모든 사용자 메시지는 한국어 스낵바로 한 번만 표시하고 소비한다.
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(currentRoute)) },
                navigationIcon = {
                    if (currentRoute != null && currentRoute != Routes.HOME) {
                        // 아이콘 폰트 의존성을 추가하지 않기 위해 텍스트 버튼을 쓴다.
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("뒤로")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    state = uiState,
                    onNavigate = navController::navigate,
                    onRefresh = viewModel::refreshPrerequisites,
                    onRequestPermission = onRequestPermission,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onOpenUrl = onOpenUrl,
                    onDismissUpdate = viewModel::dismissUpdate
                )
            }
            composable(Routes.BATTERY) { BatteryScreen(uiState, viewModel::setBatterySnapshot, viewModel::readEvBattery,
                { navController.navigate(Routes.DEVICES) }, viewModel::selectBatteryProfile, viewModel::stopBatteryRead,
                viewModel::saveBatterySnapshot, viewModel::openBatteryRecord, viewModel::deleteBatteryRecord) }
            composable(Routes.VEHICLES) {
                VehicleProfilesScreen(
                    uiState,
                    viewModel::saveVehicleProfile,
                    viewModel::selectVehicleProfile,
                    viewModel::deleteVehicleProfile,
                    onOpenSupport = { navController.navigate(Routes.SUPPORT) }
                )
            }
            composable(Routes.SUPPORT) {
                VehicleSupportScreen(
                    profile = uiState.selectedVehicleProfile,
                    onManageVehicles = { navController.navigate(Routes.VEHICLES) },
                    onOpenUrl = onOpenUrl
                )
            }
            composable("knowledge?code={code}", arguments = listOf(androidx.navigation.navArgument("code") { defaultValue = "" })) { entry ->
                KnowledgeScreen(onOpenUrl, entry.arguments?.getString("code").orEmpty(), uiState.selectedVehicleProfile)
            }
            composable(Routes.UNKNOWN_CODES) {
                UnknownCodeQueueScreen(
                    state = uiState,
                    onRefresh = viewModel::refreshUnknownCodeQueue,
                    onOpenCode = { navController.navigate("knowledge?code=${android.net.Uri.encode(it)}") },
                    onMessage = viewModel::showMessage
                )
            }
            composable(Routes.DEVICES) {
                DeviceScreen(
                    state = uiState,
                    onConnect = viewModel::connect,
                    onConnectDevice = viewModel::connectDevice,
                    onDisconnect = viewModel::disconnect,
                    onRefresh = viewModel::refreshPrerequisites,
                    onRequestPermission = onRequestPermission,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onOpenAppSettings = onOpenAppSettings
                )
            }
            composable(Routes.DIAGNOSIS) {
                DiagnosisScreen(
                    state = uiState,
                    onRunDiagnosis = viewModel::runDiagnosis,
                    onCopySummary = { viewModel.clipboardSummary() },
                    onMessage = viewModel::showMessage,
                    onGoToClear = { navController.navigate(Routes.CLEAR) },
                    onGoToLogs = { navController.navigate(Routes.LOGS) },
                    onGoToBusMonitor = { navController.navigate(Routes.BUS_MONITOR) },
                    onGoToEcuScan = { navController.navigate(Routes.ECU_SCAN) },
                    onManageProfiles = { navController.navigate(Routes.VEHICLES) },
                    onOpenCode = { navController.navigate("knowledge?code=${android.net.Uri.encode(it)}") }
                )
            }
            composable(Routes.CLEAR) {
                ClearDtcScreen(
                    state = uiState,
                    onToggleCheck = viewModel::setSafetyCheck,
                    onConfirmationChange = viewModel::setConfirmationInput,
                    onClear = viewModel::clearDtcs,
                    onGoToUdsClear = { navController.navigate(Routes.UDS_CLEAR) }
                )
            }
            composable(Routes.LOGS) {
                LogScreen(
                    state = uiState,
                    onRefresh = viewModel::refreshSavedSessions,
                    onDelete = viewModel::deleteSavedSession,
                    onRead = viewModel::readSavedSession,
                    onMessage = viewModel::showMessage
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = uiState,
                    onTestModeChange = viewModel::setTestMode,
                    onScenarioChange = viewModel::setFakeScenario,
                    onManageVehicles = { navController.navigate(Routes.VEHICLES) },
                    onProtocolChange = viewModel::setProtocol,
                    onAutoSweepChange = viewModel::setAutoProtocolSweep,
                    onHeadersChange = viewModel::setHeadersOn,
                    onReadVinChange = viewModel::setReadVin,
                    onSaveVinMaskedChange = viewModel::setSaveVinMasked,
                    onReadFreezeFrameChange = viewModel::setReadFreezeFrame,
                    onReadLiveValuesChange = viewModel::setReadLiveValues,
                    onCheckUpdatesChange = viewModel::setCheckForUpdates,
                    onCheckUpdateNow = { viewModel.checkForUpdates(silent = false) },
                    onUpdateKnowledgePack = viewModel::updateKnowledgePack,
                    onOpenUrl = onOpenUrl,
                    onOpenHelp = { navController.navigate(Routes.HELP) },
                    onOpenAppSettings = onOpenAppSettings
                )
            }
            composable(Routes.HELP) {
                HelpScreen()
            }
            composable(Routes.BUS_MONITOR) {
                BusMonitorScreen(
                    state = uiState,
                    onRun = viewModel::runCanMonitor
                )
            }
            composable(Routes.ECU_SCAN) {
                EcuScanScreen(
                    state = uiState,
                    onScan = viewModel::runEcuScan,
                    onGoToDtc = { navController.navigate(Routes.UDS_DTC) }
                )
            }
            composable(Routes.UDS_DTC) {
                UdsDtcScreen(
                    state = uiState,
                    onRead = { viewModel.runUdsDiagnostics() },
                    onGoToScan = { navController.navigate(Routes.ECU_SCAN) },
                    onGoToClear = { navController.navigate(Routes.UDS_CLEAR) },
                    onGoToDidScan = { navController.navigate(Routes.DID_SCAN) },
                    onOpenCode = { navController.navigate("knowledge?code=${android.net.Uri.encode(it)}") },
                    onMessage = viewModel::showMessage
                )
            }
            composable(Routes.UDS_CLEAR) {
                UdsClearScreen(
                    state = uiState,
                    onToggleCheck = viewModel::setUdsSafetyCheck,
                    onConfirmationChange = viewModel::setUdsConfirmationInput,
                    onClear = viewModel::runUdsClear,
                    onToggleTarget = viewModel::toggleUdsClearTarget,
                    onExtendedSessionChange = viewModel::setUdsExtendedSession
                )
            }
            composable(Routes.DID_SCAN) {
                DidScanScreen(
                    state = uiState,
                    onScan = viewModel::runDidScan,
                    onMessage = viewModel::showMessage
                )
            }
        }
    }
}
