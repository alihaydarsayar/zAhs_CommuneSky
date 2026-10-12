package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R

/**
 * Şemsiye: şu anki hava, bugün kalan saatlerin simgesi, yağış ihtimali ve miktarı. Ekranda yağış görünürken
 * (şu an yağıyor ya da bir saatin simgesi yağış) "gerek yok" denmez.
 */
object UmbrellaTip : LifestyleTip {
    override val id = "umbrella"
    override val title = R.string.tip_umbrella
    override val icon = R.drawable.ic_wear_umbrella

    const val MUST_CHANCE = 60
    const val MUST_MM = 5.0
    const val TAKE_CHANCE = 40
    const val TAKE_MM = 1.0

    /** Simgesi yağış gösteren saat sayısı bu kadarsa "mutlaka al". */
    const val MUST_WET_HOURS = 3

    override fun evaluate(day: TodayWeather): TipResult {
        val hours = day.remaining(6, 23).ifEmpty { day.next48.take(12) }
        val chance = hours.maxOfOrNull { it.precipitationProbability } ?: 0
        val mm = hours.sumOf { it.precipitation ?: 0.0 }
        val wetHours = hours.count { it.showsPrecipitation }
        return when {
            day.precipitatingNow -> TipResult(TipStatus.Poor, R.string.tip_umbrella_now, attention = 3)
            day.thunderNow || hours.any { it.isThunderstorm } -> TipResult(TipStatus.Poor, R.string.tip_umbrella_thunder, attention = 3)
            chance >= MUST_CHANCE -> TipResult(TipStatus.Poor, R.string.tip_umbrella_must, listOf(chance), attention = 3)
            mm >= MUST_MM || wetHours >= MUST_WET_HOURS -> TipResult(TipStatus.Poor, R.string.tip_umbrella_must_rain, attention = 3)
            chance > TAKE_CHANCE -> TipResult(TipStatus.Fair, R.string.tip_umbrella_take, listOf(chance), attention = 2)
            mm > TAKE_MM || wetHours > 0 -> TipResult(TipStatus.Fair, R.string.tip_umbrella_take_rain, attention = 2)
            else -> TipResult(TipStatus.Good, R.string.tip_umbrella_none)
        }
    }
}
