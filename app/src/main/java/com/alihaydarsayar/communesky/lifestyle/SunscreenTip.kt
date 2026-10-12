package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R

/** Güneş kremi: bugün kalan saatlerin en yüksek UV değeri. Akşam olduysa (kalan saat yoksa) gerek yoktur. */
object SunscreenTip : LifestyleTip {
    override val id = "sunscreen"
    override val title = R.string.tip_sunscreen
    override val icon = R.drawable.ic_tip_sun

    override fun evaluate(day: TodayWeather): TipResult {
        val hours = day.remaining(6, 20)
        // Günün en yüksek değeri yalnızca saatlik UV verisi yokken kullanılır; güneş battıktan sonra öğlenin değeri sayılmaz.
        val uv = if (hours.isEmpty()) 0.0 else hours.mapNotNull { it.uvIndex }.maxOrNull() ?: day.today?.uvIndexMax ?: 0.0
        val rounded = Math.round(uv).toInt()
        return when {
            uv >= 6 -> TipResult(TipStatus.Poor, R.string.tip_sunscreen_must, listOf(rounded), attention = 2)
            uv >= 3 -> TipResult(TipStatus.Fair, R.string.tip_sunscreen_good_idea, listOf(rounded), attention = 1)
            else -> TipResult(TipStatus.Good, R.string.tip_sunscreen_none)
        }
    }
}
