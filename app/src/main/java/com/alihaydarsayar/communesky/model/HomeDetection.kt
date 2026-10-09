package com.alihaydarsayar.communesky.model

/** Kullanıcı evine göre nerede? */
enum class HomeProximity {
    /** Evde ya da evin hemen yanında (aynı mahalle). */
    AtHome,

    /** Evin yakınlık mesafesi içinde: ev ana bilgi olarak kalır. */
    Nearby,

    /** Yakınlık mesafesinin dışında: iki yer yan yana gösterilir. */
    Away,
}

/**
 * "Kullanıcı evde mi, yakında mı, uzakta mı?" kararı. Hesap tamamen cihazda yapılır; konum bunun
 * için hiçbir yere gönderilmez.
 *
 * Evde: yaklaşık konumun hata payı birkaç km olabildiği için sabit bir eşik yerine konumun kendi
 * hata payı (accuracy) da hesaba katılır, ama sınırlı:
 *
 *   evde = mesafe ≤ min(EV_YARIÇAPI + min(hata payı, 1 km), yakınlık mesafesi / 2)
 *
 * - EV_YARIÇAPI (1 km): ev ile mahalle içindeki yakın yerler (market, komşu) aynı sayılsın.
 * - Hata payı Android'in verdiği değerdir (%68 güven yarıçapı); bilinmiyorsa 2 km varsayılır.
 *   "Evde" sınırına katkısı en fazla 1 km: yaklaşık konumda sınır 3–6 km'ye çıkıp 3 km
 *   ötedeki Tuzla Merkez'i "evde" saymasın. Böylece "Evde" sınırı en fazla 2 km.
 * - "Evde" sınırı yakınlık mesafesinin yarısını geçmez: "Yakında" her ayarda mümkün kalır
 *   (2 km seçilirse evde sınırı 1 km).
 *
 * Yakında: evin "yakınlık mesafesi" içinde (kullanıcı 2–20 km arası seçer, varsayılan 5 km).
 * Uzakta: bunun dışında.
 *
 * Bu sınırlar içinde eşik cömert: evdeyken yanlışlıkla "evde değil" göstermek, yakındayken "evde"
 * göstermekten daha rahatsız edici (iki yer aynı havayı tekrar eder).
 */
object HomeDetection {
    const val HOME_RADIUS_KM = 1.0
    const val DEFAULT_ACCURACY_M = 2_000f
    const val MAX_ACCURACY_KM = 5.0

    /** Hata payının "Evde" sınırına en fazla katkısı (km). */
    const val MAX_HOME_ACCURACY_KM = 1.0

    const val DEFAULT_NEARBY_KM = 5
    const val MIN_NEARBY_KM = 2
    const val MAX_NEARBY_KM = 20

    fun classify(
        device: GeoPoint,
        accuracyMeters: Float?,
        home: GeoPoint,
        nearbyRadiusKm: Int = DEFAULT_NEARBY_KM,
    ): HomeProximity = classifyDistance(distanceKm(device, home), accuracyKm(accuracyMeters), nearbyRadiusKm)

    /** Mesafe ve hata payı (km) belliyken karar; Ayarlar ekranı kayıtlı yerler için hata payı 0 ile kullanır. */
    fun classifyDistance(distanceKm: Double, accuracyKm: Double, nearbyRadiusKm: Int): HomeProximity {
        val radius = nearbyRadiusKm.coerceIn(MIN_NEARBY_KM, MAX_NEARBY_KM)
        return when {
            distanceKm <= homeLimitKm(accuracyKm, radius) -> HomeProximity.AtHome
            distanceKm <= radius -> HomeProximity.Nearby
            else -> HomeProximity.Away
        }
    }

    /** "Evde" sınırı (km): 1 km + en fazla 1 km hata payı, ama yakınlık mesafesinin yarısından fazla değil. */
    fun homeLimitKm(accuracyKm: Double, nearbyRadiusKm: Int): Double {
        val radius = nearbyRadiusKm.coerceIn(MIN_NEARBY_KM, MAX_NEARBY_KM)
        return minOf(HOME_RADIUS_KM + accuracyKm.coerceIn(0.0, MAX_HOME_ACCURACY_KM), radius / 2.0)
    }

    fun isAtHome(device: GeoPoint, accuracyMeters: Float?, home: GeoPoint): Boolean =
        classify(device, accuracyMeters, home) == HomeProximity.AtHome

    fun accuracyKm(accuracyMeters: Float?): Double =
        ((accuracyMeters ?: DEFAULT_ACCURACY_M) / 1000.0).coerceIn(0.0, MAX_ACCURACY_KM)

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double =
        GeoDistance.kilometers(a.latitude, a.longitude, b.latitude, b.longitude)
}
