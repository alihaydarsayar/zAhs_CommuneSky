package com.alihaydarsayar.communesky.widget

import android.content.Context
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.HomeAwaySettings
import com.alihaydarsayar.communesky.model.ObservationUse
import com.alihaydarsayar.communesky.model.PrecipitationNowcast
import com.alihaydarsayar.communesky.model.RainStart
import com.alihaydarsayar.communesky.model.RainTiming
import com.alihaydarsayar.communesky.model.TemperatureUnit
import com.alihaydarsayar.communesky.model.TurkishSuffix
import com.alihaydarsayar.communesky.model.WeatherCondition
import com.alihaydarsayar.communesky.model.WeatherSnapshot
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Widget'lardaki cümleler. Hepsi string kaynaklarından, İngilizce ve Türkçe. */
object WidgetPhrases {

    fun nowcast(snapshot: WeatherSnapshot, now: LocalDateTime = snapshot.forecast.localNow()): PrecipitationNowcast =
        PrecipitationNowcast.of(snapshot.forecast, now, observed = snapshot.observation is ObservationUse.Override)

    /** Yağış widget'ının cümlesi: "Yağmur 20:00'de başlıyor", "Önümüzdeki 2 saat yağış yok". */
    fun nowcastSentence(context: Context, nowcast: PrecipitationNowcast, now: LocalDateTime): String {
        val snow = nowcast.isSnow
        return when (nowcast.kind) {
            PrecipitationNowcast.Kind.Unknown -> context.getString(R.string.widget_precip_unknown)
            PrecipitationNowcast.Kind.Dry -> context.getString(R.string.widget_precip_dry)
            PrecipitationNowcast.Kind.Continuing ->
                context.getString(if (snow) R.string.widget_snow_continues else R.string.widget_rain_continues)
            PrecipitationNowcast.Kind.Starting -> if (nowcast.isImminent(now)) {
                context.getString(if (snow) R.string.widget_snow_starts_soon else R.string.widget_rain_starts_soon)
            } else {
                context.getString(if (snow) R.string.widget_snow_starts_at else R.string.widget_rain_starts_at, atTime(context, nowcast.at!!))
            }
            PrecipitationNowcast.Kind.Stopping -> if (nowcast.isImminent(now)) {
                context.getString(if (snow) R.string.widget_snow_stops_soon else R.string.widget_rain_stops_soon)
            } else {
                context.getString(if (snow) R.string.widget_snow_stops_at else R.string.widget_rain_stops_at, atTime(context, nowcast.at!!))
            }
        }
    }

    fun nextRain(snapshot: WeatherSnapshot): RainStart? =
        RainTiming.next(snapshot.forecast, snapshot.forecast.localNow())

    /** Uzun uyarı: "Yağmur 21:00 civarı başlıyor" / "Şu an yağmur yağıyor". Yağış yoksa null. */
    fun rainAlert(context: Context, snapshot: WeatherSnapshot): String? {
        val start = nextRain(snapshot) ?: return null
        return when {
            start.isNow -> context.getString(if (start.isSnow) R.string.widget_snowing_now else R.string.widget_raining_now)
            else -> context.getString(
                if (start.isSnow) R.string.widget_snow_starts_around else R.string.widget_rain_starts_around,
                start.time.format(timeFormatter(context)),
            )
        }
    }

    /** Kısa uyarı: "21:00'de yağmur" / "Yağmur yağıyor". */
    fun rainShort(context: Context, snapshot: WeatherSnapshot): String? {
        val start = nextRain(snapshot) ?: return null
        return when {
            start.isNow -> context.getString(if (start.isSnow) R.string.widget_snowing_short else R.string.widget_raining_short)
            else -> context.getString(
                if (start.isSnow) R.string.widget_snow_at else R.string.widget_rain_at,
                atTime(context, start.time),
            )
        }
    }

    /** Sadece yağışın saati ("21:00"); şu an yağıyorsa "Şimdi". */
    fun rainTime(context: Context, snapshot: WeatherSnapshot): String? {
        val start = nextRain(snapshot) ?: return null
        return if (start.isNow) context.getString(R.string.now) else start.time.format(timeFormatter(context))
    }

    /** Uzaktayken alt şeritteki öğeler: "Evden 9 km", "Evde 2° serin", "Evde yağmur 21:00". */
    fun awayItems(
        context: Context,
        here: WidgetPlaceData,
        home: WidgetPlaceData,
        distanceKm: Double,
        settings: HomeAwaySettings,
        unit: TemperatureUnit,
    ): List<AwayItem> = buildList {
        if (settings.showDistance) {
            add(AwayItem(context.getString(R.string.widget_away_distance, distanceText(context, distanceKm)), rain = false))
        }
        if (settings.showTemperatureDifference) {
            val diff = (unit.fromCelsius(home.snapshot.forecast.current.temperature) -
                unit.fromCelsius(here.snapshot.forecast.current.temperature)).roundToInt()
            val text = when {
                diff <= -1 -> context.getString(R.string.widget_home_cooler, -diff)
                diff >= 1 -> context.getString(R.string.widget_home_warmer, diff)
                else -> context.getString(R.string.widget_home_same)
            }
            add(AwayItem(text, rain = false))
        }
        if (settings.showHomeRain) {
            nextRain(home.snapshot)?.let { start ->
                val text = when {
                    start.isNow -> context.getString(if (start.isSnow) R.string.widget_home_snowing else R.string.widget_home_raining)
                    else -> context.getString(
                        if (start.isSnow) R.string.widget_home_snow_at else R.string.widget_home_rain_at,
                        start.time.format(timeFormatter(context)),
                    )
                }
                add(AwayItem(text, rain = true))
            }
        }
    }

    data class AwayItem(val text: String, val rain: Boolean)

    /** Yakındayken hap: "Tuzla Merkez'desin · 3 km · hava aynı". */
    fun nearbyPill(context: Context, here: WidgetPlaceData, home: WidgetPlaceData, distanceKm: Double, unit: TemperatureUnit): String {
        val name = here.snapshot.city.name
        val inPlace = when {
            // Konumun adı bulunamadıysa "Konumum'dasın" demeyelim.
            name == null -> context.getString(R.string.widget_nearby_unknown)
            locale(context).language == "tr" -> context.getString(R.string.widget_nearby_in, TurkishSuffix.locative(name))
            else -> context.getString(R.string.widget_nearby_in, name)
        }
        return listOf(inPlace, distanceText(context, distanceKm), comparison(context, here.snapshot, home.snapshot, unit))
            .joinToString(" · ")
    }

    /** Bulunduğun yerin havası evle karşılaştırıldığında: "hava aynı", "2° daha serin", "burada yağmur". */
    fun comparison(context: Context, here: WeatherSnapshot, home: WeatherSnapshot, unit: TemperatureUnit): String {
        val hereWet = here.forecast.current.condition.let { it.isWet || it == WeatherCondition.Snow }
        val homeWet = home.forecast.current.condition.let { it.isWet || it == WeatherCondition.Snow }
        if (hereWet != homeWet) {
            return context.getString(if (hereWet) R.string.widget_rain_here else R.string.widget_rain_at_home_short)
        }
        val diff = (unit.fromCelsius(here.forecast.current.temperature) - unit.fromCelsius(home.forecast.current.temperature)).roundToInt()
        return when {
            abs(diff) < 1 -> context.getString(R.string.widget_same_weather)
            diff > 0 -> context.getString(R.string.widget_here_warmer, diff)
            else -> context.getString(R.string.widget_here_cooler, -diff)
        }
    }
}

/** "Yağmur 21:00'de başlıyor" / "Şu an yağmur yağıyor" (önümüzdeki 12 saat); yağış yoksa null. */
fun WidgetPhrases.rainStartSentence(context: android.content.Context, snapshot: WeatherSnapshot): String? {
    val start = nextRain(snapshot) ?: return null
    return when {
        start.isNow -> context.getString(if (start.isSnow) R.string.widget_snowing_now else R.string.widget_raining_now)
        else -> context.getString(
            if (start.isSnow) R.string.widget_snow_starts_at else R.string.widget_rain_starts_at,
            atTime(context, start.time),
        )
    }
}
