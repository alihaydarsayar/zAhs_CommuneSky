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
 * 5 (1.5): yer başına ek veriler (ulusal servisin tahmini, uyarılar, öteki modeller).
 * 6 (1.5): doğruluk kaydı (kaydedilen tahminler ve ölçümler).
 * 7 (1.5.10): istasyon listesine uygulama sürümü ve "eksiksiz" işareti (liste sürüm değişince yeniden bulunur).
 * Geçişleri Room şema dosyalarından üretir (app/schemas).
 */
@Database(
    entities = [
        WeatherCacheEntity::class,
        PlaceEntity::class,
        ObservationEntity::class,
        MgmStationEntity::class,
        com.alihaydarsayar.communesky.data.extras.PlaceExtrasEntity::class,
        com.alihaydarsayar.communesky.data.accuracy.ForecastRecordEntity::class,
        com.alihaydarsayar.communesky.data.accuracy.ObservedRecordEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun placeDao(): PlaceDao
    abstract fun observationDao(): ObservationDao
    abstract fun placeExtrasDao(): com.alihaydarsayar.communesky.data.extras.PlaceExtrasDao
    abstract fun accuracyDao(): com.alihaydarsayar.communesky.data.accuracy.AccuracyDao
}
