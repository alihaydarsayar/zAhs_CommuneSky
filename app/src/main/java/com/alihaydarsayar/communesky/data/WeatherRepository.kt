package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.local.WeatherCacheDao
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity
import com.alihaydarsayar.communesky.data.remote.DailyDto
import com.alihaydarsayar.communesky.data.remote.ForecastResponseDto
import com.alihaydarsayar.communesky.data.remote.HourlyDto
import com.alihaydarsayar.communesky.data.remote.Minutely15Dto
import com.alihaydarsayar.communesky.data.remote.OpenMeteoApi
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.PrecipitationSlice
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
    current = currentToModel(),
    hourly = hourly.toModel(daily),
    daily = daily.toModel(),
    utcOffsetSeconds = utcOffsetSeconds,
    minutely = minutely15?.toModel().orEmpty(),
)

private fun ForecastResponseDto.currentToModel(): CurrentWeather {
    val time = LocalDateTime.parse(current.time)
    val (sunrise, sunset) = daily.sunTimes(time.toLocalDate())
    // Anlık veride güneşlenme süresi yok; içinde bulunduğumuz saatin değerini kullanıyoruz.
    val hourStart = time.withMinute(0)
    val sunshine = hourly.time.indexOfFirst { LocalDateTime.parse(it) == hourStart }
        .takeIf { it >= 0 }
        ?.let { hourly.sunshineDuration.getOrNull(it) }
    return CurrentWeather(
        time = time,
        temperature = current.temperature,
        apparentTemperature = current.apparentTemperature,
        humidity = current.humidity,
        dewPoint = current.dewPoint,
        windSpeed = current.windSpeed,
        windDirection = current.windDirection,
        // Önce ince bulut düzeltmesi (sadece açar), sonra yağış kontrolü (sadece yağış ekler).
        // Anlık yağış değerleri son 15 dakikanın toplamıdır.
        weatherCode = RainDetection.code(
            code = SkyCorrection.hourlyCode(
                code = current.weatherCode,
                cloudLow = current.cloudLow,
                cloudMid = current.cloudMid,
                cloudHigh = current.cloudHigh,
                sunshineSeconds = sunshine,
                hourStart = hourStart,
                sunrise = sunrise,
                sunset = sunset,
            ),
            precipitationMm = current.precipitation,
            showersMm = current.showers,
            snowfallCm = current.snowfall,
            sliceHours = 0.25,
        ),
        isDay = current.isDay == 1,
        pressure = current.pressure,
        uvIndex = current.uvIndex,
        visibility = current.visibility,
        cloudCover = current.cloudCover,
        cloudLow = current.cloudLow,
        cloudMid = current.cloudMid,
        cloudHigh = current.cloudHigh,
    )
}

private fun DailyDto.sunTimes(date: LocalDate): Pair<LocalDateTime?, LocalDateTime?> {
    val index = time.indexOf(date.toString())
    if (index < 0) return null to null
    return sunrise.getOrNull(index)?.let(LocalDateTime::parse) to
        sunset.getOrNull(index)?.let(LocalDateTime::parse)
}

// Sütunları satırlara çeviriyoruz; sıcaklığı veya kodu eksik olan saat/gün atlanır.
private fun HourlyDto.toModel(daily: DailyDto): List<HourlyForecast> = time.indices.mapNotNull { i ->
    val hourStart = LocalDateTime.parse(time[i])
    val (sunrise, sunset) = daily.sunTimes(hourStart.toLocalDate())
    HourlyForecast(
        time = hourStart,
        temperature = temperature.getOrNull(i) ?: return@mapNotNull null,
        // Open-Meteo'da yağış "bir önceki saatin toplamı"dır: 14:00–15:00 arası 15:00 satırındadır.
        weatherCode = RainDetection.code(
            code = SkyCorrection.hourlyCode(
                code = weatherCode.getOrNull(i) ?: return@mapNotNull null,
                cloudLow = cloudLow.getOrNull(i),
                cloudMid = cloudMid.getOrNull(i),
                cloudHigh = cloudHigh.getOrNull(i),
                sunshineSeconds = sunshineDuration.getOrNull(i),
                hourStart = hourStart,
                sunrise = sunrise,
                sunset = sunset,
            ),
            precipitationMm = precipitation.getOrNull(i + 1),
            showersMm = showers.getOrNull(i + 1),
            snowfallCm = snowfall.getOrNull(i + 1),
            sliceHours = 1.0,
        ),
        precipitationProbability = precipitationProbability.getOrNull(i) ?: 0,
        isDay = isDay.getOrNull(i) == 1,
    )
}

// Her değer bir önceki 15 dakikanın toplamı; dilimin bitiş zamanı satırın kendi saatidir.
private fun Minutely15Dto.toModel(): List<PrecipitationSlice> = time.indices.mapNotNull { i ->
    val end = LocalDateTime.parse(time[i])
    val amount = precipitation.getOrNull(i) ?: return@mapNotNull null
    PrecipitationSlice(
        start = end.minusMinutes(15),
        end = end,
        precipitationMm = amount,
        isWet = RainDetection.isWet(amount, sliceHours = 0.25),
    )
}

private fun DailyDto.toModel(): List<DailyForecast> = time.indices.mapNotNull { i ->
    DailyForecast(
        date = LocalDate.parse(time[i]),
        weatherCode = SkyCorrection.dailyCode(
            code = weatherCode.getOrNull(i) ?: return@mapNotNull null,
            sunshineSeconds = sunshineDuration.getOrNull(i),
            daylightSeconds = daylightDuration.getOrNull(i),
        ),
        minTemperature = minTemperature.getOrNull(i) ?: return@mapNotNull null,
        maxTemperature = maxTemperature.getOrNull(i) ?: return@mapNotNull null,
        precipitationProbability = precipitationProbability.getOrNull(i) ?: 0,
        sunrise = sunrise.getOrNull(i)?.let(LocalDateTime::parse),
        sunset = sunset.getOrNull(i)?.let(LocalDateTime::parse),
        uvIndexMax = uvIndexMax.getOrNull(i),
        precipitationSum = precipitationSum.getOrNull(i),
    )
}
