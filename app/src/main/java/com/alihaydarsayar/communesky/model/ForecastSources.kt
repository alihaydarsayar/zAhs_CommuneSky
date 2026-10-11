package com.alihaydarsayar.communesky.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDateTime

/** Bir tahminin geldiği yer: ulusal servis ya da Open-Meteo üzerinden alınan bir model. */
@Serializable
enum class ForecastSource {
    /** Meteoroloji Genel Müdürlüğü (Türkiye). */
    Mgm,

    /** ECMWF IFS, 9 km (Open-Meteo üzerinden). */
    EcmwfIfs,

    /** ECMWF'nin yapay zekâ modeli AIFS (Open-Meteo üzerinden). */
    EcmwfAifs,

    /** Open-Meteo'nun o nokta için kendi seçtiği model karışımı ("best_match"). */
    OpenMeteo,

    /** Météo-France AROME/ARPEGE (Open-Meteo üzerinden). */
    MeteoFrance,

    /** Met Office UKV 2 km + küresel (Open-Meteo üzerinden). */
    UkMetOffice,
}

/** Bir kaynağın tahmini: saatlik ve günlük değerler. Kaynak bir alanı vermediyse o alan null'dır. */
@Serializable
data class CandidateSeries(
    val source: ForecastSource,
    val hours: List<NationalHour> = emptyList(),
    val days: List<NationalDay> = emptyList(),
)

/**
 * Bir yer için ana tahmine (Open-Meteo) eklenen her şey: ulusal servisin tahmini, öteki modellerin
 * tahminleri, uyarılar, mevsim normali ve telefonun kendi verdiği kararlar (hakem, düzeltme).
 * Önbellekte JSON olarak durur; alanların hepsi isteğe bağlıdır.
 */
@Serializable
data class PlaceExtras(
    /** Ulusal servisin merkezi bulunurken kullanılan koordinat; yer değişince yeniden bulunur. */
    val queryLatitude: Double = 0.0,
    val queryLongitude: Double = 0.0,
    /** MGM ilçe kodu; null ise yer Türkiye'de değil ya da merkez henüz bulunamadı. */
    val centerId: Int? = null,
    /** Mevsim normali yalnızca il merkezi için verilir. */
    val provinceCenterId: Int? = null,
    val hourlyStationId: Int? = null,
    val dailyStationId: Int? = null,
    val national: CandidateSeries? = null,
    val nationalFetchedAt: Long = 0,
    /** Son istekte ulusal servis cevap vermedi (hata, zaman aşımı ya da boş cevap). */
    val nationalFailed: Boolean = false,
    val models: List<CandidateSeries> = emptyList(),
    val modelsFetchedAt: Long = 0,
    val alerts: List<WeatherAlert> = emptyList(),
    val alertsFetchedAt: Long = 0,
    val normal: SeasonalNormal? = null,
    val normalFetchedAt: Long = 0,
    /** Hakemin seçtiği kaynak; null ise varsayılan kullanılır. */
    val chosen: ForecastSource? = null,
    /** Öğrenen düzeltme (°C): gündüz ve gece saatlerinin sıcaklığına eklenir; 0 ise düzeltme yok. */
    val dayCorrection: Double = 0.0,
    val nightCorrection: Double = 0.0,
    /** Son karnenin özeti (bilgi kutusunda gösterilir) ve karnenin çıkarıldığı gün. */
    val accuracy: List<StoredAccuracy> = emptyList(),
    val decidedDay: Long = -1,
) {
    val isNational: Boolean get() = centerId != null

    fun series(source: ForecastSource): CandidateSeries? =
        if (source == national?.source) national else models.firstOrNull { it.source == source }
}

/** Bir kaynağın son 30 günlük karnesi, saklanacak kadarıyla. */
@Serializable
data class StoredAccuracy(
    val source: ForecastSource,
    /** Karşılaştırma yapılabilen gün sayısı. */
    val days: Int = 0,
    /** 24 saat sonrası için ortalama mutlak hata (°C). */
    val dayAheadError: Double? = null,
    /** Yarının en yüksek sıcaklığındaki ortalama mutlak hata (°C). */
    val nextDayMaxError: Double? = null,
)

/** Ekranda görünen tahminin kaynakları ("Tahmin: MGM · Yağış: Open-Meteo"). */
data class ForecastSources(
    /** Saatlik ve günlük sıcaklık ile hava durumunun kaynağı. */
    val forecast: ForecastSource,
    /** Yağış ihtimali ve miktarı, UV, basınç gibi alanların kaynağı. */
    val precipitation: ForecastSource = ForecastSource.OpenMeteo,
    /** Yer ulusal servisin alanında ama servis cevap vermedi; yedek kaynak kullanılıyor. */
    val nationalUnavailable: Boolean = false,
    val rainNote: RainNote? = null,
    /** Uygulanan öğrenen düzeltme (°C); yoksa 0. */
    val dayCorrection: Double = 0.0,
    val nightCorrection: Double = 0.0,
    /** Kaynakların karnesi; henüz yeterli veri yoksa boş. */
    val accuracy: List<StoredAccuracy> = emptyList(),
)

/** İkon ile yağış ihtimali çeliştiğinde ekranda tek bir bilgi durur. */
sealed interface RainNote {
    /** Ulusal servis yağış diyor, Open-Meteo'nun ihtimali düşük: yüzde gösterilmez, "Yağmur bekleniyor (MGM)". */
    data class Expected(val by: ForecastSource) : RainNote

    /** Ulusal servis yağış demiyor, Open-Meteo'nun ihtimali yüksek: ikon ulusal servisten, yanında "Yağmur ihtimali %60". */
    data class Chance(val percent: Int) : RainNote
}

/**
 * Hissedilen sıcaklık (Steadman'ın gölgedeki "apparent temperature" formülü; Avustralya Meteoroloji Bürosu'nun
 * kullandığı biçim): sıcaklık, nem ve rüzgârdan. Saf hesap; test edilir.
 */
object ApparentTemperature {
    fun of(temperature: Double, humidityPercent: Int?, windKmh: Double?): Double? {
        if (humidityPercent == null || windKmh == null) return null
        val vapour = humidityPercent / 100.0 * 6.105 * Math.exp(17.27 * temperature / (237.7 + temperature))
        return temperature + 0.33 * vapour - 0.70 * (windKmh / 3.6) - 4.0
    }
}

/**
 * Ana tahmini (Open-Meteo) seçilen kaynağın sıcaklık ve hava durumuyla birleştirir. Saf mantık; test edilir.
 *
 * - Saatlik sıcaklık: kaynağın adımları seyrekse (MGM 3 saatte bir) aradaki saatler komşu değerlerden
 *   doğrusal hesaplanır. Hava durumu (ikon) en yakın saatten alınır; ara değerle uydurulmaz.
 * - Günlük en yüksek/en düşük ve hava durumu: kaynağın verdiği günlerde ondan; sonrası yedekten.
 * - Yağış ihtimali ve miktarı, UV, basınç, dakikalık yağış hep ana tahminden.
 * - Kaynak bir parçayı vermediyse o parça sıradaki yedekten gelir; ekran asla boş kalmaz.
 */
object ForecastMerge {

    /** İki tahmin saati arasındaki boşluk bundan uzunsa aradaki saatler hesaplanmaz. */
    private val MAX_GAP: Duration = Duration.ofHours(6)

    /** İkon bu kadar yakın bir saatten alınır. */
    private val NEAREST: Duration = Duration.ofMinutes(100)

    const val LOW_CHANCE = 30
    const val HIGH_CHANCE = 60

    data class Result(val forecast: Forecast, val sources: ForecastSources)

    /** Kaynak sırası: hakemin seçtiği (yoksa varsayılan), sonra yedekler. */
    fun order(extras: PlaceExtras): List<ForecastSource> {
        val default = if (extras.isNational) ForecastSource.Mgm else ForecastSource.OpenMeteo
        val fallbacks = if (extras.isNational) listOf(ForecastSource.Mgm, ForecastSource.EcmwfIfs, ForecastSource.OpenMeteo) else listOf(ForecastSource.OpenMeteo)
        return (listOfNotNull(extras.chosen, default) + fallbacks).distinct()
    }

    fun merge(base: Forecast, extras: PlaceExtras?, now: LocalDateTime = base.localNow()): Result {
        if (extras == null) return Result(base, ForecastSources(ForecastSource.OpenMeteo))
        val order = order(extras)
        // Kullanılacak kaynak: sıradaki, önümüzdeki saatler için sıcaklık verebilen ilk kaynak.
        val chosen = order.firstOrNull { source ->
            source == ForecastSource.OpenMeteo || extras.series(source)?.let { covers(it, base, now) } == true
        } ?: ForecastSource.OpenMeteo
        // Ana tahmin zaten Open-Meteo: o seçildiyse üstüne bir şey yazılmaz.
        val series = extras.series(chosen).takeIf { chosen != ForecastSource.OpenMeteo }
        // Günlük tahmin ayrı bir parçadır: saatliği vermeyen kaynak günlüğü verebilir (ör. saatlik istasyonu olmayan yer).
        val dailySeries = order.takeWhile { it != ForecastSource.OpenMeteo }.firstNotNullOfOrNull { source -> extras.series(source)?.takeIf { it.days.isNotEmpty() } }
        val later = extras.series(ForecastSource.EcmwfIfs)?.takeIf { extras.isNational && dailySeries?.source != ForecastSource.EcmwfIfs }

        val hourly = base.hourly.map { hour ->
            val temperature = series?.let { temperatureAt(it.hours, hour.time) } ?: return@map hour
            val code = series.hours.filter { it.weatherCode != null }
                .minByOrNull { Duration.between(it.at, hour.time).abs() }
                ?.takeIf { Duration.between(it.at, hour.time).abs() <= NEAREST }?.weatherCode
            val humidity = valueAt(series.hours, hour.time) { it.humidity?.toDouble() }?.let { Math.round(it).toInt() } ?: hour.humidity
            val wind = valueAt(series.hours, hour.time) { it.windSpeed } ?: hour.windSpeed
            val gust = valueAt(series.hours, hour.time) { it.gust } ?: hour.windGust
            val merged = hour.copy(
                temperature = temperature,
                weatherCode = code ?: hour.weatherCode,
                humidity = humidity,
                windSpeed = wind,
                windGust = gust,
                // Hissedilen, ekrandaki sıcaklık, nem ve rüzgârla yeniden hesaplanır: ikisi hep tutarlı kalır.
                apparentTemperature = ApparentTemperature.of(temperature, humidity, wind) ?: hour.apparentTemperature?.let { it + (temperature - hour.temperature) },
            )
            // Kaynak yağış diyor, ihtimal düşük: çelişen yüzde gösterilmez.
            if (code != null && merged.condition.isWet && hour.precipitationProbability < LOW_CHANCE) merged.copy(showProbability = false) else merged
        }
        val daily = base.daily.map { day ->
            val own = dailySeries?.days?.firstOrNull { it.day == day.date }
            val backup = later?.days?.firstOrNull { it.day == day.date }.takeIf { own == null }
            val from = own ?: backup ?: return@map day
            day.copy(
                maxTemperature = from.maxTemperature ?: day.maxTemperature,
                minTemperature = from.minTemperature ?: day.minTemperature,
                weatherCode = from.weatherCode ?: day.weatherCode,
            )
        }
        // Öğrenen düzeltme: gündüz ve gece saatlerine ayrı; günün en yükseği gündüz, en düşüğü gece düzeltmesini alır.
        val day = extras.dayCorrection
        val night = extras.nightCorrection
        val corrected = day != 0.0 || night != 0.0
        val merged = if (!corrected) {
            base.copy(hourly = hourly, daily = daily)
        } else {
            base.copy(
                hourly = hourly.map { hour ->
                    val shift = if (hour.isDay) day else night
                    hour.copy(temperature = hour.temperature + shift, apparentTemperature = hour.apparentTemperature?.plus(shift))
                },
                daily = daily.map { it.copy(maxTemperature = it.maxTemperature + day, minTemperature = it.minTemperature + night) },
            )
        }
        val sources = ForecastSources(
            dayCorrection = day,
            nightCorrection = night,
            accuracy = extras.accuracy,
            forecast = chosen,
            nationalUnavailable = extras.isNational && chosen != ForecastSource.Mgm && extras.chosen != chosen,
            rainNote = if (chosen == ForecastSource.OpenMeteo) null else rainNote(merged, chosen, now),
        )
        return Result(merged, sources)
    }

    /** Kaynak, önümüzdeki 12 saatin en az yarısı için sıcaklık verebiliyor mu? */
    private fun covers(series: CandidateSeries, base: Forecast, now: LocalDateTime): Boolean {
        val next = base.upcomingHours(now, count = 12)
        return next.isNotEmpty() && next.count { temperatureAt(series.hours, it.time) != null } * 2 >= next.size
    }

    /** [time] anındaki sıcaklık: tam o saatin değeri ya da iki komşu değerin doğrusal karışımı. */
    fun temperatureAt(hours: List<NationalHour>, time: LocalDateTime): Double? = valueAt(hours, time) { it.temperature }

    /** Herhangi bir sayısal alan için aynı hesap (nem, rüzgâr, hamle). */
    fun valueAt(hours: List<NationalHour>, time: LocalDateTime, value: (NationalHour) -> Double?): Double? {
        val known = hours.filter { value(it) != null }
        val before = known.lastOrNull { !it.at.isAfter(time) } ?: return null
        if (before.at == time) return value(before)
        val after = known.firstOrNull { it.at.isAfter(time) } ?: return null
        val gap = Duration.between(before.at, after.at)
        if (gap > MAX_GAP) return null
        val share = Duration.between(before.at, time).toMinutes().toDouble() / gap.toMinutes()
        return value(before)!! + (value(after)!! - value(before)!!) * share
    }

    /** Önümüzdeki 12 saat için: ikon ile yağış ihtimali çelişiyorsa ekranda duracak tek bilgi. */
    private fun rainNote(merged: Forecast, source: ForecastSource, now: LocalDateTime): RainNote? {
        val next = merged.upcomingHours(now, count = 12)
        val wet = next.filter { it.condition.isWet }
        return when {
            wet.isNotEmpty() -> if (wet.all { it.precipitationProbability < LOW_CHANCE }) RainNote.Expected(source) else null
            else -> next.maxOfOrNull { it.precipitationProbability }?.takeIf { it >= HIGH_CHANCE }?.let { RainNote.Chance(it) }
        }
    }
}
