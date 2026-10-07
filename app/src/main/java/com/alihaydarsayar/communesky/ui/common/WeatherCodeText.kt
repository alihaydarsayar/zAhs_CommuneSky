package com.alihaydarsayar.communesky.ui.common

import androidx.annotation.StringRes
import com.alihaydarsayar.communesky.R

/**
 * Open-Meteo'nun WMO hava kodunu metin kaynağına çevirir.
 * Metnin kendisi values/strings.xml (İngilizce) veya values-tr/strings.xml (Türkçe) içinden gelir.
 */
@StringRes
fun weatherDescriptionRes(code: Int): Int = when (code) {
    0 -> R.string.weather_clear
    1 -> R.string.weather_mainly_clear
    2 -> R.string.weather_partly_cloudy
    3 -> R.string.weather_overcast
    45, 48 -> R.string.weather_fog
    51, 53, 55 -> R.string.weather_drizzle
    56, 57 -> R.string.weather_freezing_drizzle
    61 -> R.string.weather_rain_light
    63 -> R.string.weather_rain
    65 -> R.string.weather_rain_heavy
    66, 67 -> R.string.weather_freezing_rain
    71 -> R.string.weather_snow_light
    73 -> R.string.weather_snow
    75 -> R.string.weather_snow_heavy
    77 -> R.string.weather_snow_grains
    80, 81 -> R.string.weather_rain_showers
    82 -> R.string.weather_rain_showers_violent
    85, 86 -> R.string.weather_snow_showers
    95 -> R.string.weather_thunderstorm
    96, 99 -> R.string.weather_thunderstorm_hail
    else -> R.string.weather_unknown
}

/** Geçici hava ikonu: sistem emojisi. 4. adımda kendi çizdiğimiz ikonlarla değişecek. */
fun weatherEmoji(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1 -> if (isDay) "🌤️" else "🌙"
    2 -> if (isDay) "⛅" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57, 61, 63, 66, 80, 81 -> "🌦️"
    65, 67, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}
