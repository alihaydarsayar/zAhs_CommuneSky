package com.alihaydarsayar.communesky.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** API'den gelen JSON'un birebir Kotlin karşılığı (DTO = Data Transfer Object). */
@Serializable
data class ForecastResponseDto(
    val current: CurrentDto,
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
