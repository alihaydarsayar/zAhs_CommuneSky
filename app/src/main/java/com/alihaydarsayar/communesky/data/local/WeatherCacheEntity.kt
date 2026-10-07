package com.alihaydarsayar.communesky.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Son gösterilen yerin hava durumu. Tabloda tek satır var (id = 0); her yenilemede üzerine yazılır.
 * Tahminin kendisi API'den geldiği haliyle JSON olarak saklanır; böylece yeni alan eklemek
 * veritabanı şemasını değiştirmeyi gerektirmez.
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val cityName: String?,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean,
    val forecastJson: String,
    val fetchedAtMillis: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
