package com.alihaydarsayar.communesky.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API'den gelen JSON'un birebir Kotlin karşılığı (DTO = Data Transfer Object).
 * Aynı sınıf, önbelleğe (Room) JSON olarak yazılırken de kullanılır.
 */
@Serializable
data class ForecastResponseDto(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    /** Modelin bu nokta için kullandığı rakım (m); ölçümle karşılaştırmak için. */
    val elevation: Double? = null,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
    /** 15 dakikalık yağış verisi; eski önbellekte olmayabilir. */
    @SerialName("minutely_15") val minutely15: Minutely15Dto? = null,
)

@Serializable
data class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
    @SerialName("pressure_msl") val pressure: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    val visibility: Double? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("cloud_cover_low") val cloudLow: Int? = null,
    @SerialName("cloud_cover_mid") val cloudMid: Int? = null,
    @SerialName("cloud_cover_high") val cloudHigh: Int? = null,
    /** Son 15 dakikadaki yağış (mm). Kar dahil toplam; rain ve showers bunun parçalarıdır. */
    val precipitation: Double? = null,
    val rain: Double? = null,
    val showers: Double? = null,
    /** Son 15 dakikadaki kar (cm). */
    val snowfall: Double? = null,
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
    @SerialName("cloud_cover_low") val cloudLow: List<Int?> = emptyList(),
    @SerialName("cloud_cover_mid") val cloudMid: List<Int?> = emptyList(),
    @SerialName("cloud_cover_high") val cloudHigh: List<Int?> = emptyList(),
    @SerialName("sunshine_duration") val sunshineDuration: List<Double?> = emptyList(),
    /** Bir önceki saatte düşen toplam yağış (mm): 14:00 satırı 13:00–14:00 arasını anlatır. */
    val precipitation: List<Double?> = emptyList(),
    val showers: List<Double?> = emptyList(),
    val snowfall: List<Double?> = emptyList(),
)

/** 15 dakikalık dilimler: her değer bir önceki 15 dakikada düşen yağıştır (mm). */
@Serializable
data class Minutely15Dto(
    val time: List<String>,
    val precipitation: List<Double?> = emptyList(),
)

@Serializable
data class DailyDto(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("temperature_2m_max") val maxTemperature: List<Double?>,
    @SerialName("temperature_2m_min") val minTemperature: List<Double?>,
    @SerialName("precipitation_probability_max") val precipitationProbability: List<Int?>,
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    @SerialName("sunshine_duration") val sunshineDuration: List<Double?> = emptyList(),
    @SerialName("daylight_duration") val daylightDuration: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
)
