package com.alihaydarsayar.communesky.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AppSettings
import com.alihaydarsayar.communesky.model.WindUnit
import kotlin.math.roundToInt

/** Kullanıcı ayarları ekranın her yerinden okunabilsin diye en üstte sağlanır. */
val LocalAppSettings = compositionLocalOf { AppSettings() }

/** Ayarlardaki temaya göre (sistem/açık/koyu) koyu tema etkin mi? */
val LocalDarkTheme = compositionLocalOf { false }

/** °C cinsinden değeri kullanıcının seçtiği birime çevirip yuvarlar. */
@Composable
@ReadOnlyComposable
fun Double.asTemperature(): Int = LocalAppSettings.current.temperatureUnit.fromCelsius(this).roundToInt()

/** km/sa cinsinden rüzgârı kullanıcının seçtiği birime çevirip yuvarlar. */
@Composable
@ReadOnlyComposable
fun Double.asWindSpeed(): Int = LocalAppSettings.current.windUnit.fromKmh(this).roundToInt()

@get:StringRes
val WindUnit.labelRes: Int
    get() = when (this) {
        WindUnit.KilometersPerHour -> R.string.wind_unit_kmh
        WindUnit.MetersPerSecond -> R.string.wind_unit_ms
        WindUnit.MilesPerHour -> R.string.wind_unit_mph
        WindUnit.Knots -> R.string.wind_unit_knots
    }
