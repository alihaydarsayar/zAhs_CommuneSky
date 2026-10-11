package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.HourlyForecast

/** Araba yıkama: önümüzdeki 48 saatte yağış yoksa uygun. */
object CarWashTip : LifestyleTip {
    override val id = "car_wash"
    override val title = R.string.tip_car_wash
    override val icon = R.drawable.ic_tip_car

    override fun evaluate(day: TodayWeather): TipResult {
        fun rainy(hours: List<HourlyForecast>) = hours.any { it.isWet() || it.precipitationProbability >= 50 }
        return when {
            rainy(day.next48.take(24)) -> TipResult(TipStatus.Poor, R.string.tip_car_wash_rain_soon)
            rainy(day.next48.drop(24)) -> TipResult(TipStatus.Fair, R.string.tip_car_wash_rain_later)
            else -> TipResult(TipStatus.Good, R.string.tip_car_wash_ok, attention = 1)
        }
    }
}
