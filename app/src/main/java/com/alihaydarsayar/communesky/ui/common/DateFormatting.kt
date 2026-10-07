package com.alihaydarsayar.communesky.ui.common

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Saat biçimi telefonun ayarına uyar: 24 saat açıksa "14:00", kapalıysa "2 PM".
 * Desen dile göre Android tarafından seçilir.
 */
@Composable
fun rememberHourFormatter(): DateTimeFormatter = rememberTimeFormatter(withMinutes = false)

/** Dakikalı saat ("07:12" / "7:12 AM"); gün doğumu/batımı için. */
@Composable
fun rememberTimeFormatter(withMinutes: Boolean = true): DateTimeFormatter {
    val locale = currentLocale()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour, withMinutes) {
        val skeleton = when {
            is24Hour -> "Hm"
            withMinutes -> "hm"
            else -> "h a"
        }
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

/** Dile uygun ondalık sayı ("12.5" / "12,5"). */
@Composable
fun rememberDecimalFormat(maxFractionDigits: Int = 1): NumberFormat {
    val locale = currentLocale()
    return remember(locale, maxFractionDigits) {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = maxFractionDigits }
    }
}

/**
 * "5 dakika önce" gibi göreli zaman; Android'in kendi çevirileriyle, telefonun dilinde.
 * Dakikada bir kendini yeniler. Bir dakikadan yeniyse null döner.
 */
@Composable
fun relativeTimeSince(instant: Instant): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(instant) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    val millis = instant.toEpochMilli()
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) return null
    // Configuration okunur ki dil değişince metin de yenilensin.
    LocalConfiguration.current
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
