package com.alihaydarsayar.communesky.model

/** Uygulamanın kendi hava durumu modeli; UI, API'nin JSON formatını bilmek zorunda kalmaz. */
data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val weatherCode: Int,
    val isDay: Boolean,
)
