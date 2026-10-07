package com.alihaydarsayar.communesky.model

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/** WMO hava kodlarının görsel olarak ayrışan gruplara indirgenmiş hali. */
enum class WeatherCondition {
    Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, HeavyRain, Snow, Thunderstorm;

    val isWet: Boolean get() = this == Drizzle || this == Rain || this == HeavyRain || this == Thunderstorm

    companion object {
        fun fromCode(code: Int): WeatherCondition = when (code) {
            0 -> Clear
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            51, 53, 55, 56, 57 -> Drizzle
            61, 63, 66, 80, 81 -> Rain
            65, 67, 82 -> HeavyRain
            71, 73, 75, 77, 85, 86 -> Snow
            95, 96, 99 -> Thunderstorm
            else -> Cloudy
        }
    }
}

/**
 * Arka planın ve widget'ın renk teması. Hava durumuna ve günün saatine göre seçilir.
 * [isLight] açık renkli gökyüzlerinde kartların koyu camla çizilmesi gerektiğini söyler (okunabilirlik).
 */
enum class SkyTheme(val isLight: Boolean) {
    ClearDay(isLight = true),
    ClearNight(isLight = false),
    Sunrise(isLight = true),
    Sunset(isLight = true),
    CloudyDay(isLight = true),
    CloudyNight(isLight = false),
    RainDay(isLight = true),
    RainNight(isLight = false),
    SnowDay(isLight = true),
    SnowNight(isLight = false),
    FogDay(isLight = true),
    FogNight(isLight = false),
    Storm(isLight = false),
}

/** Bir anın görsel tarifi: renk teması + hava efekti + gece mi. */
data class WeatherScene(
    val theme: SkyTheme,
    val condition: WeatherCondition,
    val isNight: Boolean,
) {
    companion object {
        /** Gün doğumu/batımına bu kadar yakınken gökyüzü turuncu-pembe tonlara döner. */
        private val GoldenHour: Duration = Duration.ofMinutes(40)

        fun of(
            condition: WeatherCondition,
            now: LocalDateTime,
            sunrise: LocalDateTime?,
            sunset: LocalDateTime?,
            fallbackIsDay: Boolean,
        ): WeatherScene {
            val isDay = if (sunrise != null && sunset != null) {
                now.isAfter(sunrise) && now.isBefore(sunset)
            } else {
                fallbackIsDay
            }
            val nearSunrise = sunrise != null && abs(Duration.between(sunrise, now).toMinutes()) <= GoldenHour.toMinutes()
            val nearSunset = sunset != null && abs(Duration.between(sunset, now).toMinutes()) <= GoldenHour.toMinutes()
            val theme = when (condition) {
                WeatherCondition.Thunderstorm -> SkyTheme.Storm
                WeatherCondition.Clear, WeatherCondition.PartlyCloudy -> when {
                    nearSunrise -> SkyTheme.Sunrise
                    nearSunset -> SkyTheme.Sunset
                    isDay -> SkyTheme.ClearDay
                    else -> SkyTheme.ClearNight
                }
                WeatherCondition.Cloudy -> if (isDay) SkyTheme.CloudyDay else SkyTheme.CloudyNight
                WeatherCondition.Fog -> if (isDay) SkyTheme.FogDay else SkyTheme.FogNight
                WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.HeavyRain ->
                    if (isDay) SkyTheme.RainDay else SkyTheme.RainNight
                WeatherCondition.Snow -> if (isDay) SkyTheme.SnowDay else SkyTheme.SnowNight
            }
            return WeatherScene(theme = theme, condition = condition, isNight = !isDay)
        }
    }
}

/** Anlık hava durumu için sahneyi, o yerin şu anki saatine göre hesaplar. */
fun WeatherSnapshot.currentScene(now: LocalDateTime = forecast.localNow()): WeatherScene {
    val today = forecast.today(now)
    return WeatherScene.of(
        condition = forecast.current.condition,
        now = now,
        sunrise = today?.sunrise,
        sunset = today?.sunset,
        fallbackIsDay = forecast.current.isDay,
    )
}
