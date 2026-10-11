package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R

/** Şemsiye: bugün kalan saatlerin yağış ihtimali ve miktarı. */
object UmbrellaTip : LifestyleTip {
    override val id = "umbrella"
    override val title = R.string.tip_umbrella
    override val icon = R.drawable.ic_wear_umbrella

    const val MUST_CHANCE = 60
    const val MUST_MM = 5.0
    const val TAKE_CHANCE = 40
    const val TAKE_MM = 1.0

    override fun evaluate(day: TodayWeather): TipResult {
        val hours = day.remaining(6, 23).ifEmpty { day.next48.take(12) }
        val chance = hours.maxOfOrNull { it.precipitationProbability } ?: 0
        val mm = hours.sumOf { it.precipitation ?: 0.0 }
        return when {
            chance >= MUST_CHANCE -> TipResult(TipStatus.Poor, R.string.tip_umbrella_must, listOf(chance), attention = 3)
            mm >= MUST_MM -> TipResult(TipStatus.Poor, R.string.tip_umbrella_must_rain, attention = 3)
            chance > TAKE_CHANCE -> TipResult(TipStatus.Fair, R.string.tip_umbrella_take, listOf(chance), attention = 2)
            mm > TAKE_MM -> TipResult(TipStatus.Fair, R.string.tip_umbrella_take_rain, attention = 2)
            else -> TipResult(TipStatus.Good, R.string.tip_umbrella_none)
        }
    }
}
