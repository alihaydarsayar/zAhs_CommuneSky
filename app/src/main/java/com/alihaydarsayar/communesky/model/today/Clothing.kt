package com.alihaydarsayar.communesky.model.today

import com.alihaydarsayar.communesky.model.Forecast
import com.alihaydarsayar.communesky.model.HourlyForecast
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * "Ne giysem": bugünün hava verisinden, telefonda hesaplanan giyim önerisi. Ek internet, izin ya da uyanma
 * yoktur. Bu dosyadaki her şey saf hesaptır; test edilir.
 */

/** Gün dilimleri: sabah 07–11, öğle 11–17, akşam 17–22. */
enum class DayPart(val from: Int, val until: Int) {
    Morning(7, 11),
    Midday(11, 17),
    Evening(17, 22),
}

/** Bir dilimin havası: hissedilen sıcaklığın ortalaması, en kuvvetli hamle, yağış ve UV. */
data class PartWeather(
    val part: DayPart,
    val feelsLike: Double,
    val gust: Double?,
    val rainChance: Int,
    val rainMm: Double,
    val uv: Double?,
)

/** Hissedilen sıcaklığa göre katman; soğuktan sıcağa değil, inceden kalına sıralı. */
enum class Layer {
    /** 26° üstü: ince, açık renk; tişört, şort ya da etek. */
    Light,

    /** 21–26°: tişört, ince pantolon. */
    TShirt,

    /** 16–21°: uzun kollu ya da ince hırka. */
    LongSleeve,

    /** 11–16°: kazak ya da ince mont. */
    Sweater,

    /** 5–11°: mont. */
    Coat,

    /** 0–5°: kalın mont, atkı. */
    HeavyCoat,

    /** 0° altı: kalın mont, atkı, bere, eldiven. */
    FullWinter,
}

enum class Extra { Umbrella, Windbreaker, Sunglasses, Sunscreen, Hat, WaterproofShoes }

/** Ayarlar › "Çabuk üşürüm / Normal / Sıcaklarım": eşikler buna göre 3° kayar. */
enum class Sensitivity(val shift: Double) {
    /** Çabuk üşür: aynı havada bir kat daha kalın. */
    Cold(3.0),
    Normal(0.0),

    /** Çabuk sıcaklar: aynı havada bir kat daha ince. */
    Warm(-3.0),
}

/** Sabah ile öğle arasında hissedilen sıcaklık çok değişiyorsa: sabahki ve öğlenki değer, sabah için katman. */
data class Swing(val morning: Double, val midday: Double, val morningLayer: Layer, val middayLayer: Layer)

data class PartAdvice(val weather: PartWeather, val layer: Layer)

data class ClothingAdvice(
    /** Günün önerisi: henüz bitmemiş dilimlerin en kalın katmanı (üşümemek için). */
    val layer: Layer,
    val extras: Set<Extra>,
    /** Günün bütün dilimleri (geçmiş olanlar da); ayrıntıda gösterilir. */
    val parts: List<PartAdvice>,
    val swing: Swing?,
    /** Öneri yarın için mi (akşam geç saatte bugünün dilimleri bitmiştir). */
    val forTomorrow: Boolean = false,
)

object Clothing {
    /** Sabah ile öğle arasındaki fark bundan büyükse ayrıca söylenir. */
    const val SWING = 8.0

    /** "Üşüdüm" / "Bunaldım" düğmelerinin her basışta kaydırdığı miktar ve toplam sınırı. */
    const val FEEDBACK_STEP = 0.5
    const val FEEDBACK_LIMIT = 4.0

    const val UMBRELLA_CHANCE = 40
    const val UMBRELLA_MM = 1.0
    const val WINDBREAKER_GUST = 40.0
    const val SUN_UV = 6.0
    const val HAT_UV = 8.0
    const val HAT_FEELS = 30.0
    const val WATERPROOF_MM = 5.0

    /** Bu saatten sonra öneri yarın için verilir. */
    const val TOMORROW_FROM = 21

    /**
     * [feelsLike] için katman. [personalShift]: kişisel ayar (°C); artıysa kişi üşüyor demektir ve hava ona
     * o kadar daha soğukmuş gibi hesaplanır.
     */
    fun layer(feelsLike: Double, personalShift: Double = 0.0): Layer {
        val t = feelsLike - personalShift
        return when {
            t > 26 -> Layer.Light
            t >= 21 -> Layer.TShirt
            t >= 16 -> Layer.LongSleeve
            t >= 11 -> Layer.Sweater
            t >= 5 -> Layer.Coat
            t >= 0 -> Layer.HeavyCoat
            else -> Layer.FullWinter
        }
    }

    /** Kişisel kaydırma: ayardaki seçim ve "Üşüdüm/Bunaldım" düğmelerinin toplamı (düğmeler en çok ±4°). */
    fun personalShift(sensitivity: Sensitivity, feedback: Double): Double =
        sensitivity.shift + feedback.coerceIn(-FEEDBACK_LIMIT, FEEDBACK_LIMIT)

    /** "Üşüdüm" (+) ya da "Bunaldım" (−) düğmesine basılınca yeni kaydırma. */
    fun afterFeedback(feedback: Double, feltCold: Boolean): Double =
        (feedback + if (feltCold) FEEDBACK_STEP else -FEEDBACK_STEP).coerceIn(-FEEDBACK_LIMIT, FEEDBACK_LIMIT)

    /** [date] gününün dilimleri; saatlik verisi olmayan dilim atlanır. */
    fun parts(forecast: Forecast, date: LocalDate): List<PartWeather> = DayPart.entries.mapNotNull { part ->
        val hours = forecast.hourly.filter { it.time.toLocalDate() == date && it.time.hour >= part.from && it.time.hour < part.until }
        if (hours.isEmpty()) null else weather(part, hours)
    }

    private fun weather(part: DayPart, hours: List<HourlyForecast>) = PartWeather(
        part = part,
        feelsLike = hours.map { it.apparentTemperature ?: it.temperature }.average(),
        gust = hours.mapNotNull { it.windGust ?: it.windSpeed }.maxOrNull(),
        rainChance = hours.maxOf { it.precipitationProbability },
        rainMm = hours.sumOf { it.precipitation ?: 0.0 },
        uv = hours.mapNotNull { it.uvIndex }.maxOrNull(),
    )

    /** Öneri hangi gün için: akşam dilimi bitmek üzereyken (21:00'den sonra) yarın. */
    fun adviceDate(now: LocalDateTime): LocalDate = if (now.hour >= TOMORROW_FROM) now.toLocalDate().plusDays(1) else now.toLocalDate()

    /** Şu anki öneri: bugünün kalan dilimleri; akşam geç saatte yarının bütün dilimleri. */
    fun advise(forecast: Forecast, now: LocalDateTime, personalShift: Double = 0.0): ClothingAdvice? {
        val date = adviceDate(now)
        val tomorrow = date != now.toLocalDate()
        return advise(parts(forecast, date), personalShift, fromHour = if (tomorrow) 0 else now.hour)?.copy(forTomorrow = tomorrow)
    }

    /**
     * [allParts] gününün önerisi. [fromHour]: şu anki saat; bitmiş dilimler (ör. öğleden sonra sabah dilimi)
     * katmanı ve ekleri etkilemez ama ayrıntıda görünür.
     */
    fun advise(allParts: List<PartWeather>, personalShift: Double = 0.0, fromHour: Int = 0): ClothingAdvice? {
        if (allParts.isEmpty()) return null
        val all = allParts.map { PartAdvice(it, layer(it.feelsLike, personalShift)) }
        val advised = all.filter { it.weather.part.until > fromHour }.ifEmpty { all.takeLast(1) }
        val parts = advised.map { it.weather }
        val rainMm = parts.sumOf { it.rainMm }
        val chance = parts.maxOf { it.rainChance }
        val gust = parts.mapNotNull { it.gust }.maxOrNull() ?: 0.0
        val uv = parts.mapNotNull { it.uv }.maxOrNull() ?: 0.0
        val warmest = parts.maxOf { it.feelsLike }
        val extras = buildSet {
            if (chance > UMBRELLA_CHANCE || rainMm > UMBRELLA_MM) add(Extra.Umbrella)
            if (gust > WINDBREAKER_GUST) add(Extra.Windbreaker)
            if (uv >= SUN_UV) {
                add(Extra.Sunglasses)
                add(Extra.Sunscreen)
            }
            if (uv >= HAT_UV || warmest - personalShift >= HAT_FEELS) add(Extra.Hat)
            if (rainMm > WATERPROOF_MM) add(Extra.WaterproofShoes)
        }
        val morning = advised.firstOrNull { it.weather.part == DayPart.Morning }
        val midday = advised.firstOrNull { it.weather.part == DayPart.Midday }
        val swing = if (morning != null && midday != null && abs(midday.weather.feelsLike - morning.weather.feelsLike) >= SWING) {
            Swing(morning.weather.feelsLike, midday.weather.feelsLike, morning.layer, midday.layer)
        } else {
            null
        }
        return ClothingAdvice(layer = advised.maxOf { it.layer }, extras = extras, parts = all, swing = swing)
    }
}
