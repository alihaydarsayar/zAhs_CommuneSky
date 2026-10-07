package com.alihaydarsayar.communesky.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WeatherSceneTest {

    private val day = LocalDate.of(2026, 10, 8)
    private val sunrise = day.atTime(7, 7)
    private val sunset = day.atTime(18, 35)

    private fun sceneAt(time: LocalTime, condition: WeatherCondition = WeatherCondition.Clear) =
        WeatherScene.of(condition, LocalDateTime.of(day, time), sunrise, sunset, fallbackIsDay = true)

    @Test
    fun `clear noon is a clear day sky`() {
        val scene = sceneAt(LocalTime.NOON)
        assertEquals(SkyTheme.ClearDay, scene.theme)
        assertFalse(scene.isNight)
    }

    @Test
    fun `clear midnight is a clear night sky`() {
        val scene = sceneAt(LocalTime.MIDNIGHT)
        assertEquals(SkyTheme.ClearNight, scene.theme)
        assertTrue(scene.isNight)
    }

    @Test
    fun `golden hour around sunrise and sunset`() {
        assertEquals(SkyTheme.Sunrise, sceneAt(LocalTime.of(7, 20)).theme)
        assertEquals(SkyTheme.Sunset, sceneAt(LocalTime.of(18, 10)).theme)
        assertEquals(SkyTheme.Sunset, sceneAt(LocalTime.of(19, 0)).theme)
    }

    @Test
    fun `thunderstorm overrides time of day`() {
        assertEquals(SkyTheme.Storm, sceneAt(LocalTime.NOON, WeatherCondition.Thunderstorm).theme)
        assertEquals(SkyTheme.Storm, sceneAt(LocalTime.MIDNIGHT, WeatherCondition.Thunderstorm).theme)
    }

    @Test
    fun `rain uses day and night rain skies`() {
        assertEquals(SkyTheme.RainDay, sceneAt(LocalTime.NOON, WeatherCondition.Rain).theme)
        assertEquals(SkyTheme.RainNight, sceneAt(LocalTime.of(23, 0), WeatherCondition.HeavyRain).theme)
    }

    @Test
    fun `wmo codes map to visual groups`() {
        assertEquals(WeatherCondition.Clear, WeatherCondition.fromCode(0))
        assertEquals(WeatherCondition.PartlyCloudy, WeatherCondition.fromCode(2))
        assertEquals(WeatherCondition.Fog, WeatherCondition.fromCode(48))
        assertEquals(WeatherCondition.Drizzle, WeatherCondition.fromCode(53))
        assertEquals(WeatherCondition.HeavyRain, WeatherCondition.fromCode(82))
        assertEquals(WeatherCondition.Snow, WeatherCondition.fromCode(75))
        assertEquals(WeatherCondition.Thunderstorm, WeatherCondition.fromCode(99))
    }

    @Test
    fun `upcoming hours skip the past when the cache is old`() {
        val hours = (0 until 36).map { i ->
            HourlyForecast(day.atTime(6, 0).plusHours(i.toLong()), 20.0, 0, 0, true)
        }
        val forecast = Forecast(
            current = CurrentWeather(day.atTime(6, 0), 20.0, 20.0, 50, null, 5.0, null, 0, true, null, null, null),
            hourly = hours,
            daily = emptyList(),
            utcOffsetSeconds = 0,
        )
        val upcoming = forecast.upcomingHours(day.atTime(9, 40))
        assertEquals(day.atTime(9, 0), upcoming.first().time)
        assertEquals(24, upcoming.size)
    }
}
