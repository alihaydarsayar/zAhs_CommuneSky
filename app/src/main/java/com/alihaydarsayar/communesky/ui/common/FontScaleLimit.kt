package com.alihaydarsayar.communesky.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Telefonun yazı boyutu ayarı çok büyükken, sütunları sabit genişlikli bölümlerde (saatlik ve günlük satırlar,
 * karolar) yazılar birbirine biner ve kesilir. Bu bölümlerde yazı en çok aşağıdaki ölçeğe kadar büyür; öteki
 * her yerde (başlıklar, cümleler, açıklamalar) telefonun ayarı aynen uygulanır. Normal yazı boyutunda hiçbir
 * şey değişmez.
 */
object FontScaleLimits {
    /** Saatlik ve 7 günlük tahmin satırları. */
    const val ROWS = 1.3f

    /** İkişerli karolar (yağış, hissedilen, rüzgâr…). */
    const val TILES = 1.2f

    /** [limit]'i aşmayan yazı ölçeği. Saf mantık; test edilir. */
    fun limited(fontScale: Float, limit: Float): Float = if (fontScale > limit) limit else fontScale
}

/** [content] içindeki yazılar en çok [limit] ölçeğine kadar büyür. */
@Composable
fun LimitFontScale(limit: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = FontScaleLimits.limited(density.fontScale, limit)
    if (scale == density.fontScale) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale), content = content)
    }
}
