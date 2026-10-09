package com.alihaydarsayar.communesky.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong

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
    /** Rüzgârın estiği yön (derece). Bilinmiyorsa rüzgâr hızı da kullanılmaz. */
    val windDirection: Int? = null,
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
 *   kadar temsil edicidir ve gerçek bir ölçümdür.
 * - 10–40 km ("çevre"): sıcaklık deniz, rakım ve şehir etkisiyle birkaç derece değişebileceği için
 *   değiştirilmez; ama orada yağış ya da gök gürültüsü ölçülmüşse uyarı gösterilir. Sağanak
 *   hücreleri 10–30 km büyüklüğündedir ve saatte 20–40 km yol alır. (Atakum ↔ Çarşamba: 27 km.)
 * - 40 km'den uzak: kullanılmaz.
 * - 30 dakikadan eski ölçüm kullanılmaz; uygulama açıkken de 30 dakikayı geçen ölçüm ekrandan kalkar.
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

    /** Ölçümün ekrandan kalkacağı an. */
    fun expiresAt(observation: Observation): Instant = observation.observedAt.plus(MAX_AGE)

    fun distanceKm(observation: Observation, latitude: Double, longitude: Double): Double =
        GeoDistance.kilometers(observation.latitude, observation.longitude, latitude, longitude)

    /** Bu ölçüm bu yer için işe yarar mı? (Taze ve en fazla 40 km uzakta.) */
    fun isUsable(observation: Observation, latitude: Double, longitude: Double, now: Instant): Boolean =
        isFresh(observation, now) && distanceKm(observation, latitude, longitude) <= ADVISORY_KM

    /**
     * Kullanılabilir ölçümler (MGM istasyonları ve havalimanları) arasından en iyisi: önce en yakın,
     * mesafe aynıysa (1 km'ye yuvarlanmış) en taze olan. Eski ya da 40 km'den uzak olanlar elenir.
     */
    fun chooseBest(candidates: List<Observation>, latitude: Double, longitude: Double, now: Instant): Observation? =
        candidates
            .filter { isUsable(it, latitude, longitude, now) }
            .minWithOrNull(
                compareBy<Observation>({ distanceKm(it, latitude, longitude).roundToLong() }, { -it.observedAtMillis }),
            )

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
        val temperature = obs.temperature?.takeIf { elevationOk } ?: current.temperature
        val humidity = obs.humidity ?: current.humidity
        // Sıcaklık ya da nem ölçümden geldiyse çiy noktası ikisinden yeniden hesaplanır.
        val dewPoint = if (temperature != current.temperature || humidity != current.humidity) {
            dewPoint(temperature, humidity)
        } else {
            current.dewPoint
        }
        // Rüzgâr hız ve yön birlikte değişir; ölçümde yön yoksa modelinki kalır.
        val wind = if (obs.windSpeedKmh != null && obs.windDirection != null) {
            obs.windSpeedKmh to obs.windDirection
        } else {
            current.windSpeed to current.windDirection
        }
        return current.copy(
            temperature = temperature,
            // Hissedilen sıcaklık, ölçülen sıcaklıkla aynı miktarda kaydırılır.
            apparentTemperature = current.apparentTemperature + (temperature - current.temperature),
            humidity = humidity,
            dewPoint = dewPoint,
            windSpeed = wind.first,
            windDirection = wind.second,
            weatherCode = obs.weatherCode ?: current.weatherCode,
        )
    }

    /** Sıcaklık (°C) ve bağıl nemden (%) çiy noktası (Magnus formülü). */
    fun dewPoint(temperature: Double, humidity: Int): Double {
        val rh = humidity.coerceIn(1, 100) / 100.0
        val gamma = ln(rh) + 17.625 * temperature / (243.04 + temperature)
        return 243.04 * gamma / (17.625 - gamma)
    }

    /** Çiy noktası ve sıcaklıktan bağıl nem (%). */
    fun relativeHumidity(temperature: Double, dewPoint: Double): Int {
        fun saturation(t: Double) = exp(17.625 * t / (243.04 + t))
        return (100 * saturation(dewPoint) / saturation(temperature)).roundToLong().toInt().coerceIn(0, 100)
    }
}

/** İstasyon ölçümünü bir yerin hava verisine işler. Saf mantık; test edilir. */
object ObservationBlend {
    /**
     * Yakın ve taze ölçüm anlık sıcaklığı ve durumu belirler; uzaktaki yağış/gök gürültüsü uyarı
     * olarak eklenir. [now] anında artık taze olmayan ölçüm yok sayılır, veri olduğu gibi kalır.
     */
    fun apply(snapshot: WeatherSnapshot, observation: Observation?, now: Instant): WeatherSnapshot {
        val use = ObservationPolicy.evaluate(
            observation = observation,
            latitude = snapshot.city.latitude,
            longitude = snapshot.city.longitude,
            now = now,
            modelIsWet = snapshot.forecast.current.condition.isWet,
        ) ?: return snapshot
        val current = ObservationPolicy.applyTo(snapshot.forecast.current, use, snapshot.forecast.elevation)
        return snapshot.copy(forecast = snapshot.forecast.copy(current = current), observation = use)
    }
}
