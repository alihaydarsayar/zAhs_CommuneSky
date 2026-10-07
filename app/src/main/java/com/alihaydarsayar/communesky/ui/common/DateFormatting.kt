package com.alihaydarsayar.communesky.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Saat biçimi telefonun ayarına uyar: 24 saat açıksa "14:00", kapalıysa "2 PM".
 * Desen dile göre Android tarafından seçilir.
 */
@Composable
fun rememberHourFormatter(): DateTimeFormatter {
    val locale = currentLocale()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) {
        val skeleton = if (is24Hour) "Hm" else "h a"
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}
