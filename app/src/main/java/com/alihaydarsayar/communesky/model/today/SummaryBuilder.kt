package com.alihaydarsayar.communesky.model.today

import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.WeatherAlert
import java.time.LocalDateTime

/** Özet kurma sonucu: tam özet, basit yedek özet ya da hiçbiri. */
sealed interface BuiltSummary {
    data class Full(val text: SummaryText) : BuiltSummary

    /** Tam özet kurulamadı ([error] null ise bugünün verisi yok); en düşük/en yüksek sıcaklık ve genel durum. */
    data class Basic(val title: String, val body: String, val error: Throwable?) : BuiltSummary

    /** Yedek özet de kurulamadı; [error] null ise o günün verisi tahminde yok. */
    data class Unavailable(val error: Throwable?) : BuiltSummary
}

/**
 * Özeti kurar ve hiçbir koşulda sessizce düşmez: tam özet bir istisna verirse (beklenmedik veri, eksik alan) en
 * düşük/en yüksek sıcaklık ve genel durumdan oluşan basit özet döner. Saf; test edilir.
 */
object SummaryBuilder {

    fun build(
        kind: SummaryKind,
        forecast: Forecast,
        alerts: List<WeatherAlert>,
        now: LocalDateTime,
        wearShift: Double,
        strings: Strings,
        degrees: (Double) -> Int,
        clock: (LocalDateTime) -> String,
        wetNow: Boolean = false,
    ): BuiltSummary {
        var error: Throwable? = null
        try {
            DailySummary.of(kind, forecast, alerts, now, wearShift, strings, degrees, clock, wetNow)?.let { return BuiltSummary.Full(it) }
        } catch (e: Throwable) {
            error = e
        }
        val basic = runCatching { basic(kind, forecast, now, degrees, strings) }.getOrNull()
            ?: return BuiltSummary.Unavailable(error)
        return BuiltSummary.Basic(basic.first, basic.second, error)
    }

    /** Başlık ve gövde: "12–20° · açık" ve "Ayrıntılar için dokun."; günün verisi yoksa null. */
    fun basic(kind: SummaryKind, forecast: Forecast, now: LocalDateTime, degrees: (Double) -> Int, strings: Strings): Pair<String, String>? {
        val date = if (kind == SummaryKind.Morning) now.toLocalDate() else now.toLocalDate().plusDays(1)
        val day = forecast.daily.firstOrNull { it.date == date } ?: return null
        val title = strings.get(
            if (kind == SummaryKind.Morning) R.string.summary_title else R.string.summary_title_tomorrow,
            degrees(day.minTemperature), degrees(day.maxTemperature), DailySummary.skyPhrase(day.weatherCode, strings),
        )
        return title to strings.get(R.string.summary_no_advice)
    }
}
