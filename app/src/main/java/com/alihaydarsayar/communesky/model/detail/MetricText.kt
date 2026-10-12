package com.alihaydarsayar.communesky.model.detail

import androidx.annotation.StringRes
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.today.Strings
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ayrıntı sayfalarının metinleri: başlık, tek kelimelik durum, dünle karşılaştırma cümlesi, açıklama. */
object MetricText {

    @StringRes
    fun title(metric: Metric): Int = when (metric) {
        Metric.FeelsLike -> R.string.feels_like
        Metric.Wind -> R.string.wind
        Metric.Humidity -> R.string.humidity
        Metric.Uv -> R.string.uv_index
        Metric.Visibility -> R.string.visibility
        Metric.Pressure -> R.string.pressure
        Metric.Cloud -> R.string.cloud_cover
        Metric.Precipitation -> R.string.precipitation
        Metric.Sun -> R.string.sun
    }

    /** Sekmedeki kısa ad. */
    @StringRes
    fun tab(metric: Metric): Int = when (metric) {
        Metric.Uv -> R.string.tab_uv
        Metric.Visibility -> R.string.tab_visibility
        Metric.Cloud -> R.string.tab_cloud
        else -> title(metric)
    }

    @StringRes
    fun what(metric: Metric): Int = when (metric) {
        Metric.FeelsLike -> R.string.what_feels
        Metric.Wind -> R.string.what_wind
        Metric.Humidity -> R.string.what_humidity
        Metric.Uv -> R.string.what_uv
        Metric.Visibility -> R.string.what_visibility
        Metric.Pressure -> R.string.what_pressure
        Metric.Cloud -> R.string.what_cloud
        Metric.Precipitation -> R.string.what_precipitation
        Metric.Sun -> R.string.what_sun
    }

    /** Karşılaştırılan değerin ne olduğu: "Günün en kuvvetli rüzgârı". */
    @StringRes
    fun compareCaption(metric: Metric): Int = when (metric) {
        Metric.FeelsLike -> R.string.compare_caption_feels
        Metric.Wind -> R.string.compare_caption_wind
        Metric.Humidity -> R.string.compare_caption_humidity
        Metric.Uv -> R.string.compare_caption_uv
        Metric.Visibility -> R.string.compare_caption_visibility
        Metric.Pressure -> R.string.compare_caption_pressure
        Metric.Cloud -> R.string.compare_caption_cloud
        Metric.Precipitation -> R.string.compare_caption_precipitation
        Metric.Sun -> R.string.compare_caption_sun
    }

    @StringRes
    fun uvLevel(uv: Double): Int = when {
        uv < 3 -> R.string.uv_low
        uv < 6 -> R.string.uv_moderate
        uv < 8 -> R.string.uv_high
        uv < 11 -> R.string.uv_very_high
        else -> R.string.uv_extreme
    }

    @StringRes
    fun windLevel(kmh: Double): Int = when {
        kmh < 2 -> R.string.status_wind_calm
        kmh < 12 -> R.string.status_wind_light
        kmh < 29 -> R.string.status_wind_moderate
        kmh < 50 -> R.string.status_wind_strong
        kmh < 75 -> R.string.status_wind_gale
        else -> R.string.status_wind_storm
    }

    /** Büyük değerin yanındaki kısa durum: "Hafif esinti", "Normal basınç, düşüyor", "Yüksek UV". */
    fun status(detail: MetricDetail, strings: Strings): String? {
        val value = detail.current ?: return null
        return when (detail.metric) {
            Metric.FeelsLike -> {
                val difference = value - (detail.currentSecondary ?: value)
                strings.get(
                    when {
                        difference >= 1.5 -> R.string.status_feels_warmer
                        difference <= -1.5 -> R.string.status_feels_colder
                        else -> R.string.status_feels_similar
                    },
                )
            }
            Metric.Wind -> strings.get(windLevel(value))
            Metric.Humidity -> strings.get(
                when {
                    value < 30 -> R.string.status_humidity_dry
                    value < 60 -> R.string.status_humidity_comfortable
                    value < 80 -> R.string.status_humidity_humid
                    else -> R.string.status_humidity_very_humid
                },
            )
            Metric.Uv -> strings.get(R.string.status_uv, strings.get(uvLevel(value)))
            Metric.Visibility -> strings.get(
                when {
                    value >= 20 -> R.string.status_visibility_excellent
                    value >= 8 -> R.string.status_visibility_good
                    value >= 2 -> R.string.status_visibility_moderate
                    else -> R.string.status_visibility_poor
                },
            )
            Metric.Pressure -> {
                val level = strings.get(
                    when {
                        value > 1022 -> R.string.status_pressure_high
                        value < 1005 -> R.string.status_pressure_low
                        else -> R.string.status_pressure_normal
                    },
                )
                val trend = when (detail.trend) {
                    Trend.Rising -> R.string.trend_rising
                    Trend.Falling -> R.string.trend_falling
                    Trend.Steady -> R.string.trend_steady
                    null -> null
                }
                if (trend == null) level else strings.get(R.string.status_with_trend, level, strings.get(trend))
            }
            Metric.Cloud -> strings.get(
                when {
                    value < 20 -> R.string.status_cloud_clear
                    value < 50 -> R.string.status_cloud_few
                    value < 85 -> R.string.status_cloud_partly
                    else -> R.string.status_cloud_overcast
                },
            )
            Metric.Precipitation -> {
                val sum = detail.currentSecondary ?: 0.0
                strings.get(
                    when {
                        // Miktar sıfır ama ihtimal yüksek: "%65" yazarken "Kuru bir gün" denmez.
                        sum < 0.1 && (detail.current ?: 0.0) >= com.alihaydarsayar.communesky.model.PrecipitationOutlook.POSSIBLE_CHANCE ->
                            R.string.status_precipitation_possible
                        sum < 0.1 -> R.string.status_precipitation_dry
                        sum < 2.5 -> R.string.status_precipitation_light
                        sum < 10 -> R.string.status_precipitation_wet
                        else -> R.string.status_precipitation_heavy
                    },
                )
            }
            Metric.Sun -> {
                val difference = detail.comparison?.difference ?: return null
                when {
                    difference >= 0.5 -> strings.get(R.string.status_sun_longer)
                    difference <= -0.5 -> strings.get(R.string.status_sun_shorter)
                    else -> null
                }
            }
        }
    }

    /** "Dünkünden biraz düşük"; gün uzunluğunda "Gündüz, dünden 2 dk kısa". Dünün verisi yoksa null. */
    fun comparison(detail: MetricDetail, strings: Strings): String? {
        val compared = detail.comparison ?: return null
        if (detail.metric == Metric.Sun) {
            val minutes = abs(compared.difference).roundToInt()
            return when {
                minutes < 1 -> strings.get(R.string.comparison_sun_same)
                compared.difference > 0 -> strings.get(R.string.comparison_sun_longer, minutes)
                else -> strings.get(R.string.comparison_sun_shorter, minutes)
            }
        }
        return strings.get(
            when (MetricData.level(detail.metric, compared)) {
                ComparisonLevel.Same -> R.string.comparison_same
                ComparisonLevel.Higher -> R.string.comparison_higher
                ComparisonLevel.MuchHigher -> R.string.comparison_much_higher
                ComparisonLevel.Lower -> R.string.comparison_lower
                ComparisonLevel.MuchLower -> R.string.comparison_much_lower
            },
        )
    }
}
