package com.alihaydarsayar.communesky.model

import java.time.Duration
import java.time.LocalDateTime

/**
 * Gündüz mü gece mi, ne kadar kaldı? Gündüzse gün doğumundan batımına, geceyse batımdan bir sonraki
 * doğuma kadar olan yay üzerindeki konum ([progress], 0–1).
 */
data class SunPhase(
    val isDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val progress: Float,
    val remaining: Duration,
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val dayLength: Duration,
) {
    companion object {
        fun of(forecast: Forecast, now: LocalDateTime): SunPhase? {
            val days = forecast.daily.sortedBy { it.date }
            val todayIndex = days.indexOfFirst { it.date == now.toLocalDate() }
            if (todayIndex < 0) return null
            val today = days[todayIndex]
            val sunrise = today.sunrise ?: return null
            val sunset = today.sunset ?: return null
            val dayLength = Duration.between(sunrise, sunset)
            return when {
                now.isBefore(sunrise) -> {
                    val previousSunset = days.getOrNull(todayIndex - 1)?.sunset ?: sunset.minusDays(1)
                    night(previousSunset, sunrise, now, sunrise, sunset, dayLength)
                }
                now.isBefore(sunset) -> SunPhase(
                    isDay = true,
                    start = sunrise,
                    end = sunset,
                    progress = fraction(sunrise, sunset, now),
                    remaining = Duration.between(now, sunset),
                    sunrise = sunrise,
                    sunset = sunset,
                    dayLength = dayLength,
                )
                else -> {
                    val tomorrow = days.getOrNull(todayIndex + 1)
                    val nextSunrise = tomorrow?.sunrise ?: sunrise.plusDays(1)
                    night(sunset, nextSunrise, now, nextSunrise, sunset, tomorrow?.let {
                        if (it.sunrise != null && it.sunset != null) Duration.between(it.sunrise, it.sunset) else null
                    } ?: dayLength)
                }
            }
        }

        private fun night(
            start: LocalDateTime,
            end: LocalDateTime,
            now: LocalDateTime,
            sunrise: LocalDateTime,
            sunset: LocalDateTime,
            dayLength: Duration,
        ) = SunPhase(
            isDay = false,
            start = start,
            end = end,
            progress = fraction(start, end, now),
            remaining = Duration.between(now, end),
            sunrise = sunrise,
            sunset = sunset,
            dayLength = dayLength,
        )

        private fun fraction(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime): Float {
            val total = Duration.between(start, end).seconds.toFloat()
            if (total <= 0f) return 0f
            return (Duration.between(start, now).seconds / total).coerceIn(0f, 1f)
        }
    }
}
