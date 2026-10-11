package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.HourlyForecast
import java.time.LocalTime

/**
 * Koşu / yürüyüş için en iyi saat: bugün 06–22 arasında, hissedilen 8–22°, yağış yok, rüzgâr 25 km/sa altı ve
 * UV 6 altı koşullarının en iyi tuttuğu iki saatlik aralık.
 */
object RunTip : LifestyleTip {
    override val id = "run"
    override val title = R.string.tip_run
    override val icon = R.drawable.ic_tip_run

    /** Bir saatin tuttuğu koşul sayısı (0–4). */
    fun score(hour: HourlyForecast): Int {
        val feels = hour.apparentTemperature ?: hour.temperature
        return listOf(
            feels in 8.0..22.0,
            !hour.isWet() && hour.precipitationProbability < 30,
            (hour.windSpeed ?: 0.0) < 25.0,
            (hour.uvIndex ?: 0.0) < 6.0,
        ).count { it }
    }

    /** En iyi iki saatlik aralığın başlangıcı ve toplam puanı (0–8); iki ardışık saat yoksa null. */
    fun bestWindow(hours: List<HourlyForecast>): Pair<HourlyForecast, Int>? =
        hours.zipWithNext().filter { (a, b) -> b.time == a.time.plusHours(1) }
            .map { (a, b) -> a to score(a) + score(b) }
            .maxWithOrNull(compareBy<Pair<HourlyForecast, Int>> { it.second }.thenByDescending { it.first.time })

    override fun evaluate(day: TodayWeather): TipResult {
        if (day.airQualityPoor) return TipResult(TipStatus.Poor, R.string.tip_run_air, attention = 2)
        val best = bestWindow(day.remaining(6, 22)) ?: return TipResult(TipStatus.Poor, R.string.tip_run_none)
        val from: LocalTime = best.first.time.toLocalTime()
        val until: LocalTime = from.plusHours(2)
        return when {
            best.second == 8 -> TipResult(TipStatus.Good, R.string.tip_run_best, listOf(from, until), attention = 1)
            best.second >= 6 -> TipResult(TipStatus.Fair, R.string.tip_run_fair, listOf(from, until))
            else -> TipResult(TipStatus.Poor, R.string.tip_run_none)
        }
    }
}
