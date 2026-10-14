package com.alihaydarsayar.communesky.model

import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/**
 * İstasyon ağı: anlık havayı tek bir istasyon yerine çevredeki birkaç istasyondan çıkarır. Tek istasyon
 * arızalı, gölgede ya da farklı yükseklikte olabilir; ağ bunu komşularıyla karşılaştırarak yakalar.
 * Saf mantık; test edilir.
 *
 * - Kalite: 30 dakikadan eski ölçüm kullanılmaz; fiziksel olmayan değerler atılır; sıcaklığı komşularının
 *   ortancasından 4 °C'den fazla sapan istasyon o tur dışarıda kalır.
 * - Sıcaklık: her istasyonun sıcaklığı yerin yüksekliğine indirgenir (her 1000 m'de 6,5 °C) ve uzaklığın
 *   karesiyle ters ağırlıklı ortalanır. Model de 15 km uzaktaki bir istasyon gibi ağırlık alır: yakın istasyon
 *   varken etkisi küçük, istasyonlar uzakken büyüktür. Yüksekliği 300 m'den fazla farklı istasyon sıcaklığa katılmaz.
 * - Yağış: 5 km içindeki bir istasyon yağış ölçüyorsa ya da 15 km içindekilerin çoğu yağış ölçüyorsa "yağıyor".
 *   Kuru diyen tek bir istasyon çevredeki yağışı ezemez. Yağışı ölçmeyen istasyonlar (ör. deniz fenerleri) bu
 *   oylamaya hiç girmez: ne kuru ne ıslak sayılır; sıcaklığa katılmaya devam eder. 15 km içinde yağışı bilen
 *   istasyon yoksa ağ yağış hakkında bir şey söylemez ([Result.knowsPrecipitation] false).
 */
object StationNetwork {
    const val RADIUS_KM = 25.0

    /** İl ölçümleri tek istekte alınamazsa en çok bu kadar istasyon tek tek sorulur. */
    const val MAX_SINGLE_REQUESTS = 6
    const val OUTLIER_C = 4.0
    const val MAX_ELEVATION_DIFF_M = 300.0
    const val LAPSE_C_PER_M = 0.0065

    /** Model, bu kadar uzaktaki bir istasyon gibi ağırlık alır. */
    const val MODEL_DISTANCE_KM = 15.0

    /** Bu kadar yakın bir istasyon varsa ağırlığın en az [DOMINANT_SHARE] kadarı onundur. */
    const val DOMINANT_KM = 2.0
    const val DOMINANT_SHARE = 0.7

    const val RAIN_NEAR_KM = 5.0
    const val RAIN_AREA_KM = 15.0

    /** Denizdeki istasyonun (fener, mendirek, şamandıra) sıcaklık ağırlığı bu oranda azalır: deniz havası karayı temsil etmez. */
    const val SEA_WEIGHT = 0.5

    private val SEA_WORDS = listOf("fener", "mendirek", "şamandıra", "samandira")

    /**
     * İstasyon denizde mi? Adında "Fener", "Mendirek" ya da "Şamandıra" geçen MGM istasyonları. Sıcaklık ağında
     * kalırlar ama ağırlıkları yarıya iner; hakem ve öğrenen düzeltme onların ölçümünü kullanmaz.
     */
    fun isAtSea(observation: Observation): Boolean {
        val name = observation.stationName.lowercase(java.util.Locale.forLanguageTag("tr"))
        return observation.source == ObservationSource.Mgm && SEA_WORDS.any { it in name }
    }

    /**
     * Hakemin ve öğrenen düzeltmenin kullanacağı ham ölçüm: sıcaklığı olan en yakın kara istasyonu; karada yoksa
     * sıcaklığı olan en yakın istasyon. [stations] en yakından uzağa sıralı olmalı.
     */
    fun referenceStation(stations: List<Observation>): Observation? =
        stations.firstOrNull { it.temperature != null && !isAtSea(it) } ?: stations.firstOrNull { it.temperature != null }

    /** Yerin hemen dibindeki istasyon sonsuz ağırlık almasın. */
    private const val MIN_DISTANCE_KM = 0.5

    /** Ağın bir yer için söylediği. */
    data class Result(
        /** Yerin yüksekliğine indirgenmiş, ağırlıklı sıcaklık (model payıyla). */
        val temperature: Double,
        val isRaining: Boolean,
        /** 25 km içindeki herhangi bir istasyonda gök gürültüsü. */
        val hasThunder: Boolean,
        /** Kaliteden geçen istasyon sayısı. */
        val stationCount: Int,
        /** Sıcaklığa katılan istasyon sayısı (0 ise sıcaklık modelden). */
        val temperatureStations: Int,
        /** En yakın geçerli istasyon: nem, rüzgâr ve kaynak satırı ondan gelir. */
        val nearest: Observation,
        val nearestKm: Double,
        /** Ekranda gösterilecek hava kodu; ağ bir şey söylemiyorsa null (modelinki kalır). */
        val weatherCode: Int?,
        /** Yerde yağmıyor ama çevredeki bir istasyon yağış ya da gök gürültüsü ölçüyor: uyarı için. */
        val nearbyWet: Observation?,
        /** 15 km içinde yağışı bilen istasyon var mı? Yoksa [isRaining] "kuru" değil, "bilinmiyor" demektir. */
        val knowsPrecipitation: Boolean = true,
        /** Yağışı bilen en yakın istasyon (yağış kararının geldiği yer); yoksa null. */
        val precipitationStation: Observation? = null,
        val precipitationStationKm: Double? = null,
    )

    /** Fiziksel olmayan değerleri atar: nem 0–100 dışında, rüzgâr eksi. */
    fun clean(observation: Observation): Observation {
        val humidity = observation.humidity?.takeIf { it in 0..100 }
        val windOk = observation.windSpeedKmh == null || observation.windSpeedKmh >= 0
        return observation.copy(
            humidity = humidity,
            windSpeedKmh = observation.windSpeedKmh.takeIf { windOk },
            windDirection = observation.windDirection.takeIf { windOk && it != null && it in 0..360 },
        )
    }

    private fun km(observation: Observation, latitude: Double, longitude: Double) =
        GeoDistance.kilometers(observation.latitude, observation.longitude, latitude, longitude)

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }

    /**
     * Kaliteden geçen ölçümler, en yakından uzağa: taze, 25 km içinde (MGM istasyonları ve havalimanları), değerleri temizlenmiş ve sıcaklığı
     * komşularından kopuk olmayanlar. Kopukluk, hepsi ortak bir yüksekliğe indirgenerek ölçülür (dağdaki
     * istasyon "arızalı" sayılmasın); karşılaştırma için en az iki komşu gerekir.
     */
    fun valid(readings: List<Observation>, latitude: Double, longitude: Double, now: Instant): List<Observation> {
        val fresh = readings
            .filter { ObservationPolicy.isFresh(it, now) && km(it, latitude, longitude) <= RADIUS_KM }
            .map(::clean)
            .sortedBy { km(it, latitude, longitude) }
        val elevations = fresh.mapNotNull { it.elevationMeters }
        val reference = if (elevations.isEmpty()) 0.0 else elevations.average()
        fun level(observation: Observation): Double? = observation.temperature?.let { temperature ->
            temperature + LAPSE_C_PER_M * ((observation.elevationMeters ?: reference) - reference)
        }
        // Her turda komşularından en çok sapan tek istasyon çıkarılır; böylece arızalı bir istasyon,
        // ortancayı kaydırıp sağlam komşusunu da dışarıda bırakamaz.
        val kept = fresh.toMutableList()
        while (true) {
            val worst = kept.mapNotNull { station ->
                val own = level(station) ?: return@mapNotNull null
                val others = kept.filter { it !== station }.mapNotNull(::level)
                if (others.size < 2) null else station to abs(own - median(others))
            }.maxByOrNull { it.second }
            if (worst == null || worst.second <= OUTLIER_C) break
            kept.remove(worst.first)
        }
        return kept
    }

    /**
     * Ağın sonucu. [readings]: bir yerin saklanan ağ ölçümleri; burada [now] anına göre yeniden elenir (ölçümler
     * ekrandayken eskir). İki geçerli istasyon kalmadıysa null döner: tek istasyon eski kurallarla kullanılır.
     */
    fun blend(
        readings: List<Observation>,
        latitude: Double,
        longitude: Double,
        placeElevation: Double?,
        modelTemperature: Double,
        now: Instant,
    ): Result? {
        val stations = valid(readings, latitude, longitude, now)
        if (stations.size < 2) return null
        val nearest = stations.firstOrNull { it.temperature != null } ?: stations.first()

        // --- Sıcaklık
        data class Weighted(val temperature: Double, val weight: Double, val km: Double, val sea: Boolean)
        val parts = stations.mapNotNull { station ->
            val temperature = station.temperature ?: return@mapNotNull null
            val lift = if (station.elevationMeters != null && placeElevation != null) station.elevationMeters - placeElevation else 0.0
            if (abs(lift) > MAX_ELEVATION_DIFF_M) return@mapNotNull null
            val distance = max(km(station, latitude, longitude), MIN_DISTANCE_KM)
            // İstasyon yerden yüksekteyse orası daha soğuktur: yerin yüksekliğine inerken sıcaklık artar.
            val sea = isAtSea(station)
            Weighted(temperature + LAPSE_C_PER_M * lift, (if (sea) SEA_WEIGHT else 1.0) / (distance * distance), distance, sea)
        }.toMutableList()
        val modelWeight = 1 / (MODEL_DISTANCE_KM * MODEL_DISTANCE_KM)
        // Baskın ağırlık yalnız karadaki yakın istasyona verilir.
        val closest = parts.filter { !it.sea }.minByOrNull { it.km }
        if (closest != null && closest.km <= DOMINANT_KM) {
            val rest = parts.sumOf { it.weight } - closest.weight + modelWeight
            val needed = rest * DOMINANT_SHARE / (1 - DOMINANT_SHARE)
            if (closest.weight < needed) parts[parts.indexOf(closest)] = closest.copy(weight = needed)
        }
        val total = parts.sumOf { it.weight } + modelWeight
        val temperature = (parts.sumOf { it.temperature * it.weight } + modelTemperature * modelWeight) / total

        // --- Yağış: yalnız yağışı bilen istasyonlarla. Bilmeyenler paydaya da girmez.
        val informed = stations.filter { it.knowsPrecipitation }
        val area = informed.filter { km(it, latitude, longitude) <= RAIN_AREA_KM }
        fun weight(station: Observation) = max(km(station, latitude, longitude), MIN_DISTANCE_KM).let { 1 / (it * it) }
        val wet = area.filter { it.isWet }
        val nearWet = wet.any { km(it, latitude, longitude) <= RAIN_NEAR_KM }
        val heavierWet = area.isNotEmpty() && wet.sumOf(::weight) > area.sumOf(::weight) / 2
        // Çoğunluk yağış ölçüyorsa, en yakındaki tek kuru istasyon bunu değiştiremez.
        val majorityWet = wet.size >= 2 && wet.size > area.size - wet.size
        val raining = nearWet || heavierWet || majorityWet
        val thunder = informed.any { it.hasThunder }

        val code = when {
            raining && thunder -> informed.firstOrNull { it.hasThunder }?.weatherCode ?: THUNDER_CODE
            raining -> informed.firstOrNull { it.isWet }?.weatherCode
            // Kuru: yalnız 15 km içinde yağışı bilen ve kuru ölçen bir istasyon bunu söyleyebilir.
            else -> area.firstOrNull { !it.isWet && !it.hasThunder }?.weatherCode
        }
        val nearbyWet = if (raining) null else informed.firstOrNull { it.hasThunder } ?: informed.firstOrNull { it.isWet }
        val precipitationStation = area.firstOrNull()
        return Result(
            temperature = temperature,
            isRaining = raining,
            hasThunder = thunder,
            stationCount = stations.size,
            temperatureStations = parts.size,
            nearest = nearest,
            nearestKm = km(nearest, latitude, longitude),
            weatherCode = code,
            nearbyWet = nearbyWet,
            knowsPrecipitation = area.isNotEmpty(),
            precipitationStation = precipitationStation,
            precipitationStationKm = precipitationStation?.let { km(it, latitude, longitude) },
        )
    }

    private const val THUNDER_CODE = 95
}
