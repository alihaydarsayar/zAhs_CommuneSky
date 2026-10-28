package com.alihaydarsayar.communesky.model.today

import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.WeatherAlert
import com.alihaydarsayar.communesky.model.WeatherCondition
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/** Sabah özeti bugünü, akşam özeti yarını anlatır. */
enum class SummaryKind { Morning, Evening }

/** Bir gün diliminin özeti: simge, ortalama sıcaklık, en yüksek yağış ihtimali (önemsizse null). */
data class SummarySlice(val part: DayPart, val weatherCode: Int, val isDay: Boolean, val temperature: Double, val chance: Int?)

/** Günün en önemli olayı; başlıkta söylenir. Sıra: uyarı, gök gürültüsü, yağış, rüzgâr, sıcaklık farkı, UV, sis, gökyüzü. */
sealed interface SummaryEvent {
    data class Warning(val alert: WeatherAlert) : SummaryEvent
    data class Thunder(val from: LocalDateTime, val until: LocalDateTime, val chance: Int) : SummaryEvent
    data class Precipitation(val kind: Kind, val from: LocalDateTime, val until: LocalDateTime, val chance: Int) : SummaryEvent {
        enum class Kind { Rain, Showers, Snow }
    }
    data class Wind(val gust: Double) : SummaryEvent
    data class Change(val degrees: Int) : SummaryEvent
    data class Uv(val index: Double) : SummaryEvent
    data object Fog : SummaryEvent
    data class Sky(val weatherCode: Int) : SummaryEvent
}

/** Bildirimin bütün metinleri; saatler ve dereceler kullanıcının biçimiyle. */
data class SummaryText(
    val date: LocalDate,
    val title: String,
    /** Kapalı görünümdeki tek satır. */
    val body: String,
    val event: SummaryEvent,
    /** O gün için geçerli en ağır uyarı (uyarı bildirimleri kapalı olsa da özette görünür). */
    val warning: WeatherAlert?,
    val slices: List<SummarySlice>,
    /** Açık görünümün satırları: olayın saati, giyim, fark, güneş. */
    val lines: List<String>,
    /** Günün simgesi (kapalı görünümün sağında). */
    val dayCode: Int,
)

/**
 * Sabah ve akşam özetinin içeriği: telefondaki tahminden, ek istek olmadan. Saf; test edilir.
 */
object DailySummary {

    /** "Yağış var" sayılan ihtimal. */
    const val RAIN_CHANCE = 40
    const val STRONG_GUST = 50.0
    const val BIG_CHANGE = 5
    const val HIGH_UV = 8.0

    /** Dilimde gösterilmeye değer en düşük yağış ihtimali. */
    private const val SHOWN_CHANCE = 20

    fun of(
        kind: SummaryKind,
        forecast: Forecast,
        alerts: List<WeatherAlert>,
        now: LocalDateTime,
        wearShift: Double,
        strings: Strings,
        degrees: (Double) -> Int,
        clock: (LocalDateTime) -> String,
        /** Şu an yağıyor (taze anlık hava): şemsiye önerisine girer, ana ekrandaki öneriyle aynı. */
        wetNow: Boolean = false,
    ): SummaryText? {
        val date = if (kind == SummaryKind.Morning) now.toLocalDate() else now.toLocalDate().plusDays(1)
        val day = forecast.daily.firstOrNull { it.date == date } ?: return null
        val before = forecast.daily.firstOrNull { it.date == date.minusDays(1) }
        // Sabah özeti, geçmiş saatleri söylemez; akşam özeti yarının bütün gününe bakar.
        val from = if (kind == SummaryKind.Morning) now.withMinute(0).withSecond(0).withNano(0) else date.atStartOfDay()
        val hours = forecast.hourly.filter { it.time.toLocalDate() == date && !it.time.isBefore(from) }.sortedBy { it.time }
        val change = before?.let { (degrees(day.maxTemperature) - degrees(it.maxTemperature)) }
        val warning = alerts.filter { it.beginsAt.isBefore(date.plusDays(1).atStartOfDay()) && it.endsAt.isAfter(from) }.maxByOrNull { it.level }
        val event = event(hours, warning, change, day.weatherCode)

        val range = strings.get(
            if (kind == SummaryKind.Morning) R.string.summary_title else R.string.summary_title_tomorrow,
            degrees(day.minTemperature), degrees(day.maxTemperature), phrase(event, strings, kind),
        )
        val wear = Clothing.advise(Clothing.parts(forecast, date), wearShift, fromHour = if (kind == SummaryKind.Morning) now.hour else 0, wetNow = wetNow)
            ?.let { WearText.sentence(it, strings, degrees) }
        val difference = change?.takeIf { abs(it) >= 1 }?.let {
            when {
                kind == SummaryKind.Morning && it < 0 -> strings.get(R.string.yesterday_colder, -it) + "."
                kind == SummaryKind.Morning -> strings.get(R.string.yesterday_warmer, it) + "."
                it < 0 -> strings.get(R.string.summary_today_colder, -it)
                else -> strings.get(R.string.summary_today_warmer, it)
            }
        }
        val body = listOfNotNull(difference.takeIf { kind == SummaryKind.Evening }?.let { "$it." }, wear).joinToString(" ")
            .ifEmpty { strings.get(R.string.summary_no_advice) }
        // Gün boyu süren olayda saat aralığı başlıkla aynı şeyi söyler; yazılmaz.
        fun window(name: String, from: LocalDateTime, until: LocalDateTime) =
            strings.get(R.string.summary_window, capital(name, strings), clock(from), clock(until))
                .takeIf { whenRes(from, until) != R.string.summary_when_all_day }
        val window = when (event) {
            is SummaryEvent.Thunder -> window(strings.get(R.string.summary_kind_thunder), event.from, event.until)
            is SummaryEvent.Precipitation -> window(strings.get(kindName(event.kind)), event.from, event.until)
            else -> null
        }
        val sun = if (kind == SummaryKind.Evening && day.sunrise != null && day.sunset != null) {
            strings.get(R.string.widget_sunrise_at, clock(day.sunrise)) + " · " + strings.get(R.string.widget_sunset_at, clock(day.sunset))
        } else {
            null
        }
        return SummaryText(
            date = date,
            title = range,
            body = body,
            event = event,
            warning = warning,
            slices = slices(forecast, date),
            lines = listOfNotNull(window, wear, difference?.takeIf { kind == SummaryKind.Morning }, sun),
            dayCode = day.weatherCode,
        )
    }

    /** Günün en önemli olayı. */
    fun event(hours: List<HourlyForecast>, warning: WeatherAlert?, change: Int?, dayCode: Int): SummaryEvent {
        if (warning != null) return SummaryEvent.Warning(warning)
        val thunder = hours.filter { it.isThunderstorm }
        if (thunder.isNotEmpty()) {
            return SummaryEvent.Thunder(thunder.first().time, thunder.last().time.plusHours(1), hours.maxOf { it.precipitationProbability })
        }
        val wet = hours.filter { it.showsPrecipitation || it.precipitationProbability >= RAIN_CHANCE }
        if (wet.isNotEmpty()) {
            val type = when {
                wet.any { it.condition == WeatherCondition.Snow } -> SummaryEvent.Precipitation.Kind.Snow
                wet.any { it.weatherCode in 80..82 } -> SummaryEvent.Precipitation.Kind.Showers
                else -> SummaryEvent.Precipitation.Kind.Rain
            }
            return SummaryEvent.Precipitation(type, wet.first().time, wet.last().time.plusHours(1), wet.maxOf { it.precipitationProbability })
        }
        hours.mapNotNull { it.windGust }.maxOrNull()?.takeIf { it >= STRONG_GUST }?.let { return SummaryEvent.Wind(it) }
        change?.takeIf { abs(it) >= BIG_CHANGE }?.let { return SummaryEvent.Change(it) }
        hours.mapNotNull { it.uvIndex }.maxOrNull()?.takeIf { it >= HIGH_UV }?.let { return SummaryEvent.Uv(it) }
        if (hours.any { it.condition == WeatherCondition.Fog && it.time.hour < 11 }) return SummaryEvent.Fog
        return SummaryEvent.Sky(dayCode)
    }

    /** Başlığın olay kısmı: "öğleden sonra sağanak", "kuvvetli rüzgâr", "dünden 6° serin", "açık". */
    fun phrase(event: SummaryEvent, strings: Strings, kind: SummaryKind): String = when (event) {
        is SummaryEvent.Warning -> event.alert.events.firstOrNull()?.let { strings.get(R.string.summary_event_warning_of, strings.get(eventName(it)).lowercase(strings.locale)) }
            ?: strings.get(R.string.summary_event_warning)
        is SummaryEvent.Thunder -> strings.get(R.string.summary_event, strings.get(whenRes(event.from, event.until)), strings.get(R.string.summary_kind_thunder))
        is SummaryEvent.Precipitation -> strings.get(R.string.summary_event, strings.get(whenRes(event.from, event.until)), strings.get(kindName(event.kind)))
        is SummaryEvent.Wind -> strings.get(R.string.summary_event_wind)
        is SummaryEvent.Change -> strings.get(
            when {
                kind == SummaryKind.Morning && event.degrees < 0 -> R.string.summary_event_cooler_yesterday
                kind == SummaryKind.Morning -> R.string.summary_event_warmer_yesterday
                event.degrees < 0 -> R.string.summary_event_cooler_today
                else -> R.string.summary_event_warmer_today
            },
            abs(event.degrees),
        )
        is SummaryEvent.Uv -> strings.get(R.string.summary_event_uv)
        SummaryEvent.Fog -> strings.get(R.string.summary_event_fog)
        is SummaryEvent.Sky -> strings.get(skyName(event.weatherCode)).lowercase(strings.locale)
    }

    /** Olayın günün hangi saatlerinde olduğu: başlangıca göre; on saatten uzunsa "gün boyu". */
    fun whenRes(from: LocalDateTime, until: LocalDateTime): Int = when {
        java.time.Duration.between(from, until).toHours() >= 10 -> R.string.summary_when_all_day
        from.hour < 6 -> R.string.summary_when_night
        from.hour < 11 -> R.string.summary_when_morning
        from.hour < 14 -> R.string.summary_when_noon
        from.hour < 18 -> R.string.summary_when_afternoon
        from.hour < 22 -> R.string.summary_when_evening
        else -> R.string.summary_when_night
    }

    /** Sabah, öğle ve akşam dilimleri. */
    fun slices(forecast: Forecast, date: LocalDate): List<SummarySlice> = DayPart.entries.mapNotNull { part ->
        val hours = forecast.hourly.filter { it.time.toLocalDate() == date && it.time.hour >= part.from && it.time.hour < part.until }
        if (hours.isEmpty()) return@mapNotNull null
        val shown = hours.firstOrNull { it.isThunderstorm }
            ?: hours.filter { it.showsPrecipitation }.maxByOrNull { it.precipitation ?: 0.0 }
            ?: hours[hours.size / 2]
        SummarySlice(
            part = part,
            weatherCode = shown.weatherCode,
            isDay = shown.isDay,
            temperature = hours.map { it.temperature }.average(),
            chance = hours.maxOf { it.precipitationProbability }.takeIf { it >= SHOWN_CHANCE },
        )
    }

    private fun kindName(kind: SummaryEvent.Precipitation.Kind): Int = when (kind) {
        SummaryEvent.Precipitation.Kind.Rain -> R.string.summary_kind_rain
        SummaryEvent.Precipitation.Kind.Showers -> R.string.summary_kind_showers
        SummaryEvent.Precipitation.Kind.Snow -> R.string.summary_kind_snow
    }

    private fun capital(text: String, strings: Strings) = text.replaceFirstChar { it.titlecase(strings.locale) }

    private fun skyName(code: Int): Int = when (WeatherCondition.fromCode(code)) {
        WeatherCondition.Clear -> R.string.weather_clear
        WeatherCondition.PartlyCloudy -> R.string.weather_partly_cloudy
        WeatherCondition.Fog -> R.string.weather_fog
        else -> R.string.weather_overcast
    }

    private fun eventName(event: AlertEvent): Int = when (event) {
        AlertEvent.Wind -> R.string.alert_event_wind
        AlertEvent.Rain -> R.string.alert_event_rain
        AlertEvent.Thunderstorm -> R.string.alert_event_thunderstorm
        AlertEvent.Snow -> R.string.alert_event_snow
        AlertEvent.Ice -> R.string.alert_event_ice
        AlertEvent.Fog -> R.string.alert_event_fog
        AlertEvent.Heat -> R.string.alert_event_heat
        AlertEvent.Cold -> R.string.alert_event_cold
        AlertEvent.Flood -> R.string.alert_event_flood
        AlertEvent.Avalanche -> R.string.alert_event_avalanche
        AlertEvent.Dust -> R.string.alert_event_dust
        AlertEvent.Fire -> R.string.alert_event_fire
        AlertEvent.Coastal -> R.string.alert_event_coastal
        AlertEvent.Other -> R.string.alert_event_other
    }
}
