package com.alihaydarsayar.communesky.widget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Yazı ile zemin arasındaki kontrast (WCAG 2.1). Renkler ARGB tam sayı; saf Kotlin, test edilir.
 * Normal yazı için en az [MIN] (4,5:1) olmalı.
 */
object Contrast {

    const val MIN = 4.5

    /** Bağıl parlaklık: 0 (siyah) – 1 (beyaz). Saydamlık yok sayılır. */
    fun luminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    /** [top] rengini, saydamlığıyla birlikte opak [bottom] renginin üstüne bindirir. */
    fun composite(top: Int, bottom: Int): Int {
        val alpha = (top ushr 24) / 255.0
        fun mix(shift: Int) = ((top shr shift and 0xFF) * alpha + (bottom shr shift and 0xFF) * (1 - alpha)).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** 1:1 (aynı renk) ile 21:1 (siyah–beyaz) arasında. Yarı saydam yazı önce zemine bindirilir. */
    fun ratio(foreground: Int, background: Int): Double {
        val solidBackground = background or (0xFF shl 24)
        val a = luminance(composite(foreground, solidBackground))
        val b = luminance(solidBackground)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    fun isReadable(foreground: Int, background: Int): Boolean = ratio(foreground, background) >= MIN

    /** "#1A2B3C", "1a2b3c" ya da "#F1A2B3C4" → ARGB; geçersizse null. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (value or 0xFF000000).toInt() else value.toInt()
    }

    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}
