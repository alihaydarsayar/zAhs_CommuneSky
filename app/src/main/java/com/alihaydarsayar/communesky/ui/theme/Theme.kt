package com.alihaydarsayar.communesky.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Uygulama her zaman renkli bir gökyüzünün üstünde çizildiği için renk şeması sabit ve koyu.
 * Sistemin koyu teması ise gökyüzünü biraz karartarak uygulanır (bkz. SkyBackground).
 */
private val SkyColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF0B1A33),
    secondary = Color(0xFFBFD9FF),
    background = Color(0xFF0B1026),
    surface = Color(0xFF16213F),
    onSurface = Color.White,
    surfaceContainerHigh = Color(0xFF1E2A4A),
)

@Composable
fun CommuneSkyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SkyColorScheme,
        typography = Typography,
        content = content,
    )
}
