package com.alihaydarsayar.communesky.model

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tahminlerin doğruluğu: kaydedilen tahminler, sonradan o saat için gelen gerçek ölçümle karşılaştırılır.
 * Hepsi telefonda hesaplanır; hiçbir veri dışarı gitmez. Bu dosyadaki her şey saf hesaptır; test edilir.
 */

/** Bir tahmin ile o an için ölçülen değerin eşi. Saatler yerin kendi saatiyle. */
data class ForecastPair(
    val source: ForecastSource,
    /** Tahmin kaç saat önceden yapılmıştı. */
    val leadHours: Int,
    /** Hedef saatin günü (dönem başından beri gün sayısı) ve yerel saati. */
    val day: Long,
    val localHour: Int,
    val forecast: Double,
    val observed: Double,
) {
    val error: Double get() = forecast - observed
    val isNight: Boolean get() = localHour < DAY_START || localHour >= NIGHT_START

    companion object {
        const val DAY_START = 7
        const val NIGHT_START = 19
    }
}

/** Bir kaynağın hata özeti. */
data class SourceAccuracy(
    val source: ForecastSource,
    /** 1–6 saat sonrası için ortalama mutlak hata (°C); eş yoksa null. */
    val shortTermError: Double?,
    /** 24 saat sonrası için ortalama mutlak hata. */
    val dayAheadError: Double?,
    /** Ortalama sapma, işaretli (tahmin − ölçüm): artıysa tahmin sıcak kalıyor. */
    val bias: Double?,
    val dayBias: Double?,
    val nightBias: Double?,
    /** Yarının en yüksek sıcaklığındaki ortalama mutlak hata. */
    val nextDayMaxError: Double?,
    /** Eşi olan gün sayısı. */
    val days: Int,
)

object AccuracyStats {

    /** Kısa vade: 1–6 saat. */
    val SHORT_LEADS = 1..6
    const val DAY_AHEAD = 24

    private fun List<Double>.meanOrNull(): Double? = if (isEmpty()) null else sum() / size

    /**
     * [pairs]: saatlik tahmin–ölçüm eşleri; [maxPairs]: "yarının en yükseği" tahmini ile o gün ölçülen en yüksek
     * (kaynak → (tahmin, ölçülen) listesi).
     */
    fun summarize(pairs: List<ForecastPair>, maxPairs: Map<ForecastSource, List<Pair<Double, Double>>> = emptyMap()): List<SourceAccuracy> =
        (pairs.map { it.source } + maxPairs.keys).distinct().map { source ->
            val own = pairs.filter { it.source == source }
            val short = own.filter { it.leadHours in SHORT_LEADS }
            SourceAccuracy(
                source = source,
                shortTermError = short.map { abs(it.error) }.meanOrNull(),
                dayAheadError = own.filter { it.leadHours == DAY_AHEAD }.map { abs(it.error) }.meanOrNull(),
                bias = short.map { it.error }.meanOrNull(),
                dayBias = short.filter { !it.isNight }.map { it.error }.meanOrNull(),
                nightBias = short.filter { it.isNight }.map { it.error }.meanOrNull(),
                nextDayMaxError = maxPairs[source].orEmpty().map { abs(it.first - it.second) }.meanOrNull(),
                days = own.map { it.day }.distinct().size,
            )
        }
}

/**
 * Otomatik hakem: geleceğin tahmini hangi kaynaktan alınsın? Kullanıcı ayarı yoktur. Şu anki değer hep
 * istasyonun gerçek ölçümüdür; hakem ona karışmaz.
 */
object ForecastReferee {
    /** Karar için gereken en az karşılaştırma günü. */
    const val MIN_DAYS = 10

    /** Başka bir aday varsayılandan en az bu oranda daha yakın çıkmalı. */
    const val MARGIN = 0.15

    /**
     * Varsayılan kaynak yerine kullanılacak aday; değiştirmeye gerek yoksa null. Ölçüt: 24 saat sonrası (yoksa
     * 1–6 saat) ortalama mutlak hata. Karar her gün yeniden verilir.
     */
    fun choose(default: ForecastSource, summary: List<SourceAccuracy>): ForecastSource? {
        fun score(accuracy: SourceAccuracy) = accuracy.dayAheadError ?: accuracy.shortTermError
        val base = summary.firstOrNull { it.source == default }?.takeIf { it.days >= MIN_DAYS }?.let(::score) ?: return null
        return summary
            .filter { it.source != default && it.days >= MIN_DAYS }
            .mapNotNull { accuracy -> score(accuracy)?.let { accuracy.source to it } }
            .filter { (_, error) -> error < base * (1 - MARGIN) }
            .minByOrNull { it.second }?.first
    }
}

/**
 * Öğrenen düzeltme: tahmin bir yerde hep aynı yöne şaşıyorsa (ör. geceleri 1,5° sıcak) sıcaklık o kadar
 * düzeltilir. Şartlar sağlanınca kendiliğinden devreye girer, bozulunca kendiliğinden kapanır.
 */
object LearnedCorrection {
    const val MIN_DAYS = 7
    const val MAX_STATION_KM = 15.0
    const val LIMIT = 3.0

    /** Bu kadar küçük bir sapma düzeltilmez. */
    private const val NEGLIGIBLE = 0.3

    data class Result(val day: Double, val night: Double)

    /** [pairs]: kullanılan kaynağın 1–6 saatlik tahmin–ölçüm eşleri. Düzeltme yoksa (0, 0). */
    fun compute(pairs: List<ForecastPair>, stationKm: Double?): Result {
        if (stationKm == null || stationKm > MAX_STATION_KM) return Result(0.0, 0.0)
        val short = pairs.filter { it.leadHours in AccuracyStats.SHORT_LEADS }
        return Result(day = part(short.filter { !it.isNight }), night = part(short.filter { it.isNight }))
    }

    private fun part(pairs: List<ForecastPair>): Double {
        if (pairs.map { it.day }.distinct().size < MIN_DAYS) return 0.0
        val errors = pairs.map { it.error }
        val mean = errors.sum() / errors.size
        val deviation = sqrt(errors.sumOf { (it - mean) * (it - mean) } / errors.size)
        // Tutarlı değil (sapmanın standart sapması ortalama sapmadan büyük) ya da önemsiz: düzeltme yok.
        if (abs(mean) < NEGLIGIBLE || deviation >= abs(mean)) return 0.0
        // Tahmin sıcak kalıyorsa (artı sapma) sıcaklık düşürülür.
        return (-mean).coerceIn(-LIMIT, LIMIT)
    }
}

/**
 * MGM saatlik tahmininin saat düzeltmesinden (1.5.2) sonra bir yerin ek verisi: eski kayıtlardan çıkan karar ve
 * düzeltme unutulur, MGM'nin tahmini ilk yenilemede doğru saatlerle yeniden istenir. Saf; test edilir.
 */
object MgmHourlyFix {
    fun forget(extras: PlaceExtras): PlaceExtras = extras.copy(
        chosen = null,
        dayCorrection = 0.0,
        nightCorrection = 0.0,
        accuracy = extras.accuracy.filter { it.source != ForecastSource.Mgm },
        decidedDay = -1,
        nationalFetchedAt = 0,
    )
}
