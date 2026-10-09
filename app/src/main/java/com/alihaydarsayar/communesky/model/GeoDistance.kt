package com.alihaydarsayar.communesky.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoDistance {
    private const val EARTH_RADIUS_KM = 6371.0

    /** İki koordinat arasındaki kuş uçuşu mesafe (km), haversine formülüyle. */
    fun kilometers(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

/** Enlem/boylam çifti. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Dışarıya (Open-Meteo, MGM, NOAA) giden koordinatlar yaklaşık 1 km'ye yuvarlanır: 0,01° enlem
 * ≈ 1,1 km, Türkiye'de 0,01° boylam ≈ 0,85 km. Hava durumu için bu fark önemsizdir; hassas konum
 * sadece cihazda (yer adı ve en yakın istasyonu seçmek için) kullanılır.
 */
object CoordinatePrivacy {
    fun round(value: Double): Double = Math.round(value * 100) / 100.0
}
