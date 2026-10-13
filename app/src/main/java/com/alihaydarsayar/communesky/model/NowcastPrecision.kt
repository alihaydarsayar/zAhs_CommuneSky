package com.alihaydarsayar.communesky.model

import java.time.Duration
import java.time.LocalDateTime

/**
 * Kısa vadeli yağış notu ne kadar kesin konuşabilir?
 *
 * Türkiye'de 15 dakikalık yağış verisi gerçek ölçüm (radar) değildir; saatlik tahminden türetilir. Böyle bir
 * veriyle "20 dk içinde başlıyor" ya da "20:15'te" demek, olmayan bir kesinlik vaat eder. Türkiye'deki yerlerde
 * not yuvarlak konuşur: "birazdan", "1 saat içinde". Çevredeki istasyonlar o sırada yağış ölçüyorsa yağış
 * gerçekten yakındadır ve kesin dil kalır. Diğer ülkelerde davranış değişmez. Saf mantık; test edilir.
 */
object NowcastPrecision {
    /** Bu kadar dakikadan yakın bir değişiklik "birazdan" diye söylenir. */
    const val SOON_MINUTES = 30

    fun isRough(snapshot: WeatherSnapshot): Boolean =
        ForecastRegion.of(snapshot.city.latitude, snapshot.city.longitude) == ForecastRegion.Turkey && !stationsMeasureRain(snapshot)

    /** Yerin kendi istasyonu ya da çevredeki bir istasyon şu an yağış ya da gök gürültüsü ölçüyor mu? */
    fun stationsMeasureRain(snapshot: WeatherSnapshot): Boolean = when (val use = snapshot.observation) {
        null -> false
        // Uzak istasyon yalnız yağış ya da gök gürültüsü ölçtüğünde not olur.
        is ObservationUse.Advisory -> true
        is ObservationUse.Override ->
            use.observation.isWet || use.observation.hasThunder || use.network?.let { it.isRaining || it.nearbyWet != null } == true
    }

    /** Yuvarlak dille söylenen süre. */
    fun span(minutes: Long): RoughSpan = when {
        minutes <= SOON_MINUTES -> RoughSpan.Soon
        minutes <= 60 -> RoughSpan.WithinAnHour
        else -> RoughSpan.WithinTwoHours
    }

    fun span(now: LocalDateTime, at: LocalDateTime): RoughSpan = span(Duration.between(now, at).toMinutes())

    /**
     * Yağışın başlangıcını saat başına yuvarlar: "20:15" yerine "20:00". Yarım saatten yakınsa "birazdan"
     * ([RainStart.isSoon]); o durumda saat olarak bir sonraki saat başı kalır.
     */
    fun roughen(start: RainStart, now: LocalDateTime): RainStart {
        if (start.isNow) return start
        val hour = start.time.withMinute(0).withSecond(0).withNano(0)
        val rounded = if (start.time.minute >= 30) hour.plusHours(1) else hour
        val soon = Duration.between(now, start.time).toMinutes() <= SOON_MINUTES
        val time = if (soon || !rounded.isAfter(now)) now.withMinute(0).withSecond(0).withNano(0).plusHours(1) else rounded
        return start.copy(time = time, isRough = true, isSoon = soon)
    }
}

/** "Birazdan", "1 saat içinde", "2 saat içinde". */
enum class RoughSpan { Soon, WithinAnHour, WithinTwoHours }
