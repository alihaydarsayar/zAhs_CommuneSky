package com.alihaydarsayar.communesky.testing

import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import java.time.LocalDate
import java.time.LocalDateTime

/** Testler için küçük tahmin kurucuları. */
object TestForecast {
    val day: LocalDate = LocalDate.of(2026, 10, 12)

    fun hour(
        hour: Int,
        feels: Double = 18.0,
        date: LocalDate = day,
        chance: Int = 0,
        mm: Double = 0.0,
        wind: Double = 8.0,
        gust: Double = 15.0,
        uv: Double = 2.0,
        humidity: Int = 50,
        cloud: Int = 30,
        visibility: Double = 20_000.0,
        temperature: Double = feels,
    ) = HourlyForecast(
        time = date.atTime(hour, 0),
        temperature = temperature,
        weatherCode = 1,
        precipitationProbability = chance,
        isDay = hour in 7..18,
        apparentTemperature = feels,
        humidity = humidity,
        windSpeed = wind,
        windGust = gust,
        uvIndex = uv,
        visibility = visibility,
        cloudCover = cloud,
        precipitation = mm,
    )

    /** Bir günün 24 saati; [each] saate göre değerleri değiştirir. */
    fun hours(date: LocalDate = day, each: (Int) -> HourlyForecast = { hour(it) }): List<HourlyForecast> =
        (0..23).map { each(it).copy(time = date.atTime(it, 0)) }

    fun daily(date: LocalDate = day, min: Double = 12.0, max: Double = 20.0, mm: Double = 0.0, wind: Double? = 12.0, gust: Double? = 20.0) = DailyForecast(
        date = date,
        weatherCode = 1,
        minTemperature = min,
        maxTemperature = max,
        precipitationProbability = 0,
        sunrise = date.atTime(7, 10),
        sunset = date.atTime(18, 30),
        uvIndexMax = 4.0,
        precipitationSum = mm,
        windSpeedMax = wind,
        windGustMax = gust,
    )

    fun forecast(hourly: List<HourlyForecast>, daily: List<DailyForecast> = listOf(daily()), now: LocalDateTime = day.atTime(8, 0)) = Forecast(
        current = CurrentWeather(
            time = now,
            temperature = 18.0,
            apparentTemperature = 18.0,
            humidity = 50,
            dewPoint = 8.0,
            windSpeed = 8.0,
            windDirection = 180,
            weatherCode = 1,
            isDay = true,
            pressure = 1015.0,
            uvIndex = 2.0,
            visibility = 20_000.0,
        ),
        hourly = hourly,
        daily = daily,
        utcOffsetSeconds = 3 * 3600,
    )
}
