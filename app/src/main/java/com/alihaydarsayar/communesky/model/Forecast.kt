package com.alihaydarsayar.communesky.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Bir şehir için ekranda gösterdiğimiz tüm hava durumu verisi. */
data class Forecast(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
)

data class HourlyForecast(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int,
    val precipitationProbability: Int,
    val isDay: Boolean,
)

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val minTemperature: Double,
    val maxTemperature: Double,
    val precipitationProbability: Int,
)
