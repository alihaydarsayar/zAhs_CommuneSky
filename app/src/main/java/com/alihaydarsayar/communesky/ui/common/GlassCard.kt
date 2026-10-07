package com.alihaydarsayar.communesky.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alihaydarsayar.communesky.ui.theme.TextSecondary

/** Arkadaki gökyüzü açık renkliyse true; kartlar okunabilirlik için koyu camla çizilir. */
val LocalSkyIsLight = compositionLocalOf { false }

val GlassShape = RoundedCornerShape(28.dp)

/**
 * Cam efektli kart: yarı saydam dolgu + ışığın vurduğu köşede parlayan, diğer köşede
 * kaybolan ince bir kenar. Gerçek arka plan bulanıklaştırması (blur) her karede tüm ekranı
 * yeniden işlediği için animasyonlu gökyüzüyle pil ve akıcılık açısından pahalı;
 * bu yöntem aynı hissi neredeyse sıfır maliyetle verir.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isLight = LocalSkyIsLight.current
    val fill by animateColorAsState(
        targetValue = if (isLight) Color(0xFF0A1B36).copy(alpha = 0.17f) else Color.White.copy(alpha = 0.075f),
        animationSpec = tween(1200),
        label = "glassFill",
    )
    val border = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isLight) 0.38f else 0.24f),
            Color.White.copy(alpha = 0.04f),
            Color.White.copy(alpha = 0.10f),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    Column(
        modifier = modifier
            .clip(GlassShape)
            .background(fill)
            .border(1.dp, border, GlassShape)
            .padding(contentPadding),
    ) {
        if (title != null) {
            SectionTitle(title)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** Kart başlığı: küçük, aralıklı büyük harfler (dile uygun büyük harf: "SAATLİK"). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(currentLocale()),
        color = TextSecondary,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
        modifier = modifier,
    )
}
