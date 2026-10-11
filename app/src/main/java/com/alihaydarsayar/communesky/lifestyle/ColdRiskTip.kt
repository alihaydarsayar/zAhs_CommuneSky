package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R

/**
 * Soğuk algınlığı riski: gece–gündüz farkı 10° üstü ya da hissedilen 5° altı ve nemli. İhtiyatlı bir dille
 * söylenir; tıbbi tavsiye değildir.
 */
object ColdRiskTip : LifestyleTip {
    override val id = "cold_risk"
    override val title = R.string.tip_cold_risk
    override val icon = R.drawable.ic_tip_thermometer

    override fun evaluate(day: TodayWeather): TipResult {
        val swing = day.today?.let { it.maxTemperature - it.minTemperature } ?: 0.0
        val chilly = day.hours.any { (it.apparentTemperature ?: it.temperature) < 5.0 && (it.humidity ?: 0) >= 75 }
        return when {
            swing >= 10 -> TipResult(TipStatus.Poor, R.string.tip_cold_risk_swing, listOf(DegreeDifference(swing)), attention = 2)
            chilly -> TipResult(TipStatus.Poor, R.string.tip_cold_risk_damp, attention = 2)
            swing >= 7 -> TipResult(TipStatus.Fair, R.string.tip_cold_risk_some, listOf(DegreeDifference(swing)))
            else -> TipResult(TipStatus.Good, R.string.tip_cold_risk_low)
        }
    }
}
