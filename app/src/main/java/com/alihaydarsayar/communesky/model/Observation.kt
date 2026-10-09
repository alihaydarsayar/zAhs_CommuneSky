package com.alihaydarsayar.communesky.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

enum class ObservationSource {
    /** Meteoroloji Genel Müdürlüğü otomatik istasyonları (sadece Türkiye). */
    Mgm,

    /** Havalimanı ölçümleri (METAR), NOAA Aviation Weather Center üzerinden; dünya geneli. */
    Metar,
}

/** Bir istasyonun son ölçümü. Değerler bilinmiyorsa null. */
@Serializable
data class Observation(
    val source: ObservationSource,
    /** "Samsun Bölge" (MGM) ya da "Samsun-Çarşamba" (havalimanı). */
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val observedAtMillis: Long,
    val temperature: Double? = null,
    val humidity: Int? = null,
    val windSpeedKmh: Double? = null,
    /** Ölçülen hava durumunun WMO karşılığı; ölçüm hava durumu söylemiyorsa null. */
    val weatherCode: Int? = null,
    /** Şu an yağış var mı (yağmur, sağanak, kar, çisenti). */
    val isWet: Boolean = false,
    /** Şu an gök gürültüsü var mı. */
    val hasThunder: Boolean = false,
) {
    val observedAt: Instant get() = Instant.ofEpochMilli(observedAtMillis)
}

/** Ölçümün ekranda nasıl kullanılacağı. */
sealed interface ObservationUse {
    val observation: Observation
    val distanceKm: Double

    /** İstasyon yakın: anlık sıcaklık ve durum ölçümden gelir. */
    data class Override(override val observation: Observation, override val distanceKm: Double) : ObservationUse

    /** İstasyon uzak ama orada yağış/gök gürültüsü var: durum değişmez, uyarı gösterilir. */
    data class Advisory(override val observation: Observation, override val distanceKm: Double) : ObservationUse
}

/**
 * Ölçümün ne zaman ve nasıl kullanılacağına dair kurallar. Saf mantık; test edilir.
 *
 * Mesafe eşikleri ve gerekçeleri:
 * - 10 km'ye kadar ("yakın"): ölçüm anlık sıcaklığı ve durumu belirler. Open-Meteo'nun modelleri
 *   Türkiye'de 2–11 km'lik ızgaralarla çalışır; 10 km içindeki bir istasyon en az bir model hücresi
 *   kadar temsil edicidir ve gerçek bir ölçümdür. MGM'nin ilçe istasyonları genelde ilçe
 *   merkezinden birkaç km uzaktadır (Atakum: 1,8 km).
 * - 10–40 km ("çevre"): sıcaklık deniz, rakım ve şehir etkisiyle birkaç derece değişebileceği için
 *   değiştirilmez; ama orada yağış ya da gök gürültüsü ölçülmüşse uyarı gösterilir. Sağanak
 *   hücreleri 10–30 km büyüklüğündedir ve saatte 20–40 km yol alır; 40 km'deki bir fırtına
 *   yakında buraya da gelebilir. (Atakum ↔ Çarşamba Havalimanı: 27 km.)
 * - 40 km'den uzak: kullanılmaz.
 * - 30 dakikadan eski ölçüm kullanılmaz (havalimanları 30–60 dakikada bir rapor verir).
 * - İstasyonla model noktası arasında 200 m'den fazla rakım farkı varsa sıcaklık değiştirilmez
 *   (her 100 m'de ~0,65°C fark eder); durum yine ölçümden gelir.
 */
object ObservationPolicy {
    const val NEAR_KM = 10.0
    const val ADVISORY_KM = 40.0
    val MAX_AGE: Duration = Duration.ofMinutes(30)
    const val MAX_ELEVATION_DIFF_M = 200.0

    /** Ölçüm saati gelecekte görünse bile (saat farkı) 5 dakikaya kadar kabul edilir. */
    private val ClockSkew: Duration = Duration.ofMinutes(5)

    fun isFresh(observation: Observation, now: Instant): Boolean {
        val age = Duration.between(observation.observedAt, now)
        return age <= MAX_AGE && age >= ClockSkew.negated()
    }

    fun distanceKm(observation: Observation, latitude: Double, longitude: Double): Double =
        GeoDistance.kilometers(observation.latitude, observation.longitude, latitude, longitude)

    /** Bu ölçüm bu yer için işe yarar mı? (Taze ve en fazla 40 km uzakta.) */
    fun isUsable(observation: Observation, latitude: Double, longitude: Double, now: Instant): Boolean =
        isFresh(observation, now) && distanceKm(observation, latitude, longitude) <= ADVISORY_KM

    fun evaluate(
        observation: Observation?,
        latitude: Double,
        longitude: Double,
        now: Instant,
        modelIsWet: Boolean,
    ): ObservationUse? {
        if (observation == null || !isFresh(observation, now)) return null
        val distance = distanceKm(observation, latitude, longitude)
        return when {
            distance <= NEAR_KM -> ObservationUse.Override(observation, distance)
            // Model zaten yağış diyorsa uyarı yeni bir şey söylemez.
            distance <= ADVISORY_KM && (observation.isWet || observation.hasThunder) && !modelIsWet ->
                ObservationUse.Advisory(observation, distance)
            else -> null
        }
    }

    /** Yakın istasyonun ölçümünü modelin anlık verisine işler. */
    fun applyTo(current: CurrentWeather, use: ObservationUse?, modelElevation: Double?): CurrentWeather {
        if (use !is ObservationUse.Override) return current
        val obs = use.observation
        val elevationOk = obs.elevationMeters == null || modelElevation == null ||
            abs(obs.elevationMeters - modelElevation) <= MAX_ELEVATION_DIFF_M
        val temperature = obs.temperature?.takeIf { elevationOk }
        val delta = temperature?.minus(current.temperature) ?: 0.0
        return current.copy(
            temperature = temperature ?: current.temperature,
            // Hissedilen sıcaklık, ölçülen sıcaklıkla aynı miktarda kaydırılır.
            apparentTemperature = current.apparentTemperature + delta,
            humidity = obs.humidity ?: current.humidity,
            windSpeed = obs.windSpeedKmh ?: current.windSpeed,
            weatherCode = obs.weatherCode ?: current.weatherCode,
        )
    }
}
