package com.alihaydarsayar.communesky.lifestyle

import com.alihaydarsayar.communesky.R

/** Yıldız gözlemi: bu gece bulut %20 altı, ay ışığı az, görüş iyi. */
object StargazingTip : LifestyleTip {
    override val id = "stargazing"
    override val title = R.string.tip_stargazing
    override val icon = R.drawable.ic_tip_star

    override fun evaluate(day: TodayWeather): TipResult {
        val clouds = day.tonight.mapNotNull { it.cloudCover }
        if (clouds.isEmpty()) return TipResult(TipStatus.Fair, R.string.tip_stargazing_unknown)
        val cloud = clouds.average()
        val visibility = day.tonight.mapNotNull { it.visibility }.minOrNull() ?: 20_000.0
        val brightMoon = day.moonIllumination >= 0.35
        return when {
            cloud < 20 && !brightMoon && visibility >= 10_000 -> TipResult(TipStatus.Good, R.string.tip_stargazing_good, attention = 2)
            cloud < 20 && brightMoon -> TipResult(TipStatus.Fair, R.string.tip_stargazing_moon)
            cloud < 20 -> TipResult(TipStatus.Fair, R.string.tip_stargazing_hazy)
            cloud < 50 -> TipResult(TipStatus.Fair, R.string.tip_stargazing_some_cloud)
            else -> TipResult(TipStatus.Poor, R.string.tip_stargazing_cloudy)
        }
    }
}
