package com.alihaydarsayar.communesky.model

/**
 * "Kullanıcı şu an evde mi?" kararı. Yaklaşık konumun hata payı birkaç km olabildiği için
 * sabit bir eşik yerine konumun kendi hata payı (accuracy) da hesaba katılır:
 *
 *   evde = mesafe ≤ EV_YARIÇAPI + hata payı
 *
 * - EV_YARIÇAPI (1 km): ev ile mahalle içindeki yakın yerler (market, komşu) aynı sayılsın.
 * - Hata payı Android'in verdiği değerdir (%68 güven yarıçapı). Yaklaşık konumda genelde
 *   1–3 km'dir; bilinmiyorsa 2 km varsayılır.
 * - Hata payı en fazla 5 km sayılır: konum çok belirsizse (ör. sadece baz istasyonu) şehrin
 *   öbür ucundaki birini de "evde" saymayalım.
 *
 * Eşik bilerek cömert: evdeyken yanlışlıkla "evde değil" göstermek, yakındayken "evde"
 * göstermekten daha rahatsız edici (iki satır aynı havayı tekrar eder).
 */
object HomeDetection {
    const val HOME_RADIUS_KM = 1.0
    const val DEFAULT_ACCURACY_M = 2_000f
    const val MAX_ACCURACY_KM = 5.0

    fun isAtHome(device: GeoPoint, accuracyMeters: Float?, home: GeoPoint): Boolean {
        val accuracyKm = ((accuracyMeters ?: DEFAULT_ACCURACY_M) / 1000.0).coerceIn(0.0, MAX_ACCURACY_KM)
        val distance = GeoDistance.kilometers(device.latitude, device.longitude, home.latitude, home.longitude)
        return distance <= HOME_RADIUS_KM + accuracyKm
    }
}
