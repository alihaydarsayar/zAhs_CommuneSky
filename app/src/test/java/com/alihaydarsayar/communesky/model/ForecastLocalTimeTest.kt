package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * "Bugün", "Şimdi" ve saatlik şerit, telefonun değil yerin saatine göre seçilir. Yerin saat dilimi
 * telefonunkinden farklıyken (Liverpool, Limerick) ve gece yarısının iki yanında denenir.
 */
class ForecastLocalTimeTest {

    private val day = LocalDate.of(2026, 10, 11)

    /** Liverpool, yaz saati: UTC+1. Saatlik veri 11 Ekim 00:00'dan 12 Ekim 23:00'a. */
    private val liverpool = Forecast(
        current = CurrentWeather(
            time = day.atTime(23, 45), temperature = 13.0, apparentTemperature = 11.0, humidity = 64, dewPoint = 6.0,
            windSpeed = 14.0, windDirection = 200, weatherCode = 61, isDay = false, pressure = 1012.0, uvIndex = 0.0, visibility = 10_000.0,
        ),
        hourly = (0 until 48).map { HourlyForecast(day.atStartOfDay().plusHours(it.toLong()), 10.0 + it, 3, 0, isDay = it % 24 in 7..18) },
        daily = (0 until 3).map { DailyForecast(day.plusDays(it.toLong()), 3, 9.0 + it, 16.0 + it, 0, null, null, null) },
        utcOffsetSeconds = 3600,
    )

    @Test
    fun `the place's own clock decides what today is`() {
        // 22:50 UTC: Liverpool'da 23:50 (hâlâ 11 Ekim); İstanbul'daki telefonda 01:50 (12 Ekim).
        val before = liverpool.localNow(Instant.parse("2026-10-11T22:50:00Z"))
        assertEquals(LocalDateTime.of(2026, 10, 11, 23, 50), before)
        assertEquals(day, liverpool.today(before)?.date)
        assertEquals(16.0, liverpool.today(before)!!.maxTemperature, 0.0)
        assertEquals(day, liverpool.upcomingDays(before).first().date)
    }

    @Test
    fun `just after midnight there, today moves to the next day`() {
        // 23:05 UTC: Liverpool'da 00:05, 12 Ekim.
        val after = liverpool.localNow(Instant.parse("2026-10-11T23:05:00Z"))
        assertEquals(day.plusDays(1), liverpool.today(after)?.date)
        assertEquals(17.0, liverpool.today(after)!!.maxTemperature, 0.0)
        // Dün listeden düşer.
        assertEquals(listOf(day.plusDays(1), day.plusDays(2)), liverpool.upcomingDays(after).map { it.date })
    }

    @Test
    fun `the hourly strip starts at the current hour there`() {
        val before = liverpool.localNow(Instant.parse("2026-10-11T22:50:00Z"))
        assertEquals(LocalDateTime.of(2026, 10, 11, 23, 0), liverpool.upcomingHours(before, count = 3).first().time)
        val after = liverpool.localNow(Instant.parse("2026-10-11T23:05:00Z"))
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 12, 0, 0), LocalDateTime.of(2026, 10, 12, 1, 0)),
            liverpool.upcomingHours(after, count = 2).map { it.time },
        )
    }
}
