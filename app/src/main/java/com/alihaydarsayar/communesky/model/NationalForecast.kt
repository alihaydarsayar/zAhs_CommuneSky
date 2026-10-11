package com.alihaydarsayar.communesky.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ulusal meteoroloji servisinden (Türkiye'de MGM) gelen tahmin, uyarı ve mevsim normali. Saatler
 * yerin kendi saatidir. Önbelleğe JSON olarak yazıldığı için tarih ve saatler metin olarak tutulur.
 */
@Serializable
data class NationalHour(
    /** Yerel saat, "2026-10-12T15:00". */
    val time: String,
    /** WMO hava kodu; servis vermediyse ya da tanımadığımız bir olaysa null. */
    val weatherCode: Int? = null,
    val temperature: Double? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val windDirection: Int? = null,
    val gust: Double? = null,
) {
    val at: LocalDateTime get() = LocalDateTime.parse(time)
}

@Serializable
data class NationalDay(
    /** "2026-10-12". */
    val date: String,
    val weatherCode: Int? = null,
    val maxTemperature: Double? = null,
    /** O günü izleyen gecenin en düşüğü. */
    val minTemperature: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Int? = null,
) {
    val day: LocalDate get() = LocalDate.parse(date)
}

enum class AlertLevel { Yellow, Orange, Red }

/** Meteorolojik uyarının konusu. Servisin tanımadığımız bir türü [Other] olur. */
enum class AlertEvent {
    Wind, Rain, Thunderstorm, Snow, Ice, Fog, Heat, Cold, Flood, Avalanche, Dust, Fire, Coastal, Other;

    companion object {
        /** MGM ve MeteoAlarm'ın olay adları ("thunderstorm", "rain", "hot", "snow-ice"…). */
        fun parse(name: String): AlertEvent = when (name.trim().lowercase()) {
            "wind" -> Wind
            "rain", "rain-flood" -> Rain
            "thunderstorm", "thunderstorms" -> Thunderstorm
            "snow", "snow-ice" -> Snow
            "ice", "frost", "low-temperature-ice" -> Ice
            "fog" -> Fog
            "hot", "heat", "high-temperature" -> Heat
            "cold", "low-temperature" -> Cold
            "flood", "flooding" -> Flood
            "avalanche", "avalanches" -> Avalanche
            "dust", "sand", "dust-sand" -> Dust
            "fire", "forest-fire" -> Fire
            "coastal", "coastalevent" -> Coastal
            else -> Other
        }
    }
}

@Serializable
data class WeatherAlert(
    /** Aynı uyarıyı tanımak için (bildirim bir kez gönderilir). */
    val id: String,
    val level: AlertLevel,
    val events: List<AlertEvent>,
    /** Servisin kendi açıklaması (kendi dilinde). */
    val text: String,
    /** Yerel saat. */
    val begin: String,
    val end: String,
    /** Uyarıyı yayımlayan kurumun kısa adı ("MGM"). */
    val source: String,
) {
    val beginsAt: LocalDateTime get() = LocalDateTime.parse(begin)
    val endsAt: LocalDateTime get() = LocalDateTime.parse(end)
}

/** O günün uzun yıllar ortalaması. */
@Serializable
data class SeasonalNormal(
    val month: Int,
    val day: Int,
    val maxAverage: Double,
    val minAverage: Double? = null,
) {
    /**
     * Bugünün en yükseği normalden kaç derece farklı (yuvarlanmış); fark 2°'den küçükse 0:
     * "Mevsim normallerinde". Saf hesap; test edilir.
     */
    fun difference(todayMax: Double): Int {
        val delta = todayMax - maxAverage
        return if (kotlin.math.abs(delta) < NORMAL_BAND) 0 else Math.round(delta).toInt()
    }

    companion object {
        const val NORMAL_BAND = 2.0
    }
}
