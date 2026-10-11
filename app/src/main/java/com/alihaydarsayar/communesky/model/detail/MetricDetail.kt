package com.alihaydarsayar.communesky.model.detail

import com.alihaydarsayar.communesky.model.DailyForecast
import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import com.alihaydarsayar.communesky.model.today.Compared
import com.alihaydarsayar.communesky.model.today.YesterdayComparison
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Ayrıntı sayfalarının verisi. Her bilgi (hissedilen, rüzgâr, nem…) aynı kalıpla hazırlanır: şimdiki değer,
 * bugünün 24 saatlik grafiği, dünle karşılaştırma ve 7 günlük özet. Hepsi saf hesaptır; test edilir. Değerler
 * hava verisinin kendi birimlerindedir (°C, km/sa, hPa, mm; görüş km); ekran kullanıcının birimine çevirir.
 */
enum class Metric { FeelsLike, Wind, Humidity, Uv, Visibility, Pressure, Cloud, Precipitation, Sun }

/** Grafikteki bir saat. [secondary]: ikinci değer (gerçek sıcaklık, hamle, çiy noktası, yağış ihtimali). */
data class ChartPoint(val time: LocalDateTime, val value: Double?, val secondary: Double? = null, val direction: Int? = null)

enum class ChartKind { Line, Bars }

/**
 * Bugünün 24 saatlik grafiği. [min]–[max]: dikey eksenin aralığı. [nowPosition]: şimdinin yatay yeri (saat
 * cinsinden, 0–23, kesirli); bugün değilse null. [secondaryPercent]: ikinci değer yüzde (0–100) ve kendi ölçeğinde.
 */
data class Chart(
    val points: List<ChartPoint>,
    val kind: ChartKind,
    val min: Double,
    val max: Double,
    val nowPosition: Float?,
    val secondaryPercent: Boolean = false,
) {
    val hasData: Boolean get() = points.count { it.value != null } >= 2
}

/** 7 günlük özetteki bir gün. [low]: aralık gösterilen bilgilerde (hissedilen) günün en düşüğü. */
data class DayValue(val date: LocalDate, val value: Double?, val low: Double? = null, val secondary: Double? = null)

enum class Trend { Rising, Falling, Steady }

/** Dünle karşılaştırmanın sonucu: "biraz düşük", "aynı", "yüksek"… */
enum class ComparisonLevel { MuchLower, Lower, Same, Higher, MuchHigher }

data class MetricDetail(
    val metric: Metric,
    /** Şimdiki değer. */
    val current: Double?,
    /** Şimdiki ikinci değer: gerçek sıcaklık, hamle, çiy noktası, bugünün toplam yağışı (mm). */
    val currentSecondary: Double?,
    val chart: Chart?,
    val comparison: Compared?,
    val week: List<DayValue>,
    /** Basınçta son üç saatin eğilimi. */
    val trend: Trend? = null,
    /** Rüzgârın şu anki yönü (derece). */
    val direction: Int? = null,
) {
    val comparisonLevel: ComparisonLevel? get() = comparison?.let { MetricData.level(metric, it) }
}

object MetricData {
    /** Basınç üç saatte bundan fazla değiştiyse yükseliyor ya da düşüyor denir (hPa). */
    const val PRESSURE_TREND = 1.5

    private const val WEEK_DAYS = 7

    fun of(metric: Metric, forecast: Forecast, now: LocalDateTime): MetricDetail {
        val today = now.toLocalDate()
        val hours = forecast.hourly.filter { it.time.toLocalDate() == today }.sortedBy { it.time }
        val days = forecast.daily.filter { !it.date.isBefore(today) }.sortedBy { it.date }.take(WEEK_DAYS)
        val day = forecast.today(now)
        val current = forecast.current
        val yesterday = YesterdayComparison.of(forecast, now)
        val position = (now.hour + now.minute / 60f).takeIf { hours.isNotEmpty() }

        fun chart(kind: ChartKind, min: Double, max: Double, secondaryPercent: Boolean = false, point: (HourlyForecast) -> ChartPoint) =
            Chart(hours.map(point), kind, min, max, position, secondaryPercent).takeIf { it.hasData }

        fun week(value: (DailyForecast) -> DayValue) = days.map(value)

        return when (metric) {
            Metric.FeelsLike -> {
                val all = hours.flatMap { listOfNotNull(it.apparentTemperature, it.temperature) }
                val (low, high) = range(all, pad = 2.0, step = 5.0)
                MetricDetail(
                    metric, current.apparentTemperature, current.temperature,
                    chart(ChartKind.Line, low, high) { ChartPoint(it.time, it.apparentTemperature, it.temperature) },
                    yesterday?.feelsLike,
                    week { DayValue(it.date, it.apparentMax ?: it.maxTemperature, it.apparentMin ?: it.minTemperature) },
                )
            }
            Metric.Wind -> {
                val top = hours.mapNotNull { it.windGust ?: it.windSpeed }.maxOrNull() ?: 0.0
                MetricDetail(
                    metric, current.windSpeed, hours.firstOrNull { it.time.hour == now.hour }?.windGust,
                    chart(ChartKind.Line, 0.0, ceilTo(max(20.0, top * 1.1), 10.0)) { ChartPoint(it.time, it.windSpeed, it.windGust, it.windDirection) },
                    yesterday?.wind,
                    week { DayValue(it.date, it.windSpeedMax, secondary = it.windGustMax) },
                    direction = current.windDirection,
                )
            }
            Metric.Humidity -> MetricDetail(
                metric, current.humidity.toDouble(), current.dewPoint,
                chart(ChartKind.Line, 0.0, 100.0) { ChartPoint(it.time, it.humidity?.toDouble(), it.dewPoint) },
                yesterday?.humidity,
                week { DayValue(it.date, it.humidityMean) },
            )
            Metric.Uv -> {
                val top = hours.mapNotNull { it.uvIndex }.maxOrNull() ?: 0.0
                MetricDetail(
                    metric, current.uvIndex, day?.uvIndexMax,
                    chart(ChartKind.Bars, 0.0, ceilTo(max(8.0, top + 1), 1.0)) { ChartPoint(it.time, it.uvIndex) },
                    yesterday?.uv,
                    week { DayValue(it.date, it.uvIndexMax) },
                )
            }
            Metric.Visibility -> {
                val top = hours.mapNotNull { it.visibility }.maxOrNull()?.div(1000) ?: 0.0
                MetricDetail(
                    metric, current.visibility?.div(1000), null,
                    chart(ChartKind.Line, 0.0, ceilTo(max(10.0, top), 10.0)) { ChartPoint(it.time, it.visibility?.div(1000)) },
                    yesterday?.visibility?.let { Compared(it.today / 1000, it.yesterday / 1000) },
                    week { DayValue(it.date, it.visibilityMean?.div(1000)) },
                )
            }
            Metric.Pressure -> {
                val (low, high) = range(hours.mapNotNull { it.pressure }, pad = 2.0, step = 5.0)
                MetricDetail(
                    metric, current.pressure, null,
                    chart(ChartKind.Line, low, high) { ChartPoint(it.time, it.pressure) },
                    yesterday?.pressure,
                    week { DayValue(it.date, it.pressureMean) },
                    trend = pressureTrend(forecast, now),
                )
            }
            Metric.Cloud -> MetricDetail(
                metric, current.cloudCover?.toDouble(), null,
                chart(ChartKind.Line, 0.0, 100.0) { ChartPoint(it.time, it.cloudCover?.toDouble()) },
                yesterday?.cloud,
                week { DayValue(it.date, it.cloudMean) },
            )
            Metric.Precipitation -> {
                val top = hours.mapNotNull { it.precipitation }.maxOrNull() ?: 0.0
                MetricDetail(
                    metric, day?.precipitationProbability?.toDouble(), day?.precipitationSum ?: hours.sumOf { it.precipitation ?: 0.0 },
                    chart(ChartKind.Bars, 0.0, max(2.0, ceil(top * 1.2)), secondaryPercent = true) {
                        ChartPoint(it.time, it.precipitation, it.precipitationProbability.toDouble())
                    },
                    yesterday?.precipitation,
                    week { DayValue(it.date, it.precipitationSum, secondary = it.precipitationProbability.toDouble()) },
                )
            }
            Metric.Sun -> MetricDetail(
                metric, day?.let { dayLength(it) }, null, null, yesterday?.dayLength,
                week { DayValue(it.date, dayLength(it)) },
            )
        }
    }

    /** Gün uzunluğu (dakika). */
    fun dayLength(day: DailyForecast): Double? {
        val rise = day.sunrise ?: return null
        val set = day.sunset ?: return null
        return Duration.between(rise, set).toMinutes().toDouble()
    }

    /** Şimdiki basınç ile üç saat öncesi arasındaki fark. */
    fun pressureTrend(forecast: Forecast, now: LocalDateTime): Trend? {
        val hour = now.withMinute(0).withSecond(0).withNano(0)
        val current = forecast.hourly.firstOrNull { it.time == hour }?.pressure ?: forecast.current.pressure ?: return null
        val before = forecast.hourly.firstOrNull { it.time == hour.minusHours(3) }?.pressure ?: return null
        val change = current - before
        return when {
            change >= PRESSURE_TREND -> Trend.Rising
            change <= -PRESSURE_TREND -> Trend.Falling
            else -> Trend.Steady
        }
    }

    /** Fark bu kadardan küçükse "aynı"; ikinci değerden büyükse "biraz" denmez. */
    fun thresholds(metric: Metric): Pair<Double, Double> = when (metric) {
        Metric.FeelsLike -> 1.0 to 4.0
        Metric.Wind -> 5.0 to 15.0
        Metric.Humidity -> 5.0 to 15.0
        Metric.Uv -> 1.0 to 3.0
        Metric.Visibility -> 2.0 to 8.0
        Metric.Pressure -> 2.0 to 6.0
        Metric.Cloud -> 10.0 to 30.0
        Metric.Precipitation -> 1.0 to 5.0
        Metric.Sun -> 1.0 to 10.0
    }

    fun level(metric: Metric, compared: Compared): ComparisonLevel {
        val (same, much) = thresholds(metric)
        val difference = compared.difference
        return when {
            abs(difference) < same -> ComparisonLevel.Same
            difference >= much -> ComparisonLevel.MuchHigher
            difference > 0 -> ComparisonLevel.Higher
            difference <= -much -> ComparisonLevel.MuchLower
            else -> ComparisonLevel.Lower
        }
    }

    /** Değerleri saran, [step]'in katlarına yuvarlanmış eksen aralığı; veri yoksa 0–[step]. */
    fun range(values: List<Double>, pad: Double, step: Double): Pair<Double, Double> {
        if (values.isEmpty()) return 0.0 to step
        val low = floor((values.min() - pad) / step) * step
        val high = ceil((values.max() + pad) / step) * step
        return low to if (high > low) high else low + step
    }

    private fun ceilTo(value: Double, step: Double): Double = ceil(value / step) * step
}
