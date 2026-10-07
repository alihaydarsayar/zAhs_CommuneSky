package com.alihaydarsayar.communesky.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** API'den gelen JSON'un birebir Kotlin karşılığı (DTO = Data Transfer Object). */
@Serializable
data class ForecastResponseDto(
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
)

@Serializable
data class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
)

/**
 * Saatlik ve günlük veriler "sütun" halinde gelir: her alan ayrı bir liste,
 * aynı sıradaki elemanlar aynı saate/güne aittir. Eksik veri null olabilir.
 */
@Serializable
data class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>,
    @SerialName("is_day") val isDay: List<Int?>,
)

@Serializable
data class DailyDto(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("temperature_2m_max") val maxTemperature: List<Double?>,
    @SerialName("temperature_2m_min") val minTemperature: List<Double?>,
    @SerialName("precipitation_probability_max") val precipitationProbability: List<Int?>,
)
