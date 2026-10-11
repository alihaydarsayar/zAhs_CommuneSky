package com.alihaydarsayar.communesky.model.today

import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bir değerin bugünü ve dünü. */
data class Compared(val today: Double, val yesterday: Double) {
    val difference: Double get() = today - yesterday
}

/**
 * Bugün ile dün: en yüksek sıcaklık, en yüksek hissedilen, en kuvvetli rüzgâr (km/sa), ortalama nem (%) ve
 * toplam yağış (mm). Dünün verisi aynı hava isteğiyle gelir; ek istek yoktur. Verisi olmayan alan null kalır.
 */
data class YesterdayComparison(
    val temperature: Compared?,
    val feelsLike: Compared?,
    val wind: Compared?,
    val humidity: Compared?,
    val precipitation: Compared?,
    /** En yüksek UV, ortalama görüş (m), ortalama basınç (hPa), ortalama bulut (%), gün uzunluğu (dakika). */
    val uv: Compared? = null,
    val visibility: Compared? = null,
    val pressure: Compared? = null,
    val cloud: Compared? = null,
    val dayLength: Compared? = null,
) {
    /** Ana ekrandaki satır için: bugünün en yükseği dünden kaç derece farklı (°C). */
    val temperatureDifference: Double? get() = temperature?.difference

    companion object {
        /** Fark bundan küçükse "Dünle aynı" denir (°C). */
        const val SAME = 1.0

        fun of(forecast: Forecast, now: LocalDateTime): YesterdayComparison? {
            val today = now.toLocalDate()
            val yesterday = today.minusDays(1)
            val todayHours = forecast.hourly.filter { it.time.toLocalDate() == today }
            val yesterdayHours = forecast.hourly.filter { it.time.toLocalDate() == yesterday }
            val days = forecast.daily.associateBy { it.date }

            fun daily(value: (com.alihaydarsayar.communesky.model.DailyForecast) -> Double?): Compared? {
                val a = days[today]?.let(value) ?: return null
                val b = days[yesterday]?.let(value) ?: return null
                return Compared(a, b)
            }

            // Saatlik değerlerden: iki günün de yeterince saati olmalı, yoksa karşılaştırma yanıltır.
            fun hourly(summary: (List<Double>) -> Double, value: (HourlyForecast) -> Double?): Compared? {
                val a = todayHours.mapNotNull(value)
                val b = yesterdayHours.mapNotNull(value)
                return if (a.size >= MIN_HOURS && b.size >= MIN_HOURS) Compared(summary(a), summary(b)) else null
            }

            val comparison = YesterdayComparison(
                temperature = daily { it.maxTemperature } ?: hourly({ it.max() }) { it.temperature },
                feelsLike = daily { it.apparentMax } ?: hourly({ it.max() }) { it.apparentTemperature },
                wind = daily { it.windSpeedMax } ?: hourly({ it.max() }) { it.windSpeed },
                humidity = daily { it.humidityMean } ?: hourly({ it.average() }) { it.humidity?.toDouble() },
                precipitation = daily { it.precipitationSum } ?: hourly({ it.sum() }) { it.precipitation },
                uv = daily { it.uvIndexMax } ?: hourly({ it.max() }) { it.uvIndex },
                visibility = daily { it.visibilityMean } ?: hourly({ it.average() }) { it.visibility },
                pressure = daily { it.pressureMean } ?: hourly({ it.average() }) { it.pressure },
                cloud = daily { it.cloudMean } ?: hourly({ it.average() }) { it.cloudCover?.toDouble() },
                dayLength = daily { day ->
                    val rise = day.sunrise
                    val set = day.sunset
                    if (rise != null && set != null) java.time.Duration.between(rise, set).toMinutes().toDouble() else null
                },
            )
            return comparison.takeIf { listOfNotNull(it.temperature, it.feelsLike, it.wind, it.humidity, it.precipitation).isNotEmpty() }
        }

        private const val MIN_HOURS = 20
    }
}

/** Ana ekrandaki tek satır: "Dünden 4° daha soğuk". */
sealed interface YesterdayLine {
    data object Same : YesterdayLine

    data class Warmer(val degrees: Int) : YesterdayLine

    data class Colder(val degrees: Int) : YesterdayLine

    companion object {
        /**
         * [differenceCelsius]: bugün − dün. 1°'den küçük fark "aynı" sayılır. [scale]: kullanıcının birimi için
         * çarpan (°F için 1,8); fark o birimde yuvarlanır.
         */
        fun of(differenceCelsius: Double, scale: Double = 1.0): YesterdayLine {
            if (abs(differenceCelsius) < YesterdayComparison.SAME) return Same
            val degrees = abs(differenceCelsius * scale).roundToInt().coerceAtLeast(1)
            return if (differenceCelsius > 0) Warmer(degrees) else Colder(degrees)
        }
    }
}
