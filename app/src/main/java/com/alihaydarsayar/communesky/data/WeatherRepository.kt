package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.remote.CurrentDto
import com.alihaydarsayar.communesky.data.remote.OpenMeteoApi
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather

/**
 * Verinin nereden geldiğini (internet, ileride Room önbelleği) ViewModel'den saklayan katman.
 */
class WeatherRepository(
    private val api: OpenMeteoApi = OpenMeteoApi.create(),
) {
    suspend fun getCurrentWeather(city: City): CurrentWeather =
        api.getForecast(city.latitude, city.longitude).current.toModel()
}

private fun CurrentDto.toModel() = CurrentWeather(
    temperature = temperature,
    apparentTemperature = apparentTemperature,
    humidity = humidity,
    windSpeed = windSpeed,
    weatherCode = weatherCode,
    isDay = isDay == 1,
)
