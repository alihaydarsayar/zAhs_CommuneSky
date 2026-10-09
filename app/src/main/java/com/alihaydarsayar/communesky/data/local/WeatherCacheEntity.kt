package com.alihaydarsayar.communesky.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bir yerin son hava durumu. Her yerin kendi satırı var; [id], yerin kimliğidir:
 * [DEVICE_PLACE_ID] "Bulunduğum yer" (izin yoksa yedek şehir), diğerleri `places` tablosundaki yerler.
 * Tahminin kendisi API'den geldiği haliyle JSON olarak saklanır; böylece yeni alan eklemek
 * veritabanı şemasını değiştirmeyi gerektirmez.
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val id: Long,
    val cityName: String?,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean,
    val forecastJson: String,
    val fetchedAtMillis: Long,
    /** Cihaz konumunun hata payı (metre); sadece "Bulunduğum yer" için, bilinmiyorsa null. */
    val accuracyMeters: Float? = null,
    /** Mahalle adı gösterildiğinde bağlı olduğu ilçe ("Yayla" → "Tuzla"). */
    val regionName: String? = null,
) {
    companion object {
        /** 1.1'deki tek satırın kimliği de buydu; geçişte o satır "Bulunduğum yer" olarak kalır. */
        const val DEVICE_PLACE_ID = 0L
    }
}
