package com.alihaydarsayar.communesky.widget

import android.content.Context
import androidx.annotation.StringRes
import com.alihaydarsayar.communesky.R

/**
 * Renklerin adı ("Mercan kırmızısı"): ayar ekranında HEX kodu yerine ad gösterilir ve ekran okuyucu
 * rengi adıyla okur. Bilinen renklerden en yakın olanın adı verilir.
 */
object ColorNames {

    private val named: List<Pair<Int, Int>> = listOf(
        0xFFFFFFFF to R.string.color_name_white,
        0xFFF1ECE3 to R.string.color_name_bone,
        0xFFF4F2EC to R.string.color_name_bone,
        0xFFA8A39D to R.string.color_name_grey,
        0xFF6E6F75 to R.string.color_name_grey,
        0xFF141416 to R.string.color_name_black,
        0xFF000000 to R.string.color_name_black,
        0xFFFF3B3B to R.string.color_name_coral,
        0xFFD93A3F to R.string.color_name_coral,
        0xFFFF7A2F to R.string.color_name_orange,
        0xFFFFB000 to R.string.color_name_gold,
        0xFFE8B04B to R.string.color_name_gold,
        0xFFFFE14D to R.string.color_name_yellow,
        0xFF2ED3A0 to R.string.color_name_mint,
        0xFF2ED3C0 to R.string.color_name_teal,
        0xFF3D8BFF to R.string.color_name_blue,
        0xFF0F2233 to R.string.color_name_navy,
        0xFF1B2A55 to R.string.color_name_navy,
        0xFF8E6BFF to R.string.color_name_purple,
        0xFFFF5FA2 to R.string.color_name_pink,
        0xFF3F6B4A to R.string.color_name_forest,
        0xFF1C3527 to R.string.color_name_forest,
        0xFF30C85A to R.string.color_name_green,
        0xFF7A4B2A to R.string.color_name_brown,
    ).map { (color, name) -> color.toInt() to name }

    @StringRes
    fun nameRes(argb: Int): Int = named.minBy { (color, _) -> distance(color, argb) }.second

    fun name(context: Context, argb: Int): String = context.getString(nameRes(argb))

    /** Göze yakın bir uzaklık: kırmızı, yeşil ve mavi farkları göz duyarlılığıyla ağırlıklı. */
    private fun distance(a: Int, b: Int): Int {
        val r = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val g = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val bl = (a and 0xFF) - (b and 0xFF)
        return 3 * r * r + 6 * g * g + bl * bl
    }
}
