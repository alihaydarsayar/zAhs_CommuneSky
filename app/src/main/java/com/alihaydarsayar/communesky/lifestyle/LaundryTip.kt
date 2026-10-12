package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R

/** Çamaşır: bugün açık havada kurur mu? Yağış yok, nem %70 altı, rüzgâr ya da güneş var. */
object LaundryTip : LifestyleTip {
    override val id = "laundry"
    override val title = R.string.tip_laundry
    override val icon = R.drawable.ic_tip_laundry

    override fun evaluate(day: TodayWeather): TipResult {
        val hours = day.remaining(9, 19)
        if (hours.size < 3) return TipResult(TipStatus.Poor, R.string.tip_laundry_late)
        val rain = day.precipitatingNow || hours.any { it.isWet() || it.precipitationProbability >= 50 }
        val humidity = hours.mapNotNull { it.humidity }.average().takeIf { !it.isNaN() } ?: 60.0
        val breezy = hours.mapNotNull { it.windSpeed }.average().let { !it.isNaN() && it >= 8.0 }
        val sunny = hours.mapNotNull { it.cloudCover }.average().let { !it.isNaN() && it < 50.0 }
        return when {
            rain -> TipResult(TipStatus.Poor, R.string.tip_laundry_rain, attention = 1)
            humidity < 70 && (breezy || sunny) -> TipResult(TipStatus.Good, R.string.tip_laundry_dries, attention = 1)
            else -> TipResult(TipStatus.Fair, R.string.tip_laundry_slow)
        }
    }
}
