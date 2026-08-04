package com.eunho.leafobd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LeafGreen80,
    secondary = LeafGreenGrey80,
    tertiary = LeafTeal80
)

private val LightColorScheme = lightColorScheme(
    primary = LeafGreen40,
    secondary = LeafGreenGrey40,
    tertiary = LeafTeal40
)

/**
 * LeafOBD 테마.
 *
 * 다이내믹 컬러(Android 12+ 배경화면 기반 색상)는 의도적으로 사용하지 않는다.
 * 안전 경고 카드와 오류 상태 색상이 기기 배경화면에 따라 달라지면
 * "위험" 표시가 눈에 띄지 않게 될 수 있기 때문이다.
 */
@Composable
fun LeafOBDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
