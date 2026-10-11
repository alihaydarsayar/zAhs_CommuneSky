package com.alihaydarsayar.communesky.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Hava kalitesi (CAMS, Open-Meteo üzerinden). Avrupa hava kalitesi indeksi (EAQI) ve kirleticiler (µg/m³).
 * Saatler yerin yerel saatidir, metin olarak saklanır ("2026-10-12T14:00").
 */
@Serializable
data class AirQuality(
    val hours: List<AirHour> = emptyList(),
) {
    /** [now] saatinin değerleri; o saat yoksa en yakın önceki saat. */
    fun at(now: LocalDateTime): AirHour? {
        val hour = now.withMinute(0).withSecond(0).withNano(0)
        return hours.lastOrNull { !it.at.isAfter(hour) && it.aqi != null }?.takeIf { !it.at.isBefore(hour.minusHours(MAX_AGE_HOURS)) }
    }

    /** [now] saatinden başlayan [count] saat (grafik için). */
    fun upcoming(now: LocalDateTime, count: Int = 48): List<AirHour> {
        val hour = now.withMinute(0).withSecond(0).withNano(0)
        return hours.filter { !it.at.isBefore(hour) }.take(count)
    }

    /** Bugünden başlayarak her günün en yüksek indeksi (en çok [count] gün). */
    fun days(today: LocalDate, count: Int = 3): List<AirDay> =
        hours.filter { it.aqi != null && !it.at.toLocalDate().isBefore(today) }
            .groupBy { it.at.toLocalDate() }
            .toSortedMap()
            .entries.take(count)
            .map { (date, list) -> AirDay(date, list.maxOf { it.aqi!! }) }

    companion object {
        /** Veri bundan eskiyse (saat) "şimdiki değer" sayılmaz. */
        const val MAX_AGE_HOURS = 3L
    }
}

@Serializable
data class AirHour(
    val time: String,
    val aqi: Int? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
    val no2: Double? = null,
    val so2: Double? = null,
    val co: Double? = null,
) {
    val at: LocalDateTime get() = LocalDateTime.parse(time)

    val level: AqiLevel? get() = aqi?.let { AqiLevel.of(it) }
}

data class AirDay(val date: LocalDate, val aqi: Int) {
    val level: AqiLevel get() = AqiLevel.of(aqi)
}

/** Avrupa hava kalitesi indeksinin (EAQI) basamakları: 0–20 iyi … 100 üstü son derece kötü. */
enum class AqiLevel(val from: Int) {
    Good(0),
    Fair(20),
    Moderate(40),
    Poor(60),
    VeryPoor(80),
    ExtremelyPoor(100),
    ;

    /** "Kötü" ve üstü: koşu önerilmez, istenirse bildirim gönderilir. */
    val isPoor: Boolean get() = this >= Poor

    companion object {
        fun of(aqi: Int): AqiLevel = entries.last { aqi >= it.from || it == Good }
    }
}
