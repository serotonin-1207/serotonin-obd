package com.eunho.leafobd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.eunho.leafobd.bluetooth.BluetoothPermissions
import com.eunho.leafobd.ui.navigation.LeafObdApp
import com.eunho.leafobd.ui.theme.LeafOBDTheme
import com.eunho.leafobd.viewmodel.MainViewModel

/**
 * 단일 액티비티. 화면 이동은 Navigation Compose 가 담당한다.
 *
 * 액티비티가 하는 일은 세 가지뿐이다.
 *  1) 런타임 권한 요청 결과를 ViewModel 에 전달
 *  2) 시스템 설정 화면 열기
 *  3) Compose 트리 구성
 *
 * Bluetooth 통신 코드는 여기에 두지 않는다.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // 요청한 권한이 하나도 없으면(Android 11 이하) 항상 허용으로 본다.
        val granted = results.isEmpty() || results.values.all { it }
        viewModel.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeafOBDTheme {
                LeafObdApp(
                    viewModel = viewModel,
                    onRequestPermission = ::requestBluetoothPermissions,
                    onOpenBluetoothSettings = ::openBluetoothSettings,
                    onOpenAppSettings = ::openAppSettings,
                    onOpenUrl = ::openUrl
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 권한이나 Bluetooth 상태를 바꾸고 돌아왔을 수 있다.
        viewModel.refreshPrerequisites()
    }

    private fun requestBluetoothPermissions() {
        val permissions = BluetoothPermissions.required
        if (permissions.isEmpty()) {
            // Android 11 이하: 설치 시 권한이라 런타임 요청이 필요 없다.
            viewModel.onPermissionResult(true)
            return
        }
        permissionLauncher.launch(permissions)
    }

    private fun openBluetoothSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }.onFailure {
            viewModel.showMessage("Bluetooth 설정 화면을 열지 못했습니다. 직접 설정에서 열어 주십시오.")
        }
    }

    /** 다운로드·GitHub 링크를 브라우저로 연다. 앱이 직접 파일을 내려받지 않는다. */
    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            viewModel.showMessage("링크를 열지 못했습니다: $url")
        }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }.onFailure {
            viewModel.showMessage("앱 설정 화면을 열지 못했습니다. 직접 설정에서 열어 주십시오.")
        }
    }
}
