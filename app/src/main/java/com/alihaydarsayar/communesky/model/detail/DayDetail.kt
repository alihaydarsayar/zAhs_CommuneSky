package com.alihaydarsayar.communesky.model.detail

import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.ForecastMerge
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.PlaceExtras
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.max

/**
 * Gün sayfasının verisi: 7 günlük karttaki bir günün ayrıntısı. Günün en yüksek ve en düşük sıcaklığı,
 * simgesi ve yağış ihtimali 7 günlük kartın satırındaki değerlerin ta kendisidir ([day]); saat saat grafikler
 * o günün saatlerinden ([hours]) hazırlanır. Saatler yoksa (henüz gelmedi ya da internet yok) grafikler null'dır
 * ve sayfa yalnız günlük bilgileri gösterir. Saf hesap; test edilir.
 */
data class DayDetail(
    val day: DailyForecast,
    val isToday: Boolean,
    val hours: List<HourlyForecast>,
    /** Sıcaklık (çizgi) ve hissedilen (kesikli). */
    val temperature: Chart?,
    /** Yağış miktarı (çubuk) ve ihtimali (yüzde). */
    val precipitation: Chart?,
    /** Rüzgâr (çizgi), hamle (kesikli) ve yön. */
    val wind: Chart?,
    val feelsHigh: Double?,
    val feelsLow: Double?,
    /** Günün toplam yağışı (mm). */
    val precipitationTotal: Double?,
    val uvMax: Double?,
    /** Gün uzunluğu (dakika). */
    val dayLengthMinutes: Double?,
    /** Günün nem aralığı (%); saatlik veri yoksa ikisi de günlük ortalamadır. */
    val humidityLow: Int?,
    val humidityHigh: Int?,
    val windMax: Double?,
    val gustMax: Double?,
)

object DayData {
    /** Bir gün, en az bu kadar saati eldeyse ek istek atılmadan gösterilir. */
    const val MIN_HOURS = 20
    const val DAYS = 7

    /** Sayfaların günleri: bugünden başlayarak 7 günlük karttaki günler. */
    fun days(forecast: Forecast, now: LocalDateTime): List<DailyForecast> = forecast.upcomingDays(now).take(DAYS)

    /**
     * O günün telefonda hazır duran saatleri: bugün ve yarın eldeki 48 saatten gelir. Sonraki günlerin saatlik
     * verisi telefonda yoktur (null); sayfa açılınca o gün için ayrıca istenir.
     */
    fun localHours(forecast: Forecast, date: LocalDate): List<HourlyForecast>? =
        forecast.hourly.filter { it.time.toLocalDate() == date }.sortedBy { it.time }.takeIf { it.size >= MIN_HOURS }

    /**
     * Ayrıca istenen bir günün saatlerini (Open-Meteo'nun ana tahmini) yerin ek verileriyle birleştirir: ana
     * ekrandaki saatlerle aynı kod ([ForecastMerge]). O günü kapsayan ilk kaynak kullanılır; Türkiye'de MGM'nin
     * saatlik tahmini iki gün sonrasına uzanmadığı için sıradaki ECMWF, o da yoksa Open-Meteo'nun kendi seçimi.
     */
    fun mergeFetched(fetched: List<HourlyForecast>, forecast: Forecast, extras: PlaceExtras?, date: LocalDate): Pair<List<HourlyForecast>, ForecastSource> {
        // Günlük değerler zaten birleştirilmiş hâlde ekranda; burada yalnız saatler birleştirilir.
        val base = forecast.copy(hourly = fetched.sortedBy { it.time }, daily = emptyList(), minutely = emptyList())
        val merged = ForecastMerge.merge(base, extras, now = date.atStartOfDay())
        return merged.forecast.hourly.filter { it.time.toLocalDate() == date } to merged.sources.forecast
    }

    /**
     * Saatlik eğriyi günün en yüksek ve en düşüğüne (7 günlük karttaki değerler) oturtur: 3.–7. günlerde saatler
     * çoğu zaman günlük değerlerden başka bir kaynaktan gelir ve eğrinin tepesi sayfadaki en yüksekle ayrışır.
     * Her saat, eğrideki yerine göre kaydırılır: en sıcak saat en yüksek farkı, en soğuk saat en düşük farkı
     * kadar, aradakiler sıcaklıklarına göre ikisinin arasında. Böylece eğrinin biçimi korunur, tepesi en yükseğe,
     * dibi en düşüğe denk gelir. Hissedilen aynı miktarda kayar.
     */
    fun anchor(hours: List<HourlyForecast>, day: DailyForecast): List<HourlyForecast> {
        if (hours.isEmpty()) return hours
        val top = hours.maxOf { it.temperature }
        val bottom = hours.minOf { it.temperature }
        val high = day.maxTemperature - top
        val low = day.minTemperature - bottom
        return hours.map { hour ->
            val share = if (top > bottom) (hour.temperature - bottom) / (top - bottom) else 0.5
            val shift = low + (high - low) * share
            hour.copy(temperature = hour.temperature + shift, apparentTemperature = hour.apparentTemperature?.plus(shift))
        }
    }

    fun of(day: DailyForecast, hours: List<HourlyForecast>, now: LocalDateTime): DayDetail {
        val isToday = day.date == now.toLocalDate()
        val position = (now.hour + now.minute / 60f).takeIf { isToday && hours.isNotEmpty() }
        fun chart(kind: ChartKind, min: Double, max: Double, secondaryPercent: Boolean = false, point: (HourlyForecast) -> ChartPoint) =
            Chart(hours.map(point), kind, min, max, position, secondaryPercent).takeIf { it.hasData }

        val temperatures = hours.flatMap { listOfNotNull(it.temperature, it.apparentTemperature) }
        val (low, high) = MetricData.range(temperatures, pad = 2.0, step = 5.0)
        val windTop = hours.mapNotNull { it.windGust ?: it.windSpeed }.maxOrNull() ?: 0.0
        val rainTop = hours.mapNotNull { it.precipitation }.maxOrNull() ?: 0.0
        val humidity = hours.mapNotNull { it.humidity }
        val mean = day.humidityMean?.let { Math.round(it).toInt() }
        return DayDetail(
            day = day,
            isToday = isToday,
            hours = hours,
            temperature = chart(ChartKind.Line, low, high) { ChartPoint(it.time, it.temperature, it.apparentTemperature) },
            precipitation = chart(ChartKind.Bars, 0.0, max(2.0, ceil(rainTop * 1.2)), secondaryPercent = true) {
                ChartPoint(it.time, it.precipitation, it.precipitationProbability.toDouble())
            },
            wind = chart(ChartKind.Line, 0.0, ceil(max(20.0, windTop * 1.1) / 10) * 10) { ChartPoint(it.time, it.windSpeed, it.windGust, it.windDirection) },
            feelsHigh = day.apparentMax ?: hours.mapNotNull { it.apparentTemperature }.maxOrNull(),
            feelsLow = day.apparentMin ?: hours.mapNotNull { it.apparentTemperature }.minOrNull(),
            precipitationTotal = day.precipitationSum ?: hours.mapNotNull { it.precipitation }.takeIf { it.isNotEmpty() }?.sum(),
            uvMax = day.uvIndexMax ?: hours.mapNotNull { it.uvIndex }.maxOrNull(),
            dayLengthMinutes = MetricData.dayLength(day),
            humidityLow = humidity.minOrNull() ?: mean,
            humidityHigh = humidity.maxOrNull() ?: mean,
            windMax = day.windSpeedMax ?: hours.mapNotNull { it.windSpeed }.maxOrNull(),
            gustMax = day.windGustMax ?: hours.mapNotNull { it.windGust }.maxOrNull(),
        )
    }

    /** Grafikte o saatin sırası; saat o günde yoksa null. */
    fun indexOfHour(hours: List<HourlyForecast>, hour: Int?): Int? =
        hour?.let { wanted -> hours.indexOfFirst { it.time.hour == wanted }.takeIf { it >= 0 } }
}
