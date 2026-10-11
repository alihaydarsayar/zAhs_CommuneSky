package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R

/** Güneş kremi: bugünün en yüksek UV değeri. */
object SunscreenTip : LifestyleTip {
    override val id = "sunscreen"
    override val title = R.string.tip_sunscreen
    override val icon = R.drawable.ic_tip_sun

    override fun evaluate(day: TodayWeather): TipResult {
        val uv = day.remaining(6, 20).mapNotNull { it.uvIndex }.maxOrNull() ?: day.today?.uvIndexMax ?: 0.0
        val rounded = Math.round(uv).toInt()
        return when {
            uv >= 6 -> TipResult(TipStatus.Poor, R.string.tip_sunscreen_must, listOf(rounded), attention = 2)
            uv >= 3 -> TipResult(TipStatus.Fair, R.string.tip_sunscreen_good_idea, listOf(rounded), attention = 1)
            else -> TipResult(TipStatus.Good, R.string.tip_sunscreen_none)
        }
    }
}
