package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.local.WeatherCacheDao
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.data.observation.ObservationStore
import com.alihaydarsayar.communesky.model.Observation
import com.alihaydarsayar.communesky.model.ObservationPolicy
import kotlinx.coroutines.flow.combine
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first hava durumu deposu:
 * - Ekran her zaman önbelleği (Room) dinler; uygulama açılır açılmaz son veri görünür.
 * - [refresh] internetten yeni veriyi alıp önbelleğe yazar; ekran kendiliğinden güncellenir.
 * Her yerin kendi önbellek satırı vardır (kimliği yerin kimliği).
 */
@Singleton
class WeatherRepository @Inject constructor(
    // Retrofit'i ilk ihtiyaç anında oluşturur (Lazy); uygulama açılışını yavaşlatmaz.
    private val api: Lazy<OpenMeteoApi>,
    private val dao: WeatherCacheDao,
    private val json: Json,
    private val observationStore: ObservationStore,
) {
    /** Çözülmüş satırlar: değişmeyen yerin JSON'u her yenilemede baştan çözülmesin. */
    private val decoded = ConcurrentHashMap<Long, Pair<WeatherCacheEntity, WeatherSnapshot?>>()

    /** Önbellekteki bütün yerlerin hava durumu (yer kimliği → veri). */
    val allWeather: Flow<Map<Long, WeatherSnapshot>> = combine(
        dao.observeAll().map { entities ->
            decoded.keys.retainAll(entities.map { it.id }.toSet())
            entities.mapNotNull { entity -> entity.cachedSnapshot()?.let { entity.id to it } }.toMap()
        },
        observationStore.observeAll(),
    ) { weather, observations ->
        weather.mapValues { (id, snapshot) -> snapshot.withObservation(observations[id]) }
    }
        // JSON çözümleme ana iş parçacığını meşgul etmesin.
        .flowOn(Dispatchers.Default)

    suspend fun snapshot(placeId: Long): WeatherSnapshot? = withContext(Dispatchers.Default) {
        dao.get(placeId)?.cachedSnapshot()?.withObservation(observationStore.get(placeId))
    }

    /** "Bulunduğum yer" satırındaki son yer (izin yoksa yedek şehir); hiç yoksa null. */
    suspend fun devicePlace(): Place? = dao.get(DEVICE_PLACE_ID)?.let {
        Place(
            id = DEVICE_PLACE_ID,
            city = City(it.cityName, it.latitude, it.longitude),
            isCurrentLocation = it.isCurrentLocation,
            accuracyMeters = it.accuracyMeters,
        )
    }

    suspend fun delete(placeId: Long) = dao.delete(placeId)

    /**
     * Verilen yerlerin tahminini tek bir istekle indirir ve önbelleğe yazar.
     * Ağ hatalarında istisna fırlatır; o durumda hiçbir satır değişmez.
     */
    suspend fun refresh(places: List<Place>) {
        if (places.isEmpty()) return
        val responses = withContext(Dispatchers.IO) {
            if (places.size == 1) {
                val city = places.single().city
                listOf(api.get().getForecast(city.latitude, city.longitude))
            } else {
                api.get().getForecasts(
                    latitudes = places.joinToString(",") { it.city.latitude.toString() },
                    longitudes = places.joinToString(",") { it.city.longitude.toString() },
                )
            }
        }
        check(responses.size == places.size) { "Beklenen ${places.size} yer, gelen ${responses.size}" }
        val now = System.currentTimeMillis()
        val entities = withContext(Dispatchers.Default) {
            places.zip(responses) { place, response ->
                WeatherCacheEntity(
                    id = place.id,
                    cityName = place.city.name,
                    latitude = place.city.latitude,
                    longitude = place.city.longitude,
                    isCurrentLocation = place.isCurrentLocation,
                    forecastJson = json.encodeToString(ForecastResponseDto.serializer(), response),
                    fetchedAtMillis = now,
                    accuracyMeters = place.accuracyMeters,
                )
            }
        }
        dao.upsertAll(entities)
    }

    /**
     * İstasyon ölçümünü işler: yakın ve tazeyse anlık sıcaklık ve durum ölçümden gelir, uzaktaki
     * yağış/gök gürültüsü uyarı olarak eklenir (bkz. ObservationPolicy). Ölçüm yoksa veri olduğu gibi.
     */
    private fun WeatherSnapshot.withObservation(observation: Observation?): WeatherSnapshot {
        val use = ObservationPolicy.evaluate(
            observation = observation,
            latitude = city.latitude,
            longitude = city.longitude,
            now = Instant.now(),
            modelIsWet = forecast.current.condition.isWet,
        ) ?: return this
        val current = ObservationPolicy.applyTo(forecast.current, use, forecast.elevation)
        return copy(forecast = forecast.copy(current = current), observation = use)
    }

    private fun WeatherCacheEntity.cachedSnapshot(): WeatherSnapshot? {
        decoded[id]?.let { (entity, snapshot) -> if (entity == this) return snapshot }
        val snapshot = toSnapshot()
        decoded[id] = this to snapshot
        return snapshot
    }

    private fun WeatherCacheEntity.toSnapshot(): WeatherSnapshot? {
        // Uygulama güncellemesiyle JSON biçimi uyumsuz hale gelirse çökmek yerine veri yok say.
        val dto = runCatching {
            json.decodeFromString(ForecastResponseDto.serializer(), forecastJson)
        }.getOrNull() ?: return null
        return WeatherSnapshot(
            placeId = id,
            city = City(cityName, latitude, longitude),
            isCurrentLocation = isCurrentLocation,
            forecast = dto.toModel(),
            fetchedAt = Instant.ofEpochMilli(fetchedAtMillis),
            accuracyMeters = accuracyMeters,
        )
    }
}

/**
 * Hava durumu istenen yer. [id] önbellek satırının kimliği: "Bulunduğum yer" için
 * [DEVICE_PLACE_ID], kayıtlı yerler için kendi kimlikleri.
 */
data class Place(
    val id: Long,
    val city: City,
    val isCurrentLocation: Boolean,
    val accuracyMeters: Float? = null,
)


private fun ForecastResponseDto.toModel() = Forecast(
    current = currentToModel(),
    hourly = hourly.toModel(daily),
    daily = daily.toModel(),
    utcOffsetSeconds = utcOffsetSeconds,
    minutely = minutely15?.toModel().orEmpty(),
    elevation = elevation,
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
