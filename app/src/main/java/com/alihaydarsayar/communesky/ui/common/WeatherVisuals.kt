package com.alihaydarsayar.communesky.ui.common

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.SkyTheme
import com.alihaydarsayar.communesky.model.WeatherCondition

/** Hava durumu ikonları res/drawable/ic_wx_*.xml; uygulama ve widget aynı ikonları kullanır. */
@DrawableRes
fun WeatherCondition.iconRes(isNight: Boolean): Int = when (this) {
    WeatherCondition.Clear -> if (isNight) R.drawable.ic_wx_clear_night else R.drawable.ic_wx_clear_day
    WeatherCondition.PartlyCloudy ->
        if (isNight) R.drawable.ic_wx_partly_cloudy_night else R.drawable.ic_wx_partly_cloudy_day
    WeatherCondition.Cloudy -> R.drawable.ic_wx_cloudy
    WeatherCondition.Fog -> R.drawable.ic_wx_fog
    WeatherCondition.Drizzle -> R.drawable.ic_wx_drizzle
    WeatherCondition.Rain -> R.drawable.ic_wx_rain
    WeatherCondition.HeavyRain -> R.drawable.ic_wx_heavy_rain
    WeatherCondition.Snow -> R.drawable.ic_wx_snow
    WeatherCondition.Thunderstorm -> R.drawable.ic_wx_thunderstorm
}

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

/** Gökyüzü gradyanının üst, orta ve alt renkleri (res/values/sky_colors.xml). */
data class SkyColorRes(@ColorRes val top: Int, @ColorRes val mid: Int, @ColorRes val bottom: Int)

val SkyTheme.colorRes: SkyColorRes
    get() = when (this) {
        SkyTheme.ClearDay -> SkyColorRes(R.color.sky_clear_day_top, R.color.sky_clear_day_mid, R.color.sky_clear_day_bottom)
        SkyTheme.ClearNight -> SkyColorRes(R.color.sky_clear_night_top, R.color.sky_clear_night_mid, R.color.sky_clear_night_bottom)
        SkyTheme.Sunrise -> SkyColorRes(R.color.sky_sunrise_top, R.color.sky_sunrise_mid, R.color.sky_sunrise_bottom)
        SkyTheme.Sunset -> SkyColorRes(R.color.sky_sunset_top, R.color.sky_sunset_mid, R.color.sky_sunset_bottom)
        SkyTheme.CloudyDay -> SkyColorRes(R.color.sky_cloudy_day_top, R.color.sky_cloudy_day_mid, R.color.sky_cloudy_day_bottom)
        SkyTheme.CloudyNight -> SkyColorRes(R.color.sky_cloudy_night_top, R.color.sky_cloudy_night_mid, R.color.sky_cloudy_night_bottom)
        SkyTheme.RainDay -> SkyColorRes(R.color.sky_rain_day_top, R.color.sky_rain_day_mid, R.color.sky_rain_day_bottom)
        SkyTheme.RainNight -> SkyColorRes(R.color.sky_rain_night_top, R.color.sky_rain_night_mid, R.color.sky_rain_night_bottom)
        SkyTheme.SnowDay -> SkyColorRes(R.color.sky_snow_day_top, R.color.sky_snow_day_mid, R.color.sky_snow_day_bottom)
        SkyTheme.SnowNight -> SkyColorRes(R.color.sky_snow_night_top, R.color.sky_snow_night_mid, R.color.sky_snow_night_bottom)
        SkyTheme.FogDay -> SkyColorRes(R.color.sky_fog_day_top, R.color.sky_fog_day_mid, R.color.sky_fog_day_bottom)
        SkyTheme.FogNight -> SkyColorRes(R.color.sky_fog_night_top, R.color.sky_fog_night_mid, R.color.sky_fog_night_bottom)
        SkyTheme.Storm -> SkyColorRes(R.color.sky_storm_top, R.color.sky_storm_mid, R.color.sky_storm_bottom)
    }
