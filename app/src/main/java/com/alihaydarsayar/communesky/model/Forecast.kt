package com.alihaydarsayar.communesky.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Bir yer için ekranda gösterdiğimiz tüm hava durumu verisi. Saatler o yerin yerel saatidir. */
data class Forecast(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val utcOffsetSeconds: Int,
    /** 15 dakikalık yağış dilimleri; eski önbellekte boş olabilir. */
    val minutely: List<PrecipitationSlice> = emptyList(),
) {
    /** O yerde şu an saat kaç? (Telefonun saat diliminden bağımsız.) */
    fun localNow(now: Instant = Instant.now()): LocalDateTime =
        LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(utcOffsetSeconds))

    /** Önbellek eskiyse geçmiş saatleri atlayıp şimdiden itibaren [count] saati döndürür. */
    fun upcomingHours(now: LocalDateTime, count: Int = 24): List<HourlyForecast> {
        val currentHour = now.withMinute(0).withSecond(0).withNano(0)
        return hourly.filter { !it.time.isBefore(currentHour) }.take(count)
    }

    /** Bugünden başlayan günler (önbellek dünden kaldıysa dün atlanır). */
    fun upcomingDays(now: LocalDateTime): List<DailyForecast> =
        daily.filter { !it.date.isBefore(now.toLocalDate()) }

    fun today(now: LocalDateTime): DailyForecast? = daily.firstOrNull { it.date == now.toLocalDate() }

    /**
     * Önümüzdeki iki saatte yağış başlayacak mı ya da şu anki yağış dinecek mi?
     * Bir değişiklik yoksa veya veri yoksa null döner.
     */
    fun rainOutlook(now: LocalDateTime): RainOutlook? {
        val horizon = now.plus(OutlookHorizon)
        val upcoming = minutely.filter { it.end.isAfter(now) && it.start.isBefore(horizon) }
        if (upcoming.isEmpty()) return null
        val isSnow = current.condition == WeatherCondition.Snow || current.temperature <= 1.0
        return if (current.condition.isWet || current.condition == WeatherCondition.Snow) {
            val dry = upcoming.firstOrNull { !it.isWet } ?: return null
            RainOutlook(RainOutlook.Kind.Stopping, minutesUntil(now, dry.start), isSnow)
        } else {
            val wet = upcoming.firstOrNull { it.isWet } ?: return null
            RainOutlook(RainOutlook.Kind.Starting, minutesUntil(now, wet.start), isSnow)
        }
    }

    private fun minutesUntil(now: LocalDateTime, time: LocalDateTime): Int {
        val minutes = Duration.between(now, time).toMinutes().coerceAtLeast(0)
        // 5 dakikaya yuvarlıyoruz; dakikası dakikasına bir kesinlik vaat etmek dürüst olmaz.
        return (((minutes + 4) / 5) * 5).toInt()
    }

    private companion object {
        val OutlookHorizon: Duration = Duration.ofHours(2)
    }
}

/** Bir 15 dakikalık dilim: [start]–[end] arasında düşen yağış. */
data class PrecipitationSlice(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val precipitationMm: Double,
    /** Ekranda yağış göstermeye değecek kadar mı? */
    val isWet: Boolean,
)

/** "Yağmur X dakika içinde başlıyor / diniyor" bilgisi. */
data class RainOutlook(val kind: Kind, val minutes: Int, val isSnow: Boolean) {
    enum class Kind { Starting, Stopping }
}

data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val dewPoint: Double?,
    val windSpeed: Double,
    val windDirection: Int?,
    val weatherCode: Int,
    val isDay: Boolean,
    val pressure: Double?,
    val uvIndex: Double?,
    /** Metre cinsinden. */
    val visibility: Double?,
    /** Bulut oranı (%): toplam ve katmanlar (alçak, orta, yüksek). */
    val cloudCover: Int? = null,
    val cloudLow: Int? = null,
    val cloudMid: Int? = null,
    val cloudHigh: Int? = null,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

data class HourlyForecast(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int,
    val precipitationProbability: Int,
    val isDay: Boolean,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val minTemperature: Double,
    val maxTemperature: Double,
    val precipitationProbability: Int,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val uvIndexMax: Double?,
    /** Günlük toplam yağış (mm). */
    val precipitationSum: Double? = null,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

/** Önbellekten okunan, ekrana hazır hava durumu: hangi yer, ne zaman alındı. */
data class WeatherSnapshot(
    val placeId: Long,
    val city: City,
    val isCurrentLocation: Boolean,
    val forecast: Forecast,
    val fetchedAt: Instant,
    /** Sadece "Bulunduğum yer" için: konumun hata payı (metre). */
    val accuracyMeters: Float? = null,
)
