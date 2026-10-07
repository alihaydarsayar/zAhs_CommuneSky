package com.alihaydarsayar.communesky.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Bir yer için ekranda gösterdiğimiz tüm hava durumu verisi. Saatler o yerin yerel saatidir. */
data class Forecast(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val utcOffsetSeconds: Int,
) {
    /** O yerde şu an saat kaç? (Telefonun saat diliminden bağımsız.) */
    fun localNow(now: Instant = Instant.now()): LocalDateTime =
        LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(utcOffsetSeconds))

    /** Önbellek eskiyse geçmiş saatleri atlayıp şimdiden itibaren [count] saati döndürür. */
    fun upcomingHours(now: LocalDateTime, count: Int = 24): List<HourlyForecast> {
        val currentHour = now.withMinute(0).withSecond(0).withNano(0)
        return hourly.filter { !it.time.isBefore(currentHour) }.take(count)
    }

    /** Bugünden başlayan günler (önbellek dünden kaldıysa dün atlanır). */
    fun upcomingDays(now: LocalDateTime): List<DailyForecast> =
        daily.filter { !it.date.isBefore(now.toLocalDate()) }

    fun today(now: LocalDateTime): DailyForecast? = daily.firstOrNull { it.date == now.toLocalDate() }
}

data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val dewPoint: Double?,
    val windSpeed: Double,
    val windDirection: Int?,
    val weatherCode: Int,
    val isDay: Boolean,
    val pressure: Double?,
    val uvIndex: Double?,
    /** Metre cinsinden. */
    val visibility: Double?,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

data class HourlyForecast(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int,
    val precipitationProbability: Int,
    val isDay: Boolean,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val minTemperature: Double,
    val maxTemperature: Double,
    val precipitationProbability: Int,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val uvIndexMax: Double?,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

/** Önbellekten okunan, ekrana hazır hava durumu: hangi yer, ne zaman alındı. */
data class WeatherSnapshot(
    val city: City,
    val isCurrentLocation: Boolean,
    val forecast: Forecast,
    val fetchedAt: Instant,
)
