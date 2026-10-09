package com.alihaydarsayar.communesky.widget

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
 * boş görünmesin. Tasarımdaki örnekle aynı yerler (Yayla evde, Pendik'te bulunuyorsun).
 */
object WidgetSamples {

    private const val OFFSET_SECONDS = 3 * 3600

    /** Örnek veride bulunduğun yer: uzakta (Pendik), yakında (Tuzla Merkez), evde ya da ev seçilmemiş. */
    enum class Scenario { Away, Nearby, AtHome, NoHome }

    fun input(now: Instant = Instant.now(), scenario: Scenario = Scenario.Away): WidgetInput {
        val local = LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(OFFSET_SECONDS))
        val home = SavedPlace(1, "Yayla", "Tuzla", 40.8360, 29.3080, isHome = scenario != Scenario.NoHome, sortOrder = 0)
        val device = when (scenario) {
            Scenario.Nearby -> City("Tuzla Merkez", 40.8160, 29.2950)
            Scenario.AtHome -> City("Yayla", 40.8380, 29.3050)
            else -> City("Pendik", 40.8770, 29.2330)
        }
        val kadikoy = SavedPlace(2, "Kadıköy", "İstanbul", 40.9900, 29.0290, isHome = false, sortOrder = 1)
        val atakum = SavedPlace(3, "Atakum", "Samsun", 41.3320, 36.2700, isHome = false, sortOrder = 2)
        return WidgetInput(
            settings = AppSettings(),
            places = listOf(home, kadikoy, atakum),
            weather = mapOf(
                DEVICE_PLACE_ID to snapshot(DEVICE_PLACE_ID, device, true, local, 18.0, if (scenario == Scenario.Away) 61 else 3, rainSoon = true),
                1L to snapshot(1, City("Yayla", 40.8360, 29.3080), false, local, 16.0, 3),
                2L to snapshot(2, City("Kadıköy", 40.9900, 29.0290), false, local, 16.0, 2),
                3L to snapshot(3, City("Atakum", 41.3320, 36.2700), false, local, 12.0, 1),
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
            val wet = i in 2..4
            HourlyForecast(
                time = time,
                temperature = temperature - i * 0.45,
                weatherCode = if (wet) 61 else if (i == 0) code else 3,
                precipitationProbability = listOf(0, 10, 40, 60, 55, 30, 10, 0)[i % 8],
                isDay = time.hour in 7..18,
            )
        }
        val daily = (0 until 7).map { i ->
            val date = today.plusDays(i.toLong())
            DailyForecast(
                date = date,
                weatherCode = listOf(2, 61, 61, 0, 2, 2, 3)[i],
                minTemperature = listOf(14.0, 13.0, 12.0, 11.0, 13.0, 14.0, 13.0)[i] - (16 - temperature) / 2,
                maxTemperature = listOf(21.0, 19.0, 18.0, 20.0, 22.0, 21.0, 20.0)[i] - (16 - temperature) / 2,
                precipitationProbability = listOf(10, 70, 40, 0, 10, 20, 15)[i],
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
                    apparentTemperature = temperature - 1,
                    humidity = 64,
                    dewPoint = 8.0,
                    windSpeed = 14.0,
                    windDirection = 45,
                    weatherCode = code,
                    isDay = isDay,
                    pressure = 1022.0,
                    uvIndex = if (isDay) 3.0 else 0.0,
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
