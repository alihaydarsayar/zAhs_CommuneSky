package com.alihaydarsayar.communesky.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Sürüm geçmişi:
 * 1 (1.0–1.1): tek satırlık hava durumu önbelleği.
 * 2 (1.2): kayıtlı yerler tablosu + önbellekte konum hata payı sütunu. Eski satır (id 0)
 *    olduğu gibi "Bulunduğum yer" önbelleği olarak kalır.
 * 3 (1.2): istasyon ölçümleri ve yer başına bulunan MGM istasyonu.
 * 4 (1.2): yere en yakın MGM istasyonları listesi ve "Bulunduğum yer" için ilçe adı.
 * Geçişleri Room şema dosyalarından üretir (app/schemas).
 */
@Database(
    entities = [
        WeatherCacheEntity::class,
        PlaceEntity::class,
        ObservationEntity::class,
        MgmStationEntity::class,
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun placeDao(): PlaceDao
    abstract fun observationDao(): ObservationDao
}
