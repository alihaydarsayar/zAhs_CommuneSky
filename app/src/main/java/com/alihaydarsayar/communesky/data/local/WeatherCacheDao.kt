package com.alihaydarsayar.communesky.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Room bu arayüzden SQL çalıştıran kodu üretir. */
@Dao
interface WeatherCacheDao {

    /** Satır her değiştiğinde yeni değeri yayar; ekran bunu dinleyerek kendini günceller. */
    @Query("SELECT * FROM weather_cache WHERE id = ${WeatherCacheEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<WeatherCacheEntity?>

    @Query("SELECT * FROM weather_cache WHERE id = ${WeatherCacheEntity.SINGLE_ROW_ID}")
    suspend fun get(): WeatherCacheEntity?

    @Upsert
    suspend fun upsert(entity: WeatherCacheEntity)
}
