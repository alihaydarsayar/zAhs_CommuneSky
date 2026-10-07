package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.remote.CurrentDto
import com.alihaydarsayar.communesky.data.remote.DailyDto
import com.alihaydarsayar.communesky.data.remote.ForecastResponseDto
import com.alihaydarsayar.communesky.data.remote.HourlyDto
import com.alihaydarsayar.communesky.data.remote.OpenMeteoApi
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Verinin nereden geldiğini (internet, ileride Room önbelleği) ViewModel'den saklayan katman.
 */
class WeatherRepository(
    private val api: OpenMeteoApi = OpenMeteoApi.create(),
) {
    suspend fun getForecast(city: City): Forecast =
        api.getForecast(city.latitude, city.longitude).toModel()
}

private fun ForecastResponseDto.toModel() = Forecast(
    current = current.toModel(),
    hourly = hourly.toModel(),
    daily = daily.toModel(),
)

private fun CurrentDto.toModel() = CurrentWeather(
    temperature = temperature,
    apparentTemperature = apparentTemperature,
    humidity = humidity,
    windSpeed = windSpeed,
    weatherCode = weatherCode,
    isDay = isDay == 1,
)

// Sütunları satırlara çeviriyoruz; sıcaklığı veya kodu eksik olan saat/gün atlanır.
private fun HourlyDto.toModel(): List<HourlyForecast> = time.indices.mapNotNull { i ->
    HourlyForecast(
        time = LocalDateTime.parse(time[i]),
        temperature = temperature.getOrNull(i) ?: return@mapNotNull null,
        weatherCode = weatherCode.getOrNull(i) ?: return@mapNotNull null,
        precipitationProbability = precipitationProbability.getOrNull(i) ?: 0,
        isDay = isDay.getOrNull(i) == 1,
    )
}

private fun DailyDto.toModel(): List<DailyForecast> = time.indices.mapNotNull { i ->
    DailyForecast(
        date = LocalDate.parse(time[i]),
        weatherCode = weatherCode.getOrNull(i) ?: return@mapNotNull null,
        minTemperature = minTemperature.getOrNull(i) ?: return@mapNotNull null,
        maxTemperature = maxTemperature.getOrNull(i) ?: return@mapNotNull null,
        precipitationProbability = precipitationProbability.getOrNull(i) ?: 0,
    )
}
