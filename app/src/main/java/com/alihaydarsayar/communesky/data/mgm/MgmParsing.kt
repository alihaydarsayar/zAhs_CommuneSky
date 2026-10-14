package com.alihaydarsayar.communesky.data.mgm

import com.alihaydarsayar.communesky.data.observation.MgmWeatherCodes
import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.NationalDay
import com.alihaydarsayar.communesky.model.NationalHour
import com.alihaydarsayar.communesky.model.SeasonalNormal
import com.alihaydarsayar.communesky.model.WeatherAlert
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** MGM cevaplarını uygulamanın modeline çevirir. Saf mantık; test edilir. */
object MgmParsing {

    /**
     * Günlük tahminin tarihleri ve uyarıların saatleri: "2026-10-12T00:00:00.000Z" biçimindedir ama sondaki Z
     * yanlıştır; değer Türkiye saatidir (günlük tahminin tarihleri gece yarısıdır). Z atılır, yerel saat okunur.
     * Saatlik tahmin için [hourlyTime].
     */
    fun localTime(text: String?): LocalDateTime? =
        runCatching { LocalDateTime.parse(text!!.trim().removeSuffix("Z")) }.getOrNull()

    /**
     * Saatlik tahminin damgaları gerçekten UTC'dir: "2026-10-14T12:00:00.000Z" Türkiye'de 15:00'tir. 14 Ekim
     * 2026'da 81 il merkezinin MGM eğrisi aynı noktadaki ICON-EU, ECMWF ve Open-Meteo eğrileriyle karşılaştırıldı:
     * merkezlerin 69'unda en iyi uyum (korelasyon) damgalar UTC okununca çıktı, Türkiye saati okununca 11'inde;
     * ortalama fark 1,5° ile 1,9°. Günün en düşüğü de böylece sabah 06:00'ya, en yükseği öğleden sonra 15:00'e düşer.
     */
    fun hourlyTime(text: String?): LocalDateTime? =
        runCatching { Instant.parse(text!!.trim()).atZone(TURKEY).toLocalDateTime() }.getOrNull()

    private val TURKEY: ZoneId = ZoneId.of("Europe/Istanbul")

    /** -9999 (ve benzeri) "veri yok" demektir. */
    fun valid(value: Double?): Double? = value?.takeIf { it > -9000 }

    fun hours(response: List<MgmHourlyForecastDto>): List<NationalHour> =
        response.firstOrNull()?.tahmin.orEmpty().mapNotNull { hour ->
            val time = hourlyTime(hour.tarih) ?: return@mapNotNull null
            NationalHour(
                time = time.withSecond(0).withNano(0).toString(),
                weatherCode = MgmWeatherCodes.map(hour.hadise).wmoCode,
                temperature = valid(hour.sicaklik),
                humidity = valid(hour.nem)?.roundToInt(),
                windSpeed = valid(hour.ruzgarHizi),
                windDirection = valid(hour.ruzgarYonu)?.roundToInt(),
                gust = valid(hour.maksimumRuzgarHizi),
            )
        }.sortedBy { it.time }

    /**
     * Günlük tahmin: alanlar gün numarasıyla gelir (tarihGun0…tarihGun5). **Dikkat:** enDusukGunN, N. günün
     * sabaha karşı ölçülecek en düşüğüdür; bir günün gecesinin en düşüğü ertesi günün alanındadır. Son günün
     * gecesi serviste yoktur (null kalır; yedek kaynaktan gelir).
     */
    fun days(response: List<JsonObject>): List<NationalDay> {
        val json = response.firstOrNull() ?: return emptyList()
        fun number(name: String) = valid((json[name] as? JsonPrimitive)?.doubleOrNull)
        fun text(name: String) = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return (0..MAX_DAY).mapNotNull { n ->
            val date = localTime(text("tarihGun$n"))?.toLocalDate() ?: return@mapNotNull null
            NationalDay(
                date = date.toString(),
                weatherCode = MgmWeatherCodes.map(text("hadiseGun$n")).wmoCode,
                maxTemperature = number("enYuksekGun$n"),
                minTemperature = number("enDusukGun${n + 1}"),
                windSpeed = number("ruzgarHizGun$n"),
                windDirection = number("ruzgarYonGun$n")?.roundToInt(),
            )
        }.sortedBy { it.date }
    }

    private const val MAX_DAY = 5

    /** Bir ilçeyi ([centerId]) kapsayan uyarılar; aynı uyarı iki listede de varsa bir kez. En ağır renk önce. */
    fun alerts(responses: List<MgmAlertDto>, centerId: Int): List<WeatherAlert> = responses.flatMap { alert ->
        AlertLevel.entries.mapNotNull { level ->
            val key = level.name.lowercase()
            val towns = (alert.towns?.get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            if (centerId !in towns) return@mapNotNull null
            val begin = localTime(alert.begin) ?: return@mapNotNull null
            val end = localTime(alert.end) ?: return@mapNotNull null
            WeatherAlert(
                id = "mgm-${alert.alertNo ?: alert.id}-$key",
                level = level,
                events = (alert.weather?.get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.map(AlertEvent::parse).distinct(),
                text = (alert.text?.get(key) as? JsonPrimitive)?.content.orEmpty(),
                begin = begin.withSecond(0).withNano(0).toString(),
                end = end.withSecond(0).withNano(0).toString(),
                source = "MGM",
            )
        }
    }.distinctBy { it.id }.sortedWith(compareByDescending<WeatherAlert> { it.level }.thenBy { it.begin })

    fun normal(response: List<MgmNormalDto>): SeasonalNormal? {
        val dto = response.firstOrNull() ?: return null
        return SeasonalNormal(
            month = dto.ay ?: return null,
            day = dto.gun ?: return null,
            maxAverage = valid(dto.maxOrt) ?: return null,
            minAverage = valid(dto.minOrt),
        )
    }
}
