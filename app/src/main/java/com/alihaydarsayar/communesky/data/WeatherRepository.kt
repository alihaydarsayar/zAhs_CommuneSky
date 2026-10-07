package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.local.WeatherCacheDao
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity
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
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first hava durumu deposu:
 * - Ekran her zaman önbelleği (Room) dinler; uygulama açılır açılmaz son veri görünür.
 * - [refresh] internetten yeni veriyi alıp önbelleğe yazar; ekran kendiliğinden güncellenir.
 */
@Singleton
class WeatherRepository @Inject constructor(
    // Retrofit'i ilk ihtiyaç anında oluşturur (Lazy); uygulama açılışını yavaşlatmaz.
    private val api: Lazy<OpenMeteoApi>,
    private val dao: WeatherCacheDao,
    private val json: Json,
) {
    /** Önbellekteki son hava durumu; hiç veri yoksa null. */
    val weather: Flow<WeatherSnapshot?> = dao.observe()
        .distinctUntilChanged()
        .map { entity -> entity?.toSnapshot() }
        // JSON çözümleme ana iş parçacığını meşgul etmesin.
        .flowOn(Dispatchers.Default)

    suspend fun currentSnapshot(): WeatherSnapshot? = withContext(Dispatchers.Default) {
        dao.get()?.toSnapshot()
    }

    suspend fun lastPlace(): Place? = dao.get()?.let {
        Place(City(it.cityName, it.latitude, it.longitude), it.isCurrentLocation)
    }

    /** İnternetten yeni tahmini alır ve önbelleğe yazar. Ağ hatalarında istisna fırlatır. */
    suspend fun refresh(place: Place) {
        val city = place.city
        val response = withContext(Dispatchers.IO) {
            api.get().getForecast(city.latitude, city.longitude)
        }
        val entity = WeatherCacheEntity(
            cityName = city.name,
            latitude = city.latitude,
            longitude = city.longitude,
            isCurrentLocation = place.isCurrentLocation,
            forecastJson = withContext(Dispatchers.Default) {
                json.encodeToString(ForecastResponseDto.serializer(), response)
            },
            fetchedAtMillis = System.currentTimeMillis(),
        )
        dao.upsert(entity)
    }

    private fun WeatherCacheEntity.toSnapshot(): WeatherSnapshot? {
        // Uygulama güncellemesiyle JSON biçimi uyumsuz hale gelirse çökmek yerine veri yok say.
        val dto = runCatching {
            json.decodeFromString(ForecastResponseDto.serializer(), forecastJson)
        }.getOrNull() ?: return null
        return WeatherSnapshot(
            city = City(cityName, latitude, longitude),
            isCurrentLocation = isCurrentLocation,
            forecast = dto.toModel(),
            fetchedAt = Instant.ofEpochMilli(fetchedAtMillis),
        )
    }
}

/** Hava durumu istenen yer ve bu yerin cihaz konumundan gelip gelmediği. */
data class Place(val city: City, val isCurrentLocation: Boolean)

private fun ForecastResponseDto.toModel() = Forecast(
    current = current.toModel(),
    hourly = hourly.toModel(),
    daily = daily.toModel(),
    utcOffsetSeconds = utcOffsetSeconds,
)

private fun CurrentDto.toModel() = CurrentWeather(
    time = LocalDateTime.parse(time),
    temperature = temperature,
    apparentTemperature = apparentTemperature,
    humidity = humidity,
    dewPoint = dewPoint,
    windSpeed = windSpeed,
    windDirection = windDirection,
    weatherCode = weatherCode,
    isDay = isDay == 1,
    pressure = pressure,
    uvIndex = uvIndex,
    visibility = visibility,
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
        sunrise = sunrise.getOrNull(i)?.let(LocalDateTime::parse),
        sunset = sunset.getOrNull(i)?.let(LocalDateTime::parse),
        uvIndexMax = uvIndexMax.getOrNull(i),
    )
}
