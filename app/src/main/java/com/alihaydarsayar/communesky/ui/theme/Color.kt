package com.alihaydarsayar.communesky.ui.theme

import androidx.compose.ui.graphics.Color

/** Gökyüzünün üstündeki yazı renkleri. Arka plan her zaman renkli olduğu için beyaz tonları. */
val TextPrimary = Color.White
val TextSecondary = Color.White.copy(alpha = 0.72f)
val TextTertiary = Color.White.copy(alpha = 0.5f)

/** Yağış ihtimali ve yağmurla ilgili vurgular. */
val RainAccent = Color(0xFF8FD3FF)

/** Uyarılar (ör. "güncellenemedi"). */
val WarningAccent = Color(0xFFFFD28A)

/** Sıcaklık çubuklarının soğuktan sıcağa renk skalası. */
val TemperatureScale = listOf(
    Color(0xFF7DD3FC),
    Color(0xFF86EFAC),
    Color(0xFFFDE047),
    Color(0xFFFB923C),
    Color(0xFFF87171),
)
