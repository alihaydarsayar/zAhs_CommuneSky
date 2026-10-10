package com.alihaydarsayar.communesky.widget

import android.content.Context
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.local.WeatherCacheEntity.Companion.DEVICE_PLACE_ID
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.City
import com.alihaydarsayar.communesky.model.CurrentWeather
import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.PrecipitationSlice
import com.alihaydarsayar.communesky.model.SavedPlace
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Örnek veri: widget seçicinin önizlemesi ve ekleme ekranı, henüz hiç hava verisi indirilmemişken
 * boş görünmesin. Örnek yerler gerçek kimseyi göstermez: ev, dile göre yazılan örnek bir ilçe
 * ([R.string.sample_place_home]: Türkçede "Beşiktaş", diğer dillerde "Besiktas"); diğer yerler
 * Liverpool ve Limerick.
 */
object WidgetSamples {

    private const val OFFSET_SECONDS = 3 * 3600

    const val LIVERPOOL = "Liverpool"
    const val LIMERICK = "Limerick"

    /** Örnek veride bulunduğun yer: uzakta (Liverpool), evin yakınında, evde ya da ev seçilmemiş. */
    enum class Scenario { Away, Nearby, AtHome, NoHome }

    fun input(context: Context, now: Instant = Instant.now(), scenario: Scenario = Scenario.Away): WidgetInput =
        input(context.getString(R.string.sample_place_home), now, scenario)

    /** [homeName]: örnek evin adı (dile göre). */
    fun input(homeName: String, now: Instant = Instant.now(), scenario: Scenario = Scenario.Away): WidgetInput {
        val local = LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(OFFSET_SECONDS))
        val home = SavedPlace(1, homeName, null, 41.0422, 29.0083, isHome = scenario != Scenario.NoHome, sortOrder = 0)
        val device = when (scenario) {
            // Evin 3 km ötesinde, adı bulunamamış bir konum.
            Scenario.Nearby -> City(null, 41.0660, 29.0260)
            Scenario.AtHome -> City(homeName, 41.0430, 29.0090)
            else -> City(LIVERPOOL, 53.4084, -2.9916)
        }
        val liverpool = SavedPlace(2, LIVERPOOL, null, 53.4084, -2.9916, isHome = false, sortOrder = 1)
        val limerick = SavedPlace(3, LIMERICK, null, 52.6638, -8.6267, isHome = false, sortOrder = 2)
        val away = scenario == Scenario.Away || scenario == Scenario.NoHome
        return WidgetInput(
            settings = AppSettings(),
            // Liverpool'dayken Liverpool ayrıca kayıtlı yer olarak tekrar etmesin.
            places = if (away) listOf(home, limerick.copy(sortOrder = 1)) else listOf(home, liverpool, limerick),
            weather = mapOf(
                DEVICE_PLACE_ID to snapshot(DEVICE_PLACE_ID, device, true, local, if (away) 13.0 else 16.0, if (away) 61 else 0, rainSoon = !away),
                1L to snapshot(1, City(homeName, 41.0422, 29.0083), false, local, 16.0, 0, rainSoon = true),
                2L to snapshot(2, City(LIVERPOOL, 53.4084, -2.9916), false, local, 13.0, 61),
                3L to snapshot(3, City(LIMERICK, 52.6638, -8.6267), false, local, 12.0, 2),
            ),
        )
    }

    private fun snapshot(
        id: Long,
        city: City,
        isCurrentLocation: Boolean,
        now: LocalDateTime,
        temperature: Double,
        code: Int,
        rainSoon: Boolean = false,
    ): WeatherSnapshot {
        val hour = now.withMinute(0).withSecond(0).withNano(0)
        val isDay = now.hour in 7..18
        val today = now.toLocalDate()
        val hourly = (0 until 24).map { i ->
            val time = hour.plusHours(i.toLong())
            val wet = rainSoon && i in 2..4
            HourlyForecast(
                time = time,
                temperature = temperature - listOf(0.0, 0.2, 1.1, 2.0, 2.1, 3.0, 3.1, 4.0)[i % 8] - i / 8,
                weatherCode = if (wet) 61 else if (i == 0) code else if (i % 3 == 0) 2 else 0,
                precipitationProbability = if (rainSoon) listOf(0, 10, 45, 70, 55, 30, 10, 0)[i % 8] else listOf(0, 0, 10, 20, 10, 0, 0, 0)[i % 8],
                isDay = time.hour in 7..18,
            )
        }
        val daily = (0 until 7).map { i ->
            val date = today.plusDays(i.toLong())
            DailyForecast(
                date = date,
                weatherCode = listOf(0, 61, 61, 2, 0, 3, 2)[i],
                minTemperature = listOf(12.0, 11.0, 11.0, 12.0, 13.0, 13.0, 12.0)[i] - (16 - temperature),
                maxTemperature = listOf(19.0, 16.0, 17.0, 20.0, 21.0, 19.0, 18.0)[i] - (16 - temperature),
                precipitationProbability = listOf(0, 70, 40, 10, 0, 20, 15)[i],
                sunrise = date.atTime(7, 12),
                sunset = date.atTime(18, 41),
                uvIndexMax = 4.0,
            )
        }
        val quarter = now.withMinute(now.minute / 15 * 15).withSecond(0).withNano(0)
        val minutely = (0 until 12).map { i ->
            val start = quarter.plusMinutes(15L * i)
            val mm = if (rainSoon) listOf(0.0, 0.0, 0.15, 0.4, 0.9, 1.2, 0.8, 0.5, 0.3, 0.1, 0.0, 0.0)[i] else 0.0
            PrecipitationSlice(start, start.plusMinutes(15), mm, isWet = mm >= 0.05)
        }
        return WeatherSnapshot(
            placeId = id,
            city = city,
            isCurrentLocation = isCurrentLocation,
            forecast = Forecast(
                current = CurrentWeather(
                    time = now,
                    temperature = temperature,
                    apparentTemperature = temperature - 2,
                    humidity = 64,
                    dewPoint = 9.0,
                    windSpeed = 14.0,
                    windDirection = 45,
                    weatherCode = code,
                    isDay = isDay,
                    pressure = 1022.0,
                    uvIndex = if (isDay) 3.0 else 1.0,
                    visibility = 20_000.0,
                ),
                hourly = hourly,
                daily = daily,
                utcOffsetSeconds = OFFSET_SECONDS,
                minutely = minutely,
            ),
            fetchedAt = Instant.now(),
            accuracyMeters = if (isCurrentLocation) 300f else null,
        )
    }
}
