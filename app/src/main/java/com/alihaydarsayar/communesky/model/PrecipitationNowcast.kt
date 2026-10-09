package com.alihaydarsayar.communesky.model

import java.time.Duration
import java.time.LocalDateTime

/**
 * Önümüzdeki iki saatin yağışı, 15 dakikalık dilimlerle (Open-Meteo minutely_15). Yağış
 * widget'ının cümlesi ("Yağmur 20:00'de başlıyor") ve çubukları buradan gelir.
 */
data class PrecipitationNowcast(
    val kind: Kind,
    /** Başlama ya da dinme zamanı ([Kind.Starting], [Kind.Stopping]). */
    val at: LocalDateTime?,
    val isSnow: Boolean,
    /** Şimdiden başlayan en fazla 8 dilim (2 saat). */
    val slices: List<PrecipitationSlice>,
) {
    enum class Kind {
        /** İki saat boyunca yağış yok. */
        Dry,

        /** Şu an kuru; [at] zamanında başlıyor. */
        Starting,

        /** Şu an yağıyor; [at] zamanında diniyor. */
        Stopping,

        /** Şu an yağıyor ve iki saat boyunca sürüyor. */
        Continuing,

        /** 15 dakikalık veri yok (eski önbellek). */
        Unknown,
    }

    /** [at] şimdiye 5 dakikadan yakınsa "başlamak üzere" denir; saat vermek anlamsız. */
    fun isImminent(now: LocalDateTime): Boolean =
        at != null && Duration.between(now, at).toMinutes() < 5

    companion object {
        val Horizon: Duration = Duration.ofHours(2)
        const val SLICE_COUNT = 8

        /**
         * @param observed anlık durum istasyon ölçümünden geldiyse true: o zaman "şu an yağıyor mu"
         *   sorusunun cevabı ölçümdür, modelin bu dilimi değil.
         */
        fun of(forecast: Forecast, now: LocalDateTime, observed: Boolean = false): PrecipitationNowcast {
            val isSnow = forecast.current.condition == WeatherCondition.Snow || forecast.current.temperature <= 1.0
            val horizon = now.plus(Horizon)
            val slices = forecast.minutely
                .filter { it.end.isAfter(now) && it.start.isBefore(horizon) }
                .take(SLICE_COUNT)
            if (slices.isEmpty()) return PrecipitationNowcast(Kind.Unknown, null, isSnow, emptyList())

            val currentSlice = slices.first().takeIf { !it.start.isAfter(now) }
            val wetNow = if (observed) {
                forecast.current.condition.isWet || forecast.current.condition == WeatherCondition.Snow
            } else {
                currentSlice?.isWet == true
            }
            return if (wetNow) {
                val dry = slices.firstOrNull { !it.isWet && it.start.isAfter(now) }
                if (dry != null) {
                    PrecipitationNowcast(Kind.Stopping, dry.start, isSnow, slices)
                } else {
                    PrecipitationNowcast(Kind.Continuing, null, isSnow, slices)
                }
            } else {
                val wet = slices.firstOrNull { it.isWet }
                if (wet != null) {
                    PrecipitationNowcast(Kind.Starting, maxOf(wet.start, now), isSnow, slices)
                } else {
                    PrecipitationNowcast(Kind.Dry, null, isSnow, slices)
                }
            }
        }
    }
}

/** Yağışın bir sonraki başlangıcı: "Evde yağmur 21:00" gibi kısa uyarılar için. */
data class RainStart(val time: LocalDateTime, val isSnow: Boolean, val isNow: Boolean)

object RainTiming {
    /**
     * Şimdiden itibaren [hours] saat içinde yağışın başladığı ilk an. Önce 15 dakikalık dilimlere,
     * onların bittiği yerden sonra saatlik tahmine bakılır. Şu an yağıyorsa [RainStart.isNow].
     */
    fun next(forecast: Forecast, now: LocalDateTime, hours: Long = 12): RainStart? {
        val until = now.plusHours(hours)
        val current = forecast.current.condition
        val snow = current == WeatherCondition.Snow || forecast.current.temperature <= 1.0
        if (current.isWet || current == WeatherCondition.Snow) return RainStart(now, snow, isNow = true)

        val minutely = forecast.minutely.filter { it.end.isAfter(now) }
        minutely.firstOrNull { it.isWet && it.start.isBefore(until) }?.let {
            return RainStart(maxOf(it.start, now), snow, isNow = !it.start.isAfter(now))
        }
        val covered = minutely.lastOrNull()?.end ?: now
        val currentHour = now.withMinute(0).withSecond(0).withNano(0)
        return forecast.hourly
            .asSequence()
            .filter { !it.time.isBefore(currentHour) && it.time.isBefore(until) }
            // 15 dakikalık veri (daha kesin) kuru diyorsa o saatleri atla.
            .filter { it.time.plusHours(1).isAfter(covered) }
            .firstOrNull { it.condition.isWet || it.condition == WeatherCondition.Snow }
            ?.let { hour ->
                val time = maxOf(hour.time, covered, now)
                RainStart(time, hour.condition == WeatherCondition.Snow, isNow = !time.isAfter(now))
            }
    }
}
