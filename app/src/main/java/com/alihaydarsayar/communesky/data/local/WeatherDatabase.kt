package com.alihaydarsayar.communesky.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Sürüm geçmişi:
 * 1 (1.0–1.1): tek satırlık hava durumu önbelleği.
 * 2 (1.2): kayıtlı yerler tablosu + önbellekte konum hata payı sütunu. Eski satır (id 0)
 *    olduğu gibi "Bulunduğum yer" önbelleği olarak kalır. Geçişi Room şema dosyalarından üretir.
 */
@Database(
    entities = [WeatherCacheEntity::class, PlaceEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun placeDao(): PlaceDao
}
